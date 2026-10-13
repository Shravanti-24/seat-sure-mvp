package dev.seatsure.payment;

import dev.seatsure.auth.AppUserPrincipal;
import dev.seatsure.payment.PaymentDtos.PayRequest;
import dev.seatsure.payment.PaymentDtos.PayResponse;
import dev.seatsure.ratelimit.RateLimited;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('ATTENDEE')")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * The {@code Idempotency-Key} header is required. The frontend generates one UUID per checkout
     * attempt and reuses it for every retry of that attempt.
     */
    @PostMapping("/api/bookings/{bookingId}/pay")
    @RateLimited(group = "payments")
    public ResponseEntity<PayResponse> pay(
            @PathVariable Long bookingId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) PayRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        boolean simulateFailure = request != null && request.simulateFailure();
        PayResponse response = paymentService.pay(bookingId, principal.getId(), idempotencyKey, simulateFailure);
        HttpStatus status = response.status() == PaymentStatus.SUCCESS
                ? HttpStatus.OK
                : HttpStatus.PAYMENT_REQUIRED;
        return ResponseEntity.status(status).body(response);
    }
}
