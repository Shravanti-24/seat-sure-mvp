package dev.seatsure.queue;

/** Every Redis key the waiting room uses, in one place. */
final class QueueKeys {

    private QueueKeys() {}

    /** Sorted set of user ids, scored by the millisecond they joined. Arrival order is the score. */
    static String queue(long eventId) {
        return "queue:" + eventId;
    }

    /** Hash of userId to last poll time, used to drop users who walked away. */
    static String lastSeen(long eventId) {
        return "lastSeen:" + eventId;
    }

    /** The admission token a user must present to the hold endpoint. */
    static String admission(long eventId, long userId) {
        return "admit:" + eventId + ":" + userId;
    }

    /** Counters the scheduler flushes into queue_stats. */
    static String joined(long eventId) {
        return "queueJoined:" + eventId;
    }
}
