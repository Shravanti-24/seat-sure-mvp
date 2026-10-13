package dev.seatsure.booking;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class BookingDtos {

    private BookingDtos() {}

    public record CreateHoldRequest(
            @NotEmpty(message = "pick at least one seat") @Size(max = 6, message = "at most 6 seats") List<Long> seatIds) {}

    public record HeldSeat(Long seatId, Long eventSeatId, String sectionName, String label, long pricePaise) {}

    public record BookingResponse(
            Long bookingId,
            Long eventId,
            String eventTitle,
            String venueName,
            Instant eventStartsAt,
            BookingStatus status,
            long totalPaise,
            Instant createdAt,
            Instant confirmedAt,
            Instant holdExpiresAt,
            List<HeldSeat> seats) {}
}
