package dev.seatsure.booking;

import dev.seatsure.event.EventSeat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Links a booking to the seats it covers. Rows are written when the hold is taken, and flipped to
 * {@code confirmed} inside the payment transaction. A partial unique index on
 * {@code (event_seat_id) where confirmed} is the second, database-level guarantee that a seat can
 * belong to at most one confirmed booking.
 */
@Entity
@Table(name = "booking_seat")
@IdClass(BookingSeatId.class)
@Getter
@Setter
public class BookingSeat {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_seat_id", nullable = false)
    private EventSeat eventSeat;

    @Column(nullable = false)
    private boolean confirmed;
}
