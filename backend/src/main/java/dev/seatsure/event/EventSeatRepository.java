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
     * The read used by the payment confirmation. {@code OPTIMISTIC} states the intent explicitly:
     * these rows are about to be written, and the version read here must still be the version in the
     * database when the write lands, or the transaction loses the race and rolls back.
     */
    @Lock(LockModeType.OPTIMISTIC)
    @Query("select es from EventSeat es where es.id in :ids")
    List<EventSeat> findAllForConfirmation(Collection<Long> ids);

    long countByEventIdAndStatus(Long eventId, EventSeatStatus status);

}
