package dev.seatsure.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.seatsure.event.EventSeat;
import dev.seatsure.event.EventSeatRepository;
import dev.seatsure.event.EventSeatStatus;
import dev.seatsure.support.AbstractIntegrationTest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The two database-level guarantees, tested on their own rather than through the API, because the
 * Redis holds normally stop anyone from getting this far. These are what still protects the data if
 * Redis is flushed, fails over, or loses a key.
 */
class DoubleBookingGuardsTest extends AbstractIntegrationTest {

    @Autowired private EventSeatRepository eventSeatRepository;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("optimistic locking: a transaction that read a seat cannot book it after someone else did")
    void optimisticLockingRejectsTheSecondWriter() throws Exception {
        Long eventSeatId = jdbc.queryForObject(
                "select id from event_seat where status = 'AVAILABLE' order by id limit 1", Long.class);

        CountDownLatch loserHasRead = new CountDownLatch(1);
        CountDownLatch winnerHasCommitted = new CountDownLatch(1);
        AtomicReference<Throwable> loserFailure = new AtomicReference<>();
        AtomicReference<Throwable> winnerFailure = new AtomicReference<>();

        // The loser reads the seat first, then stalls on the checkout page while somebody else pays.
        Thread loser = new Thread(() -> run(() -> transactions.executeWithoutResult(status -> {
            EventSeat seat = eventSeatRepository.findAllForConfirmation(List.of(eventSeatId)).get(0);
            loserHasRead.countDown();
            await(winnerHasCommitted);
            seat.setStatus(EventSeatStatus.BOOKED);
            eventSeatRepository.flush();
        }), loserFailure));

        Thread winner = new Thread(() -> run(() -> {
            await(loserHasRead);
            transactions.executeWithoutResult(status -> {
                EventSeat seat = eventSeatRepository.findAllForConfirmation(List.of(eventSeatId)).get(0);
                seat.setStatus(EventSeatStatus.BOOKED);
                eventSeatRepository.flush();
            });
            winnerHasCommitted.countDown();
        }, winnerFailure));

        loser.start();
        winner.start();
        winner.join(15_000);
        loser.join(15_000);

        assertThat(winnerFailure.get()).isNull();
        assertThat(loserFailure.get())
                .as("the transaction that read a stale version must be rejected")
                .isInstanceOf(OptimisticLockingFailureException.class);

        // The seat belongs to the winner, and its version moved exactly once.
        EventSeat seat = eventSeatRepository.findById(eventSeatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(EventSeatStatus.BOOKED);
        assertThat(seat.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("the partial unique index refuses a second confirmed booking for one seat")
    void uniqueIndexRejectsASecondConfirmedBookingSeat() {
        Long userId = jdbc.queryForObject("select id from users order by id limit 1", Long.class);
        Long eventSeatId = jdbc.queryForObject(
                "select id from event_seat where status = 'AVAILABLE' order by id desc limit 1", Long.class);
        Long eventId = jdbc.queryForObject("select event_id from event_seat where id = ?", Long.class, eventSeatId);

        Long bookingA = insertBooking(userId, eventId);
        Long bookingB = insertBooking(userId, eventId);

        insertConfirmedBookingSeat(bookingA, eventSeatId);

        // An unconfirmed row for the same seat is fine: that is an expired hold's paperwork.
        jdbc.update("insert into booking_seat (booking_id, event_seat_id, confirmed) values (?, ?, false)",
                bookingB, eventSeatId);

        assertThatThrownBy(() -> jdbc.update(
                        "update booking_seat set confirmed = true where booking_id = ? and event_seat_id = ?",
                        bookingB, eventSeatId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long insertBooking(Long userId, Long eventId) {
        return jdbc.queryForObject(
                """
                insert into booking (user_id, event_id, status, total_paise)
                values (?, ?, 'CONFIRMED', 0) returning id
                """,
                Long.class, userId, eventId);
    }

    private void insertConfirmedBookingSeat(Long bookingId, Long eventSeatId) {
        jdbc.update("insert into booking_seat (booking_id, event_seat_id, confirmed) values (?, ?, true)",
                bookingId, eventSeatId);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The other transaction never got there");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void run(Runnable action, AtomicReference<Throwable> failure) {
        try {
            action.run();
        } catch (Throwable throwable) {
            failure.set(throwable);
        }
    }
}
