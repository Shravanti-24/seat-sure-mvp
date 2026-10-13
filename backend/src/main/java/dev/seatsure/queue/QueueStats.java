package dev.seatsure.queue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** One row per event, kept up to date by the admission scheduler. Feeds the fairness report. */
@Entity
@Table(name = "queue_stats")
@Getter
@Setter
public class QueueStats {

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "peak_queue_length", nullable = false)
    private int peakQueueLength;

    @Column(name = "total_joined", nullable = false)
    private int totalJoined;

    @Column(name = "total_admitted", nullable = false)
    private int totalAdmitted;

    @Column(name = "total_abandoned", nullable = false)
    private int totalAbandoned;

    @Column(name = "avg_wait_seconds", nullable = false)
    private double avgWaitSeconds;

    @Column(name = "sold_out_at")
    private Instant soldOutAt;
}
