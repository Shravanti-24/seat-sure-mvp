package dev.seatsure.booking;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, BookingSeatId> {

    @Query("""
            select bs from BookingSeat bs
            join fetch bs.eventSeat es
            join fetch es.seat s
            join fetch s.section
            where bs.booking.id = :bookingId
            order by s.section.id, s.rowLabel, s.seatNumber
            """)
    List<BookingSeat> findByBookingId(Long bookingId);

    /**
     * Anti-hoarding: how many seats this user already holds or owns for this event. Counted in the
     * database so a scripted client cannot talk its way past the limit.
     */
    @Query("""
            select count(bs) from BookingSeat bs
            where bs.booking.user.id = :userId
              and bs.booking.event.id = :eventId
              and bs.booking.status in :statuses
            """)
    long countSeatsForUser(Long userId, Long eventId, Collection<BookingStatus> statuses);
}
