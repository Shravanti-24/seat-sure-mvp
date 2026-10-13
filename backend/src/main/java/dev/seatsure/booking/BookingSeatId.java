package dev.seatsure.booking;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link BookingSeat}: one row per (booking, event seat). */
public class BookingSeatId implements Serializable {

    private Long booking;
    private Long eventSeat;

    public BookingSeatId() {}

    public BookingSeatId(Long booking, Long eventSeat) {
        this.booking = booking;
        this.eventSeat = eventSeat;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BookingSeatId id)) {
            return false;
        }
        return Objects.equals(booking, id.booking) && Objects.equals(eventSeat, id.eventSeat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(booking, eventSeat);
    }
}
