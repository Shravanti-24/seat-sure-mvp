package dev.seatsure.event;

/**
 * Only the two states that Postgres owns. HELD is deliberately absent: a hold is a Redis key with a
 * TTL, never a column, so an abandoned checkout can never leave a seat stuck.
 */
public enum EventSeatStatus {
    AVAILABLE,
    BOOKED
}
