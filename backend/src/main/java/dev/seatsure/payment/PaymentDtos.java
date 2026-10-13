package dev.seatsure.payment;

import dev.seatsure.booking.BookingStatus;
import java.util.List;

public final class PaymentDtos {

    private PaymentDtos() {}

    /** The demo toggle on the checkout page, so a failed payment can be shown on purpose. */
    public record PayRequest(boolean simulateFailure) {}

    public record PayResponse(
            Long paymentId,
            Long bookingId,
            PaymentStatus status,
            BookingStatus bookingStatus,
            long amountPaise,
            String gatewayRef,
            String message,
            List<Long> ticketIds,
            boolean replayed) {}
}
