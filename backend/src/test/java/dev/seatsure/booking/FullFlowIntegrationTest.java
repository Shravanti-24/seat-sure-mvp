package dev.seatsure.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.seatsure.support.AbstractIntegrationTest;
import dev.seatsure.support.ApiClient;
import dev.seatsure.ticket.QrCodeService;
import dev.seatsure.ticket.Ticket;
import dev.seatsure.ticket.TicketRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The whole journey against real Postgres and Redis: queue, admission, hold, pay, ticket, gate. */
class FullFlowIntegrationTest extends AbstractIntegrationTest {

    /** Seeded by V2: the high-demand event with the waiting room. */
    private static final long HIGH_DEMAND_EVENT = 1L;
    /** Seeded by V2: a normal sale, no queue. */
    private static final long NORMAL_EVENT = 2L;

    @Autowired private TicketRepository ticketRepository;
    @Autowired private QrCodeService qrCodeService;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(rest);
    }

    @Test
    @DisplayName("queue -> admission -> hold -> pay -> ticket -> check-in")
    @SuppressWarnings("unchecked")
    void happyPathThroughTheWaitingRoom() throws Exception {
        String attendee = api.registerAndLogin("flow@test.dev", "Flow Tester");
        String staff = api.login("gate@seatsure.dev", "gate12345");

        // 1. Join the queue for the high-demand event.
        ResponseEntity<Map> join = api.joinQueue(attendee, HIGH_DEMAND_EVENT);
        assertThat(join.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) join.getBody().get("position")).longValue()).isPositive();

        // 2. Without an admission token the hold endpoint refuses outright.
        List<Long> seatIds = api.availableSeatIds(HIGH_DEMAND_EVENT, 2);
        assertThat(api.hold(attendee, HIGH_DEMAND_EVENT, seatIds, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // 3. Wait for the admission scheduler to let this batch out.
        String admissionToken = awaitAdmission(attendee);
        assertThat(admissionToken).isNotBlank();

        // 4. Hold two seats.
        ResponseEntity<Map> hold = api.hold(attendee, HIGH_DEMAND_EVENT, seatIds, admissionToken);
        assertThat(hold.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) hold.getBody().get("status")).isEqualTo("PENDING_PAYMENT");
        long bookingId = ((Number) hold.getBody().get("bookingId")).longValue();

        // The seats now read HELD on the public seat map, without any column having changed.
        assertThat(api.availableSeatIds(HIGH_DEMAND_EVENT, 500)).doesNotContainAnyElementsOf(seatIds);

        // 5. Pay.
        ResponseEntity<Map> pay = api.pay(attendee, bookingId, ApiClient.newIdempotencyKey(), false);
        assertThat(pay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) pay.getBody().get("status")).isEqualTo("SUCCESS");
        assertThat((String) pay.getBody().get("bookingStatus")).isEqualTo("CONFIRMED");
        assertThat((List<Object>) pay.getBody().get("ticketIds")).hasSize(2);

        // 6. Two tickets exist for the attendee.
        ResponseEntity<List> tickets = api.myTickets(attendee);
        assertThat(tickets.getBody()).hasSize(2);
        long ticketId = ((Number) ((Map<String, Object>) tickets.getBody().get(0)).get("id")).longValue();

        // 7. The gate scans the QR code off the ticket.
        Ticket ticket = ticketRepository.findDetailed(ticketId).orElseThrow();
        ResponseEntity<Map> checkIn = api.checkIn(staff, qrCodeService.qrContent(ticket));
        assertThat(checkIn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) checkIn.getBody().get("result")).isEqualTo("OK");
        assertThat((String) checkIn.getBody().get("attendeeName")).isEqualTo("Flow Tester");
    }

    @Test
    @DisplayName("the same ticket cannot be checked in twice")
    void checkingInTwiceIsRejected() {
        String attendee = api.registerAndLogin("twice@test.dev", "Twice Tester");
        String staff = api.login("gate@seatsure.dev", "gate12345");

        long ticketId = bookOneSeatAndGetTicketId(attendee);
        Ticket ticket = ticketRepository.findDetailed(ticketId).orElseThrow();
        String qr = qrCodeService.qrContent(ticket);

        ResponseEntity<Map> first = api.checkIn(staff, qr);
        assertThat((String) first.getBody().get("result")).isEqualTo("OK");

        ResponseEntity<Map> second = api.checkIn(staff, qr);
        assertThat((String) second.getBody().get("result")).isEqualTo("ALREADY_USED");
        assertThat((String) second.getBody().get("message")).startsWith("Already used at");
    }

    @Test
    @DisplayName("a tampered QR code is rejected before any database work")
    void tamperedQrIsRejected() {
        String staff = api.login("gate@seatsure.dev", "gate12345");

        ResponseEntity<Map> response = api.checkIn(staff, "1.9bd1a0ba-2f8a-4d06-bd6f-6e0a1f3e6c21.forged");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("message")).contains("tampered");
    }

    @Test
    @DisplayName("paying twice with the same Idempotency-Key creates one booking and one payment")
    @SuppressWarnings("unchecked")
    void retryingWithTheSameKeyChargesOnce() {
        String attendee = api.registerAndLogin("idem@test.dev", "Idem Tester");
        List<Long> seatIds = api.availableSeatIds(NORMAL_EVENT, 2);
        long bookingId = ((Number) api.hold(attendee, NORMAL_EVENT, seatIds, null).getBody().get("bookingId"))
                .longValue();

        String key = ApiClient.newIdempotencyKey();
        ResponseEntity<Map> first = api.pay(attendee, bookingId, key, false);
        ResponseEntity<Map> second = api.pay(attendee, bookingId, key, false);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Boolean) first.getBody().get("replayed")).isFalse();
        assertThat((Boolean) second.getBody().get("replayed")).isTrue();
        // Same payment row, same tickets: nothing was created a second time.
        assertThat(second.getBody().get("paymentId")).isEqualTo(first.getBody().get("paymentId"));
        assertThat(second.getBody().get("ticketIds")).isEqualTo(first.getBody().get("ticketIds"));
        assertThat((List<Object>) second.getBody().get("ticketIds")).hasSize(2);
    }

    @Test
    @DisplayName("a declined payment leaves the booking payable with a new key")
    void declinedPaymentCanBeRetried() {
        String attendee = api.registerAndLogin("declined@test.dev", "Declined Tester");
        List<Long> seatIds = api.availableSeatIds(NORMAL_EVENT, 1);
        long bookingId = ((Number) api.hold(attendee, NORMAL_EVENT, seatIds, null).getBody().get("bookingId"))
                .longValue();

        ResponseEntity<Map> declined = api.pay(attendee, bookingId, ApiClient.newIdempotencyKey(), true);
        assertThat(declined.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat((String) declined.getBody().get("status")).isEqualTo("FAILED");
        assertThat((String) declined.getBody().get("bookingStatus")).isEqualTo("PENDING_PAYMENT");

        ResponseEntity<Map> retried = api.pay(attendee, bookingId, ApiClient.newIdempotencyKey(), false);
        assertThat(retried.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) retried.getBody().get("bookingStatus")).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("releasing a hold puts the seats straight back on sale")
    void releasingAHoldFreesTheSeats() {
        String attendee = api.registerAndLogin("release@test.dev", "Release Tester");
        List<Long> seatIds = api.availableSeatIds(NORMAL_EVENT, 2);
        long bookingId = ((Number) api.hold(attendee, NORMAL_EVENT, seatIds, null).getBody().get("bookingId"))
                .longValue();

        assertThat(api.availableSeatIds(NORMAL_EVENT, 500)).doesNotContainAnyElementsOf(seatIds);

        rest.exchange(
                "/api/holds/" + bookingId,
                org.springframework.http.HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(null, api.authHeaders(attendee)),
                Void.class);

        assertThat(api.availableSeatIds(NORMAL_EVENT, 500)).containsAll(seatIds);
    }

    @SuppressWarnings("unchecked")
    private long bookOneSeatAndGetTicketId(String attendee) {
        List<Long> seatIds = api.availableSeatIds(NORMAL_EVENT, 1);
        long bookingId = ((Number) api.hold(attendee, NORMAL_EVENT, seatIds, null).getBody().get("bookingId"))
                .longValue();
        ResponseEntity<Map> pay = api.pay(attendee, bookingId, ApiClient.newIdempotencyKey(), false);
        return ((Number) ((List<Object>) pay.getBody().get("ticketIds")).get(0)).longValue();
    }

    /** The scheduler runs on a timer, so the test polls the status endpoint the way the UI does. */
    private String awaitAdmission(String attendee) throws InterruptedException {
        for (int attempt = 0; attempt < 40; attempt++) {
            Map body = api.queueStatus(attendee, HIGH_DEMAND_EVENT).getBody();
            if (Boolean.TRUE.equals(body.get("admitted"))) {
                return (String) body.get("admissionToken");
            }
            Thread.sleep(250);
        }
        throw new AssertionError("The admission scheduler never let this user out of the queue");
    }
}
