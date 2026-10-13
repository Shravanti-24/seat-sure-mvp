package dev.seatsure.venue;

import dev.seatsure.common.NotFoundException;
import dev.seatsure.venue.VenueDtos.CreateVenueRequest;
import dev.seatsure.venue.VenueDtos.SectionRequest;
import dev.seatsure.venue.VenueDtos.SectionResponse;
import dev.seatsure.venue.VenueDtos.VenueResponse;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VenueService {

    private final VenueRepository venueRepository;
    private final Clock clock;

    public VenueService(VenueRepository venueRepository, Clock clock) {
        this.venueRepository = venueRepository;
        this.clock = clock;
    }

    /** Creating a venue also materialises every seat, so that events can reference them directly. */
    @Transactional
    public VenueResponse create(CreateVenueRequest request) {
        Venue venue = new Venue();
        venue.setName(request.name().trim());
        venue.setCity(request.city().trim());
        venue.setCreatedAt(clock.instant());

        for (SectionRequest sectionRequest : request.sections()) {
            VenueSection section = new VenueSection();
            section.setName(sectionRequest.name().trim());
            section.setRowCount(sectionRequest.rowCount());
            section.setSeatsPerRow(sectionRequest.seatsPerRow());
            venue.addSection(section);

            for (int row = 0; row < sectionRequest.rowCount(); row++) {
                String rowLabel = rowLabel(row);
                for (int number = 1; number <= sectionRequest.seatsPerRow(); number++) {
                    Seat seat = new Seat();
                    seat.setRowLabel(rowLabel);
                    seat.setSeatNumber(number);
                    section.addSeat(seat);
                }
            }
        }
        return toResponse(venueRepository.save(venue));
    }

    @Transactional(readOnly = true)
    public List<VenueResponse> list() {
        return venueRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public VenueResponse get(Long id) {
        return toResponse(venueRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No venue with id " + id)));
    }

    /** A, B, ... Z, AA, AB, ... so a section can have more than 26 rows. */
    static String rowLabel(int zeroBasedIndex) {
        StringBuilder label = new StringBuilder();
        int index = zeroBasedIndex;
        do {
            label.insert(0, (char) ('A' + index % 26));
            index = index / 26 - 1;
        } while (index >= 0);
        return label.toString();
    }

    private VenueResponse toResponse(Venue venue) {
        List<SectionResponse> sections = venue.getSections().stream()
                .map(section -> new SectionResponse(
                        section.getId(),
                        section.getName(),
                        section.getRowCount(),
                        section.getSeatsPerRow(),
                        section.getRowCount() * section.getSeatsPerRow()))
                .toList();
        int totalSeats = sections.stream().mapToInt(SectionResponse::seatCount).sum();
        return new VenueResponse(venue.getId(), venue.getName(), venue.getCity(), totalSeats, sections);
    }
}
