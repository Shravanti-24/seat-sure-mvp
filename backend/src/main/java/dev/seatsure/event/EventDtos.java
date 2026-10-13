package dev.seatsure.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class EventDtos {

    private EventDtos() {}

    public record EventSummary(
            Long id,
            String title,
            String venueName,
            String venueCity,
            Instant startsAt,
            Instant saleOpensAt,
            boolean highDemand,
            EventStatus status,
            int maxSeatsPerUser,
            Long minPricePaise,
            Long maxPricePaise,
            long availableSeats,
            long totalSeats) {}

    public record EventDetail(EventSummary summary, String description, List<SectionPrice> prices) {}

    public record SectionPrice(Long sectionId, String sectionName, long pricePaise) {}

    /** One seat on the map. {@code status} merges Postgres truth with the live Redis holds. */
    public record SeatMapSeat(
            Long seatId,
            Long eventSeatId,
            String rowLabel,
            int seatNumber,
            String label,
            SeatMapStatus status) {}

    public record SeatMapSection(
            Long sectionId, String sectionName, long pricePaise, int rowCount, int seatsPerRow, List<SeatMapSeat> seats) {}

    public record SeatMapResponse(
            Long eventId,
            EventStatus status,
            boolean highDemand,
            int maxSeatsPerUser,
            Instant saleOpensAt,
            List<SeatMapSection> sections) {}

    public enum SeatMapStatus {
        AVAILABLE,
        HELD,
        BOOKED
    }

    public record CreateEventRequest(
            @NotNull Long venueId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @NotNull Instant startsAt,
            @NotNull Instant saleOpensAt,
            boolean highDemand,
            @Min(1) Integer maxSeatsPerUser,
            @NotNull EventStatus status,
            @NotEmpty List<@Valid SectionPriceRequest> prices) {}

    public record UpdateEventRequest(
            @Size(max = 200) String title,
            @Size(max = 2000) String description,
            Instant startsAt,
            Instant saleOpensAt,
            Boolean highDemand,
            @Min(1) Integer maxSeatsPerUser,
            EventStatus status) {}

    public record SectionPriceRequest(@NotNull Long sectionId, @Min(0) long pricePaise) {}
}
