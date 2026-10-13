package dev.seatsure.booking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.seatsure.support.AbstractIntegrationTest;
import dev.seatsure.support.ApiClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Two people want the same seat. Exactly one may have it. */
class SeatRaceIntegrationTest extends AbstractIntegrationTest {

    private static final long EVENT_ID = 3L;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(rest);
    }

    @Test
    @DisplayName("two users hold the same seat: one wins, the other gets a 409 naming the seat")
    @SuppressWarnings("unchecked")
    void onlyOneUserCanHoldASeat() {
        String first = api.registerAndLogin("race-a@test.dev", "Race A");
        String second = api.registerAndLogin("race-b@test.dev", "Race B");
        List<Long> seatIds = api.availableSeatIds(EVENT_ID, 1);

        ResponseEntity<Map> winner = api.hold(first, EVENT_ID, seatIds, null);
        ResponseEntity<Map> loser = api.hold(second, EVENT_ID, seatIds, null);

        assertThat(winner.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(loser.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) loser.getBody().get("message")).contains("Someone just grabbed");
        assertThat((List<Object>) loser.getBody().get("details"))
                .extracting(value -> ((Number) value).longValue())
                .containsExactly(seatIds.get(0));
    }

    @Test
    @DisplayName("a basket is all or nothing: one taken seat means none of them are held")
    @SuppressWarnings("unchecked")
    void aPartiallyTakenBasketHoldsNothing() {
        String first = api.registerAndLogin("basket-a@test.dev", "Basket A");
        String second = api.registerAndLogin("basket-b@test.dev", "Basket B");
        List<Long> available = api.availableSeatIds(EVENT_ID, 4);
        List<Long> taken = List.of(available.get(0));
        List<Long> overlapping = List.of(available.get(0), available.get(1), available.get(2));

        api.hold(first, EVENT_ID, taken, null);
        ResponseEntity<Map> response = api.hold(second, EVENT_ID, overlapping, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // The two seats the second user could have had are still free for everybody else.
        List<Long> stillAvailable = api.availableSeatIds(EVENT_ID, 500);
        assertThat(stillAvailable).contains(available.get(1), available.get(2));
        assertThat(stillAvailable).doesNotContain(available.get(0));
    }

    @Test
    @DisplayName("a user cannot exceed the per-event seat limit by holding in several requests")
    void seatLimitHoldsAcrossRequests() {
        String attendee = api.registerAndLogin("hoarder@test.dev", "Hoarder");
        List<Long> seatIds = api.availableSeatIds(EVENT_ID, 8);

        assertThat(api.hold(attendee, EVENT_ID, seatIds.subList(0, 4), null).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(api.hold(attendee, EVENT_ID, seatIds.subList(4, 6), null).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> overLimit = api.hold(attendee, EVENT_ID, seatIds.subList(6, 7), null);
        assertThat(overLimit.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) overLimit.getBody().get("message")).contains("at most 6 seats");
    }
}
