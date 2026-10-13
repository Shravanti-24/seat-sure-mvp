package dev.seatsure.queue;

import java.time.Instant;

public final class QueueDtos {

    private QueueDtos() {}

    public record QueueJoinResponse(long position, long estimatedWaitSeconds, Instant joinedAt) {}

    /**
     * {@code position} is 0 once admitted. The admission token is only ever returned to the user it
     * was issued to, and is required by the hold endpoint for high-demand events.
     */
    public record QueueStatusResponse(
            long position,
            long queueLength,
            long estimatedWaitSeconds,
            boolean admitted,
            String admissionToken,
            Instant admissionExpiresAt,
            boolean saleOpen,
            Instant saleOpensAt) {}

    public record QueueStatsResponse(
            int peakQueueLength,
            int totalJoined,
            int totalAdmitted,
            int totalAbandoned,
            double avgWaitSeconds,
            long currentQueueLength,
            Instant soldOutAt) {}
}
