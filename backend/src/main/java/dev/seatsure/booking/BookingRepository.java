package dev.seatsure.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("select b from Booking b join fetch b.event e join fetch e.venue where b.user.id = :userId order by b.createdAt desc")
    List<Booking> findByUser(Long userId);

    @Query("select b from Booking b join fetch b.event e join fetch e.venue join fetch b.user where b.id = :id")
    Optional<Booking> findByIdWithEvent(Long id);

    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, Instant createdBefore);

    long countByEventIdAndStatus(Long eventId, BookingStatus status);
}
