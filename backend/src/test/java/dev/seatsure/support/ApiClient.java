package dev.seatsure.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Thin helper so the tests read like the flow they describe rather than like HTTP plumbing. */
public class ApiClient {

    private final TestRestTemplate rest;

    public ApiClient(TestRestTemplate rest) {
        this.rest = rest;
    }

    public String registerAndLogin(String email, String name) {
        rest.postForEntity(
                "/api/auth/register",
                json(Map.of("name", name, "email", email, "password", "password123")),
                Map.class);
        return login(email, "password123");
    }

    @SuppressWarnings("unchecked")
    public String login(String email, String password) {
        ResponseEntity<Map> response = rest.postForEntity(
                "/api/auth/login", json(Map.of("email", email, "password", password)), Map.class);
        return (String) response.getBody().get("token");
    }

    @SuppressWarnings("unchecked")
    public List<Long> availableSeatIds(long eventId, int count) {
        ResponseEntity<Map> response = rest.getForEntity("/api/events/" + eventId + "/seatmap", Map.class);
        List<Map<String, Object>> sections = (List<Map<String, Object>>) response.getBody().get("sections");
        return sections.stream()
                .flatMap(section -> ((List<Map<String, Object>>) section.get("seats")).stream())
                .filter(seat -> "AVAILABLE".equals(seat.get("status")))
                .limit(count)
                .map(seat -> ((Number) seat.get("seatId")).longValue())
                .toList();
    }

    public ResponseEntity<Map> hold(String token, long eventId, List<Long> seatIds, String admissionToken) {
        HttpHeaders headers = authHeaders(token);
        if (admissionToken != null) {
            headers.set("X-Admission-Token", admissionToken);
        }
        return rest.exchange(
                "/api/events/" + eventId + "/holds",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("seatIds", seatIds), headers),
                Map.class);
    }

    public ResponseEntity<Map> pay(String token, long bookingId, String idempotencyKey, boolean simulateFailure) {
        HttpHeaders headers = authHeaders(token);
        headers.set("Idempotency-Key", idempotencyKey);
        return rest.exchange(
                "/api/bookings/" + bookingId + "/pay",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("simulateFailure", simulateFailure), headers),
                Map.class);
    }

    public ResponseEntity<Map> joinQueue(String token, long eventId) {
        return rest.exchange(
                "/api/events/" + eventId + "/queue/join",
                HttpMethod.POST,
                new HttpEntity<>(null, authHeaders(token)),
                Map.class);
    }

    public ResponseEntity<Map> queueStatus(String token, long eventId) {
        return rest.exchange(
                "/api/events/" + eventId + "/queue/status",
                HttpMethod.GET,
                new HttpEntity<>(null, authHeaders(token)),
                Map.class);
    }

    public ResponseEntity<List> myTickets(String token) {
        return rest.exchange(
                "/api/me/tickets", HttpMethod.GET, new HttpEntity<>(null, authHeaders(token)), List.class);
    }

    public ResponseEntity<Map> checkIn(String staffToken, String qrContent) {
        return rest.exchange(
                "/api/checkin",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("qrContent", qrContent), authHeaders(staffToken)),
                Map.class);
    }

    public static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    public HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpEntity<Object> json(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
