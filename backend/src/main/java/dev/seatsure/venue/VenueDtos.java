package dev.seatsure.venue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class VenueDtos {

    private VenueDtos() {}

    public record CreateVenueRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 120) String city,
            @NotEmpty List<@Valid SectionRequest> sections) {}

    public record SectionRequest(
            @NotBlank @Size(max = 80) String name,
            @Min(1) @Max(60) int rowCount,
            @Min(1) @Max(60) int seatsPerRow) {}

    public record VenueResponse(Long id, String name, String city, int totalSeats, List<SectionResponse> sections) {}

    public record SectionResponse(Long id, String name, int rowCount, int seatsPerRow, int seatCount) {}
}
