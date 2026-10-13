package dev.seatsure.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.seatsure.support.AbstractIntegrationTest;
import dev.seatsure.support.ApiClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The headline guarantee: 50 threads go for the same 10 seats through the real HTTP API, and
 * afterwards exactly 10 seats are booked, each belonging to exactly one confirmed booking.
 */
class SeatBookingConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREADS = 50;
    private static final int SEATS = 10;
    private static final long EVENT_ID = 2L;

    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("50 threads, 10 seats: 10 confirmed seats, no seat sold twice")
    void fiftyThreadsCannotDoubleBookTenSeats() throws Exception {
        ApiClient api = new ApiClient(rest);
        List<Long> contestedSeats = api.availableSeatIds(EVENT_ID, SEATS);
        assertThat(contestedSeats).hasSize(SEATS);

        List<String> tokens = new java.util.ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tokens.add(api.registerAndLogin("storm" + i + "@test.dev", "Storm " + i));
        }

        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger holdConflicts = new AtomicInteger();
        AtomicInteger payConflicts = new AtomicInteger();
        CountDownLatch startTogether = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(THREADS);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        for (int i = 0; i < THREADS; i++) {
            final String token = tokens.get(i);
            // Every thread wants one seat from the contested pool; many want the same one.
            final List<Long> wanted = List.of(contestedSeats.get(i % SEATS));
            pool.submit(() -> {
                try {
                    startTogether.await();
                    ResponseEntity<Map> hold = api.hold(token, EVENT_ID, wanted, null);
                    if (hold.getStatusCode() != HttpStatus.CREATED) {
                        holdConflicts.incrementAndGet();
                        return;
                    }
                    long bookingId = ((Number) hold.getBody().get("bookingId")).longValue();
                    ResponseEntity<Map> pay = api.pay(token, bookingId, ApiClient.newIdempotencyKey(), false);
                    if (pay.getStatusCode() == HttpStatus.OK) {
                        confirmed.incrementAndGet();
                    } else {
                        payConflicts.incrementAndGet();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }

        startTogether.countDown();
        assertThat(finished.await(120, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // Exactly one winner per contested seat.
        assertThat(confirmed.get()).isEqualTo(SEATS);
        assertThat(holdConflicts.get() + payConflicts.get()).isEqualTo(THREADS - SEATS);

        Long bookedSeats = jdbc.queryForObject(
                "select count(*) from event_seat where event_id = ? and status = 'BOOKED' and id in "
                        + "(select es.id from event_seat es where es.event_id = ? and es.seat_id = any (?))",
                Long.class, EVENT_ID, EVENT_ID, contestedSeats.toArray(new Long[0]));
        assertThat(bookedSeats).isEqualTo(SEATS);

        // No event seat is in more than one confirmed booking anywhere in the database.
        Long seatsSoldTwice = jdbc.queryForObject(
                """
                select count(*) from (
                    select bs.event_seat_id
                    from booking_seat bs
                             join booking b on b.id = bs.booking_id
                    where b.status = 'CONFIRMED'
                    group by bs.event_seat_id
                    having count(*) > 1
                ) as duplicates
                """,
                Long.class);
        assertThat(seatsSoldTwice).isZero();

        // Every confirmed booking has exactly one successful payment.
        Long bookingsWithWrongPaymentCount = jdbc.queryForObject(
                """
                select count(*) from booking b
                where b.status = 'CONFIRMED'
                  and (select count(*) from payment p where p.booking_id = b.id and p.status = 'SUCCESS') <> 1
                """,
                Long.class);
        assertThat(bookingsWithWrongPaymentCount).isZero();

        // One ticket per confirmed seat.
        Long ticketMismatch = jdbc.queryForObject(
                """
                select count(*) from booking b
                where b.status = 'CONFIRMED'
                  and (select count(*) from ticket t where t.booking_id = b.id)
                   <> (select count(*) from booking_seat bs where bs.booking_id = b.id and bs.confirmed)
                """,
                Long.class);
        assertThat(ticketMismatch).isZero();
    }
}
