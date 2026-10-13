package dev.seatsure.queue;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueStatsRepository extends JpaRepository<QueueStats, Long> {
}
