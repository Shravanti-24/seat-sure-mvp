package dev.seatsure.ticket;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class TicketDtos {

    private TicketDtos() {}

    public record TicketResponse(
            Long id,
            Long bookingId,
            Long eventId,
            String eventTitle,
            String venueName,
            String venueCity,
            Instant eventStartsAt,
            String sectionName,
            String seatLabel,
            Instant checkedInAt) {}

    public record CheckInRequest(@NotBlank String qrContent) {}

    public record CheckInResponse(
            Result result,
            String message,
            String attendeeName,
            String eventTitle,
            String sectionName,
            String seatLabel,
            Instant checkedInAt) {}

    public enum Result {
        OK,
        ALREADY_USED
    }
}
