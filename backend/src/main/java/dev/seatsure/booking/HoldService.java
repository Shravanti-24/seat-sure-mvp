package dev.seatsure.booking;

import dev.seatsure.booking.BookingDtos.BookingResponse;
import dev.seatsure.common.ConflictException;
import dev.seatsure.common.NotFoundException;
import dev.seatsure.common.SeatSureProperties;
import dev.seatsure.event.Event;
import dev.seatsure.event.EventSeat;
import dev.seatsure.event.EventSeatRepository;
import dev.seatsure.event.EventSeatStatus;
import dev.seatsure.event.EventService;
import dev.seatsure.queue.AdmissionService;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Takes seat holds. The order of the checks matters: everything cheap and local happens before the
 * Redis script runs, so the exclusive claim is held for the shortest possible time.
 */
@Service
public class HoldService {

    private static final Logger log = LoggerFactory.getLogger(HoldService.class);
    private static final Set<BookingStatus> SEATS_COUNT_TOWARDS_LIMIT =
            Set.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED);

    private final EventService eventService;
    private final EventSeatRepository eventSeatRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final PendingBookingService pendingBookingService;
    private final HoldRegistry holdRegistry;
    private final AdmissionService admissionService;
    private final BookingQueryService bookingQueryService;
    private final SeatSureProperties properties;
    private final Clock clock;

    public HoldService(
            EventService eventService,
            EventSeatRepository eventSeatRepository,
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            PendingBookingService pendingBookingService,
            HoldRegistry holdRegistry,
            AdmissionService admissionService,
            BookingQueryService bookingQueryService,
            SeatSureProperties properties,
            Clock clock) {
        this.eventService = eventService;
        this.eventSeatRepository = eventSeatRepository;
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.pendingBookingService = pendingBookingService;
        this.holdRegistry = holdRegistry;
        this.admissionService = admissionService;
        this.bookingQueryService = bookingQueryService;
        this.properties = properties;
        this.clock = clock;
    }

    public BookingResponse hold(Long eventId, Long userId, List<Long> requestedSeatIds, String admissionToken) {
        List<Long> seatIds = requestedSeatIds.stream().distinct().toList();
        Event event = eventService.require(eventId);

        if (!event.saleOpen(clock.instant())) {
            throw new ConflictException("Tickets for this event are not on sale right now");
        }
        if (event.isHighDemand()) {
            admissionService.requireAdmission(eventId, userId, admissionToken);
        }
        int maxPerRequest = Math.min(properties.hold().maxSeatsPerRequest(), event.getMaxSeatsPerUser());
        if (seatIds.size() > maxPerRequest) {
            throw new ConflictException("You can hold at most " + maxPerRequest + " seats at a time");
        }

        List<EventSeat> eventSeats = eventSeatRepository.findByEventIdAndSeatIds(eventId, seatIds);
        if (eventSeats.size() != seatIds.size()) {
            throw new NotFoundException("Some of those seats are not part of this event");
        }
        List<Long> alreadyBooked = eventSeats.stream()
                .filter(eventSeat -> eventSeat.getStatus() != EventSeatStatus.AVAILABLE)
                .map(eventSeat -> eventSeat.getSeat().getId())
                .toList();
        if (!alreadyBooked.isEmpty()) {
            throw new ConflictException("Those seats are already booked", alreadyBooked);
        }

        long alreadyMine = bookingSeatRepository.countSeatsForUser(userId, eventId, SEATS_COUNT_TOWARDS_LIMIT);
        if (alreadyMine + seatIds.size() > event.getMaxSeatsPerUser()) {
            throw new ConflictException("You can book at most " + event.getMaxSeatsPerUser()
                    + " seats for this event, and you already have " + alreadyMine);
        }

        List<Long> unavailable = holdRegistry.acquire(eventId, userId, seatIds);
        if (!unavailable.isEmpty()) {
            throw new ConflictException("Someone just grabbed those seats", unavailable);
        }

        try {
            Long bookingId = pendingBookingService.create(event, userId, eventSeats);
            return bookingQueryService.get(bookingId, userId);
        } catch (RuntimeException ex) {
            // Never leave seats locked because our own write failed.
            holdRegistry.release(eventId, userId, seatIds);
            log.error("Releasing holds for event {} after booking creation failed", eventId, ex);
            throw ex;
        }
    }

    /** The user changed their mind: give the seats straight back instead of waiting for the TTL. */
    @Transactional
    public void release(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findByIdWithEvent(bookingId)
                .orElseThrow(() -> new NotFoundException("No booking with id " + bookingId));
        if (!booking.getUser().getId().equals(userId)) {
            throw new NotFoundException("No booking with id " + bookingId);
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("That booking is " + booking.getStatus() + " and cannot be released");
        }
        List<Long> seatIds = bookingSeatRepository.findByBookingId(bookingId).stream()
                .map(bookingSeat -> bookingSeat.getEventSeat().getSeat().getId())
                .toList();
        booking.setStatus(BookingStatus.CANCELLED);
        holdRegistry.release(booking.getEvent().getId(), userId, seatIds);
    }
}
