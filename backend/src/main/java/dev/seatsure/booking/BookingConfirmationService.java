package dev.seatsure.booking;

import dev.seatsure.common.ConflictException;
import dev.seatsure.common.GoneException;
import dev.seatsure.common.NotFoundException;
import dev.seatsure.event.Event;
import dev.seatsure.event.EventSeat;
import dev.seatsure.event.EventSeatRepository;
import dev.seatsure.event.EventSeatStatus;
import dev.seatsure.event.EventStatus;
import dev.seatsure.queue.QueueStatsService;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns a held booking into a confirmed one. Runs inside the caller's transaction (the payment
 * transaction) so that money, seats and tickets commit or roll back together.
 *
 * <p>Two independent guarantees stop a double booking here:
 *
 * <ol>
 *   <li>every {@code event_seat} row is updated under JPA {@code @Version} optimistic locking, so
 *       the loser of a race gets an {@code OptimisticLockException} and a 409;
 *   <li>the confirmed {@code booking_seat} rows sit behind a partial unique index on the seat.
 * </ol>
 */
@Service
public class BookingConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmationService.class);

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final EventSeatRepository eventSeatRepository;
    private final HoldRegistry holdRegistry;
    private final QueueStatsService queueStatsService;
    private final Clock clock;

    public BookingConfirmationService(
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            EventSeatRepository eventSeatRepository,
            HoldRegistry holdRegistry,
            QueueStatsService queueStatsService,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.eventSeatRepository = eventSeatRepository;
        this.holdRegistry = holdRegistry;
        this.queueStatsService = queueStatsService;
        this.clock = clock;
    }

    public Booking requireOwnPendingBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findByIdWithEvent(bookingId)
                .orElseThrow(() -> new NotFoundException("No booking with id " + bookingId));
        if (!booking.getUser().getId().equals(userId)) {
            throw new NotFoundException("No booking with id " + bookingId);
        }
        return booking;
    }

    public List<Long> seatIdsOf(Long bookingId) {
        return bookingSeatRepository.findByBookingId(bookingId).stream()
                .map(bookingSeat -> bookingSeat.getEventSeat().getSeat().getId())
                .toList();
    }

    /**
     * @return the seats that are now BOOKED for this booking
     * @throws GoneException when the Redis holds expired while the user was paying
     * @throws ConflictException when another confirmation got there first
     */
    public List<EventSeat> confirm(Booking booking) {
        Event event = booking.getEvent();
        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(booking.getId());
        if (bookingSeats.isEmpty()) {
            throw new ConflictException("That booking has no seats");
        }

        List<Long> seatIds = bookingSeats.stream()
                .map(bookingSeat -> bookingSeat.getEventSeat().getSeat().getId())
                .toList();
        if (!holdRegistry.allHeldBy(event.getId(), booking.getUser().getId(), seatIds)) {
            booking.setStatus(BookingStatus.EXPIRED);
            throw new GoneException("Your hold expired, please pick your seats again");
        }

        List<Long> eventSeatIds = bookingSeats.stream()
                .map(bookingSeat -> bookingSeat.getEventSeat().getId())
                .toList();
        List<EventSeat> eventSeats = eventSeatRepository.findAllForConfirmation(eventSeatIds);

        for (EventSeat eventSeat : eventSeats) {
            if (eventSeat.getStatus() != EventSeatStatus.AVAILABLE) {
                throw new ConflictException("Someone just took one of those seats",
                        List.of(eventSeat.getSeat().getId()));
            }
            eventSeat.setStatus(EventSeatStatus.BOOKED);
            eventSeat.setBookingId(booking.getId());
        }

        bookingSeats.forEach(bookingSeat -> bookingSeat.setConfirmed(true));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(clock.instant());

        // Flush now so an optimistic lock failure surfaces here, inside the payment transaction,
        // rather than at commit time when the response has already been built.
        eventSeatRepository.flush();

        markSoldOutIfLastSeats(event);
        return eventSeats;
    }

    private void markSoldOutIfLastSeats(Event event) {
        long remaining = eventSeatRepository.countByEventIdAndStatus(event.getId(), EventSeatStatus.AVAILABLE);
        if (remaining == 0 && event.getStatus() != EventStatus.SOLD_OUT) {
            event.setStatus(EventStatus.SOLD_OUT);
            queueStatsService.recordSoldOut(event.getId(), clock.instant());
            log.info("Event {} is sold out", event.getId());
        }
    }

    /** Called after the payment transaction commits: the seats are ours, the holds are redundant. */
    public void releaseHoldsAfterConfirmation(Long eventId, Long userId, List<Long> seatIds) {
        holdRegistry.release(eventId, userId, seatIds);
    }
}
