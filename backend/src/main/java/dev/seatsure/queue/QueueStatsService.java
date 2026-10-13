package dev.seatsure.queue;

import dev.seatsure.queue.QueueDtos.QueueStatsResponse;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the queue_stats row for an event up to date and reads it back for the fairness report. */
@Service
public class QueueStatsService {

    private final QueueStatsRepository repository;
    private final StringRedisTemplate redis;

    public QueueStatsService(QueueStatsRepository repository, StringRedisTemplate redis) {
        this.repository = repository;
        this.redis = redis;
    }

    @Transactional
    public void record(long eventId, int currentQueueLength, int joined, int admittedNow, long admittedWaitSumMs, int abandoned) {
        QueueStats stats = repository.findById(eventId).orElseGet(() -> {
            QueueStats fresh = new QueueStats();
            fresh.setEventId(eventId);
            return fresh;
        });

        stats.setPeakQueueLength(Math.max(stats.getPeakQueueLength(), currentQueueLength));
        stats.setTotalJoined(Math.max(stats.getTotalJoined(), joined));
        stats.setTotalAbandoned(stats.getTotalAbandoned() + abandoned);

        if (admittedNow > 0) {
            // Rolling average, so only the two numbers we already store are needed.
            double previousTotalWait = stats.getAvgWaitSeconds() * stats.getTotalAdmitted();
            int newAdmitted = stats.getTotalAdmitted() + admittedNow;
            stats.setAvgWaitSeconds((previousTotalWait + admittedWaitSumMs / 1000.0) / newAdmitted);
            stats.setTotalAdmitted(newAdmitted);
        }
        repository.save(stats);
    }

    @Transactional
    public void recordSoldOut(long eventId, Instant soldOutAt) {
        QueueStats stats = repository.findById(eventId).orElseGet(() -> {
            QueueStats fresh = new QueueStats();
            fresh.setEventId(eventId);
            return fresh;
        });
        if (stats.getSoldOutAt() == null) {
            stats.setSoldOutAt(soldOutAt);
            repository.save(stats);
        }
    }

    @Transactional(readOnly = true)
    public QueueStatsResponse report(long eventId) {
        Long queueLength = redis.opsForZSet().zCard(QueueKeys.queue(eventId));
        Optional<QueueStats> stats = repository.findById(eventId);
        return stats.map(value -> new QueueStatsResponse(
                        value.getPeakQueueLength(),
                        value.getTotalJoined(),
                        value.getTotalAdmitted(),
                        value.getTotalAbandoned(),
                        value.getAvgWaitSeconds(),
                        queueLength == null ? 0 : queueLength,
                        value.getSoldOutAt()))
                .orElseGet(() -> new QueueStatsResponse(0, 0, 0, 0, 0, queueLength == null ? 0 : queueLength, null));
    }
}
