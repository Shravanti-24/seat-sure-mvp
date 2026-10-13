package dev.seatsure.booking;

import dev.seatsure.booking.BookingDtos.BookingResponse;
import dev.seatsure.booking.BookingDtos.HeldSeat;
import dev.seatsure.common.NotFoundException;
import dev.seatsure.event.EventSectionPrice;
import dev.seatsure.event.EventSectionPriceRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of bookings, shared by the hold, checkout and "my bookings" endpoints. */
@Service
public class BookingQueryService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final EventSectionPriceRepository priceRepository;
    private final HoldRegistry holdRegistry;

    public BookingQueryService(
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            EventSectionPriceRepository priceRepository,
            HoldRegistry holdRegistry) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.priceRepository = priceRepository;
        this.holdRegistry = holdRegistry;
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findByIdWithEvent(bookingId)
                .orElseThrow(() -> new NotFoundException("No booking with id " + bookingId));
        if (!booking.getUser().getId().equals(userId)) {
            // Do not leak the existence of other people's bookings.
            throw new NotFoundException("No booking with id " + bookingId);
        }
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listForUser(Long userId) {
        return bookingRepository.findByUser(userId).stream().map(this::toResponse).toList();
    }

    private BookingResponse toResponse(Booking booking) {
        Map<Long, Long> priceBySection = new HashMap<>();
        for (EventSectionPrice price : priceRepository.findByKeyEventId(booking.getEvent().getId())) {
            priceBySection.put(price.getKey().getSectionId(), price.getPricePaise());
        }
        List<HeldSeat> seats = bookingSeatRepository.findByBookingId(booking.getId()).stream()
                .map(bookingSeat -> new HeldSeat(
                        bookingSeat.getEventSeat().getSeat().getId(),
                        bookingSeat.getEventSeat().getId(),
                        bookingSeat.getEventSeat().getSeat().getSection().getName(),
                        bookingSeat.getEventSeat().getSeat().label(),
                        priceBySection.getOrDefault(
                                bookingSeat.getEventSeat().getSeat().getSection().getId(), 0L)))
                .toList();

        Instant holdExpiresAt = booking.getStatus() == BookingStatus.PENDING_PAYMENT
                ? booking.getCreatedAt().plus(holdRegistry.holdDuration())
                : null;

        return new BookingResponse(
                booking.getId(),
                booking.getEvent().getId(),
                booking.getEvent().getTitle(),
                booking.getEvent().getVenue().getName(),
                booking.getEvent().getStartsAt(),
                booking.getStatus(),
                booking.getTotalPaise(),
                booking.getCreatedAt(),
                booking.getConfirmedAt(),
                holdExpiresAt,
                seats);
    }
}
