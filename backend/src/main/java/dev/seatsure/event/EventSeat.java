package dev.seatsure.event;

import dev.seatsure.venue.Seat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * One physical seat offered for one event: the row Postgres guards with {@code @Version} so that two
 * concurrent confirmations can never both claim it.
 *
 * <p>{@code bookingId} is a plain column rather than a {@code @ManyToOne} to keep the event package
 * free of a dependency on the booking package.
 */
@Entity
@Table(name = "event_seat")
@Getter
@Setter
public class EventSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventSeatStatus status = EventSeatStatus.AVAILABLE;

    @Column(name = "booking_id")
    private Long bookingId;

    @Version
    @Column(nullable = false)
    private long version;
}
