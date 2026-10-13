package dev.seatsure.booking;

import dev.seatsure.auth.User;
import dev.seatsure.auth.UserRepository;
import dev.seatsure.common.NotFoundException;
import dev.seatsure.event.Event;
import dev.seatsure.event.EventSeat;
import dev.seatsure.event.EventSectionPrice;
import dev.seatsure.event.EventSectionPriceRepository;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the PENDING_PAYMENT booking that belongs to a fresh set of holds. Kept as its own bean so
 * the transaction really is applied: a {@code @Transactional} method called from inside the same
 * class would bypass the proxy.
 */
@Service
public class PendingBookingService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final EventSectionPriceRepository priceRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public PendingBookingService(
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            EventSectionPriceRepository priceRepository,
            UserRepository userRepository,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.priceRepository = priceRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public Long create(Event event, Long userId, List<EventSeat> eventSeats) {
        Map<Long, Long> priceBySection = new HashMap<>();
        for (EventSectionPrice price : priceRepository.findByKeyEventId(event.getId())) {
            priceBySection.put(price.getKey().getSectionId(), price.getPricePaise());
        }
        long total = eventSeats.stream()
                .mapToLong(eventSeat -> priceBySection.getOrDefault(eventSeat.getSeat().getSection().getId(), 0L))
                .sum();

        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("No such user"));
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setEvent(event);
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalPaise(total);
        booking.setCreatedAt(clock.instant());
        bookingRepository.save(booking);

        for (EventSeat eventSeat : eventSeats) {
            BookingSeat bookingSeat = new BookingSeat();
            bookingSeat.setBooking(booking);
            bookingSeat.setEventSeat(eventSeat);
            bookingSeat.setConfirmed(false);
            bookingSeatRepository.save(bookingSeat);
        }
        return booking.getId();
    }
}
