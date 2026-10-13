package dev.seatsure.event;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("select e from Event e join fetch e.venue where e.status <> 'DRAFT' order by e.startsAt asc")
    List<Event> findPublished();

    @Query("select e from Event e join fetch e.venue order by e.startsAt asc")
    List<Event> findAllWithVenue();

    @Query("select e from Event e join fetch e.venue where e.id = :id")
    Optional<Event> findByIdWithVenue(Long id);

    List<Event> findByHighDemandTrueAndStatus(EventStatus status);
}
