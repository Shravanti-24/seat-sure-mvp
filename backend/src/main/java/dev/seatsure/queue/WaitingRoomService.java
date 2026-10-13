package dev.seatsure.queue;

import dev.seatsure.common.ConflictException;
import dev.seatsure.common.SeatSureProperties;
import dev.seatsure.event.Event;
import dev.seatsure.event.EventService;
import dev.seatsure.queue.QueueDtos.QueueJoinResponse;
import dev.seatsure.queue.QueueDtos.QueueStatusResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * The virtual waiting room. Arrival order is a Redis sorted set scored by join time, so refreshing
 * the page, opening a second tab or hammering the endpoint cannot move a user forward: the join uses
 * ZADD NX, and only the scheduler ever removes anyone.
 */
@Service
public class WaitingRoomService {

    private final StringRedisTemplate redis;
    private final EventService eventService;
    private final AdmissionService admissionService;
    private final SeatSureProperties properties;
    private final Clock clock;

    public WaitingRoomService(
            StringRedisTemplate redis,
            EventService eventService,
            AdmissionService admissionService,
            SeatSureProperties properties,
            Clock clock) {
        this.redis = redis;
        this.eventService = eventService;
        this.admissionService = admissionService;
        this.properties = properties;
        this.clock = clock;
    }

    public QueueJoinResponse join(Long eventId, Long userId) {
        Event event = eventService.require(eventId);
        if (!event.isHighDemand()) {
            throw new ConflictException("This event has no waiting room, go straight to the seat map");
        }
        Instant now = clock.instant();
        if (now.isBefore(event.getSaleOpensAt())) {
            throw new ConflictException("The queue opens when the sale opens");
        }

        String member = String.valueOf(userId);
        Boolean added = redis.opsForZSet()
                .addIfAbsent(QueueKeys.queue(eventId), member, now.toEpochMilli());
        if (Boolean.TRUE.equals(added)) {
            redis.opsForValue().increment(QueueKeys.joined(eventId));
        }
        touch(eventId, userId, now);

        long position = position(eventId, member);
        return new QueueJoinResponse(position, estimatedWait(position), now);
    }

    public QueueStatusResponse status(Long eventId, Long userId) {
        Event event = eventService.require(eventId);
        Instant now = clock.instant();
        String member = String.valueOf(userId);

        // Polling is also the heartbeat: a user who stops polling is counted as abandoned.
        touch(eventId, userId, now);

        String token = admissionService.tokenFor(eventId, userId);
        Long ttl = token == null ? null : admissionService.admissionTtlSeconds(eventId, userId);
        long position = token != null ? 0 : position(eventId, member);
        Long queueLength = redis.opsForZSet().zCard(QueueKeys.queue(eventId));

        return new QueueStatusResponse(
                position,
                queueLength == null ? 0 : queueLength,
                token != null ? 0 : estimatedWait(position),
                token != null,
                token,
                ttl == null || ttl < 0 ? null : now.plusSeconds(ttl),
                event.saleOpen(now),
                event.getSaleOpensAt());
    }

    private void touch(Long eventId, Long userId, Instant now) {
        redis.opsForHash().put(QueueKeys.lastSeen(eventId), String.valueOf(userId), String.valueOf(now.toEpochMilli()));
    }

    /** 1-based place in line, or -1 when the user is not queued at all. */
    private long position(Long eventId, String member) {
        Long rank = redis.opsForZSet().rank(QueueKeys.queue(eventId), member);
        return rank == null ? -1 : rank + 1;
    }

    /** Batches of {@code batchSize} leave every scheduler tick, so waiting time is simple to project. */
    private long estimatedWait(long position) {
        if (position <= 0) {
            return 0;
        }
        int batchSize = Math.max(1, properties.queue().batchSize());
        long batchesAhead = (position - 1) / batchSize;
        long tickSeconds = Math.max(1, Duration.ofMillis(admissionService.tickIntervalMs()).toSeconds());
        return batchesAhead * tickSeconds;
    }
}
