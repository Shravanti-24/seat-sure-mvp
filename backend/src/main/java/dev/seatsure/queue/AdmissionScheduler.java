package dev.seatsure.queue;

import dev.seatsure.common.SeatSureProperties;
import dev.seatsure.event.Event;
import dev.seatsure.event.EventRepository;
import dev.seatsure.event.EventStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lets the next batch of people out of the waiting room, in arrival order, every tick. This is the
 * whole fairness mechanism: the front of the sorted set is popped, so being early is the only thing
 * that helps.
 */
@Component
public class AdmissionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AdmissionScheduler.class);

    private final StringRedisTemplate redis;
    private final EventRepository eventRepository;
    private final AdmissionService admissionService;
    private final QueueStatsService queueStatsService;
    private final SeatSureProperties properties;
    private final Clock clock;

    public AdmissionScheduler(
            StringRedisTemplate redis,
            EventRepository eventRepository,
            AdmissionService admissionService,
            QueueStatsService queueStatsService,
            SeatSureProperties properties,
            Clock clock) {
        this.redis = redis;
        this.eventRepository = eventRepository;
        this.admissionService = admissionService;
        this.queueStatsService = queueStatsService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${seatsure.queue.tick-interval-ms}")
    public void admitNextBatch() {
        List<Event> events = eventRepository.findByHighDemandTrueAndStatus(EventStatus.ON_SALE);
        for (Event event : events) {
            try {
                tick(event);
            } catch (RuntimeException ex) {
                log.error("Admission tick failed for event {}", event.getId(), ex);
            }
        }
    }

    private void tick(Event event) {
        long eventId = event.getId();
        Instant now = clock.instant();
        if (now.isBefore(event.getSaleOpensAt())) {
            return;
        }

        int abandoned = dropAbandoned(eventId, now);

        Set<TypedTuple<String>> batch =
                redis.opsForZSet().popMin(QueueKeys.queue(eventId), properties.queue().batchSize());
        int admitted = 0;
        long waitSumMs = 0;
        List<String> admittedUsers = new ArrayList<>();
        if (batch != null) {
            for (TypedTuple<String> entry : batch) {
                String member = entry.getValue();
                if (member == null) {
                    continue;
                }
                admissionService.admit(eventId, Long.parseLong(member));
                admittedUsers.add(member);
                admitted++;
                if (entry.getScore() != null) {
                    waitSumMs += Math.max(0, now.toEpochMilli() - entry.getScore().longValue());
                }
            }
        }

        Long queueLength = redis.opsForZSet().zCard(QueueKeys.queue(eventId));
        String joinedRaw = redis.opsForValue().get(QueueKeys.joined(eventId));
        int joined = joinedRaw == null ? 0 : Integer.parseInt(joinedRaw);

        // Peak has to include the people we just let out, otherwise popping hides the real maximum.
        int peakCandidate = (int) ((queueLength == null ? 0 : queueLength) + admitted);
        queueStatsService.record(eventId, peakCandidate, joined, admitted, waitSumMs, abandoned);

        if (admitted > 0) {
            log.info("Admitted {} user(s) to event {}, {} still waiting", admitted, eventId,
                    queueLength == null ? 0 : queueLength);
        }
        admittedUsers.forEach(member -> redis.opsForHash().delete(QueueKeys.lastSeen(eventId), member));
    }

    /** Someone who stops polling has left the queue; their place should not block the people behind. */
    private int dropAbandoned(long eventId, Instant now) {
        long cutoff = now.toEpochMilli() - properties.queue().abandonAfterSeconds() * 1000;
        Map<Object, Object> lastSeen = redis.opsForHash().entries(QueueKeys.lastSeen(eventId));
        int abandoned = 0;
        for (Map.Entry<Object, Object> entry : lastSeen.entrySet()) {
            String member = String.valueOf(entry.getKey());
            long seenAt;
            try {
                seenAt = Long.parseLong(String.valueOf(entry.getValue()));
            } catch (NumberFormatException ex) {
                continue;
            }
            if (seenAt >= cutoff) {
                continue;
            }
            Long removed = redis.opsForZSet().remove(QueueKeys.queue(eventId), member);
            redis.opsForHash().delete(QueueKeys.lastSeen(eventId), member);
            if (removed != null && removed > 0) {
                abandoned++;
            }
        }
        if (abandoned > 0) {
            log.info("Dropped {} user(s) from the queue for event {} after no polling", abandoned, eventId);
        }
        return abandoned;
    }
}
