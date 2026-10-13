package dev.seatsure.payment;

/** Seam for a real PSP. The MVP ships the mock implementation. */
public interface PaymentGateway {

    GatewayResult charge(long amountPaise, String idempotencyKey, boolean simulateFailure);

    record GatewayResult(boolean success, String reference, String failureReason) {}
}
