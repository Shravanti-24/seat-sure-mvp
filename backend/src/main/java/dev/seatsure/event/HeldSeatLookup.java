package dev.seatsure.event;

import java.util.Collection;
import java.util.Set;

/**
 * Lets the seat map ask "which of these seats are currently held?" without the event package
 * depending on the booking package. Implemented by the Redis hold registry.
 */
public interface HeldSeatLookup {

    Set<Long> heldSeatIds(long eventId, Collection<Long> seatIds);
}
