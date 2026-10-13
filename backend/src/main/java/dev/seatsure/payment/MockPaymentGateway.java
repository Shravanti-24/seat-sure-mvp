package dev.seatsure.payment;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Always succeeds unless the caller explicitly asks for a failure, which keeps demos and load tests
 * deterministic. The idempotency key is passed through the way a real gateway would want it.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public GatewayResult charge(long amountPaise, String idempotencyKey, boolean simulateFailure) {
        if (simulateFailure) {
            return new GatewayResult(false, null, "Card declined by issuer");
        }
        return new GatewayResult(true, "mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18), null);
    }
}
