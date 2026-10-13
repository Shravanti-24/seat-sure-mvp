package dev.seatsure.event;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EventSeatRepository extends JpaRepository<EventSeat, Long> {

    @Query("""
            select es from EventSeat es
            join fetch es.seat s
            join fetch s.section
            where es.event.id = :eventId
            order by s.section.id, s.rowLabel, s.seatNumber
            """)
    List<EventSeat> findSeatMap(Long eventId);

    @Query("select es from EventSeat es join fetch es.seat where es.event.id = :eventId and es.seat.id in :seatIds")
    List<EventSeat> findByEventIdAndSeatIds(Long eventId, Collection<Long> seatIds);

    /**
     * Read with optimistic locking forced, so the version is checked on write even if nothing on the
     * row changed. This is the read used by the payment confirmation.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select es from EventSeat es where es.id in :ids")
    List<EventSeat> findAllByIdForUpdate(Collection<Long> ids);

    long countByEventIdAndStatus(Long eventId, EventSeatStatus status);

}
