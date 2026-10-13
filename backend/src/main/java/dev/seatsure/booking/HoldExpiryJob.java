package dev.seatsure.booking;

import dev.seatsure.common.SeatSureProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Redis TTL already freed the seats. This job only tidies up the paperwork: a booking nobody
 * paid for becomes EXPIRED so it stops counting against the user's seat limit.
 */
@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final BookingRepository bookingRepository;
    private final SeatSureProperties properties;
    private final Clock clock;

    public HoldExpiryJob(BookingRepository bookingRepository, SeatSureProperties properties, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${seatsure.hold.expiry-job-interval-ms}")
    @Transactional
    public void expireStaleBookings() {
        Instant cutoff = clock.instant()
                .minusSeconds(properties.hold().durationSeconds() + properties.hold().expiryJobGraceSeconds());
        List<Booking> stale = bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING_PAYMENT, cutoff);
        if (stale.isEmpty()) {
            return;
        }
        stale.forEach(booking -> booking.setStatus(BookingStatus.EXPIRED));
        log.info("Marked {} unpaid booking(s) as EXPIRED", stale.size());
    }
}
