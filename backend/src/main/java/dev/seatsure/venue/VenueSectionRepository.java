package dev.seatsure.venue;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueSectionRepository extends JpaRepository<VenueSection, Long> {

    List<VenueSection> findByVenueIdOrderByIdAsc(Long venueId);
}
