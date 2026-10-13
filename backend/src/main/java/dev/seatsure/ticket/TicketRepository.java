package dev.seatsure.ticket;

import dev.seatsure.auth.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("""
            select t from Ticket t
            join fetch t.booking b
            join fetch b.event e
            join fetch e.venue
            join fetch t.eventSeat es
            join fetch es.seat s
            join fetch s.section
            where b.user.id = :userId and b.status = 'CONFIRMED'
            order by e.startsAt asc, t.id asc
            """)
    List<Ticket> findForUser(Long userId);

    @Query("""
            select t from Ticket t
            join fetch t.booking b
            join fetch b.event e
            join fetch e.venue
            join fetch b.user
            join fetch t.eventSeat es
            join fetch es.seat s
            join fetch s.section
            where t.id = :id
            """)
    Optional<Ticket> findDetailed(Long id);

    /**
     * Replay protection. The {@code checked_in_at is null} predicate means the database, not the
     * application, decides who wins when the same QR is scanned twice at the same moment.
     *
     * @return 1 when this scan checked the ticket in, 0 when it had already been used
     */
    @Modifying
    @Query("update Ticket t set t.checkedInAt = :now, t.checkedInBy = :staff where t.id = :id and t.checkedInAt is null")
    int markCheckedIn(Long id, Instant now, User staff);

    long countByBookingEventIdAndCheckedInAtIsNotNull(Long eventId);

    long countByBookingEventId(Long eventId);
}
