package dev.seatsure.payment;

import dev.seatsure.booking.Booking;
import dev.seatsure.booking.BookingConfirmationService;
import dev.seatsure.booking.BookingStatus;
import dev.seatsure.common.BadRequestException;
import dev.seatsure.common.ConflictException;
import dev.seatsure.payment.PaymentDtos.PayResponse;
import dev.seatsure.payment.PaymentGateway.GatewayResult;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Orchestrates paying for a held booking.
 *
 * <p>Deliberately not transactional itself: the idempotency lookup, the gateway call and the
 * release of the Redis holds all happen outside the database transaction, which is kept as short as
 * possible and only covers the writes that must be atomic.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentTransactionService transactionService;
    private final PaymentGateway gateway;
    private final BookingConfirmationService confirmationService;
    private final PaymentReplayService replayService;

    public PaymentService(
            PaymentTransactionService transactionService,
            PaymentGateway gateway,
            BookingConfirmationService confirmationService,
            PaymentReplayService replayService) {
        this.transactionService = transactionService;
        this.gateway = gateway;
        this.confirmationService = confirmationService;
        this.replayService = replayService;
    }

    public PayResponse pay(Long bookingId, Long userId, String idempotencyKey, boolean simulateFailure) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new BadRequestException("Idempotency-Key header must be a non-empty string of at most 64 characters");
        }

        Optional<PayResponse> replay = replayService.find(idempotencyKey, bookingId, userId);
        if (replay.isPresent()) {
            return replay.get();
        }

        Booking booking = confirmationService.requireOwnPendingBooking(bookingId, userId);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("That booking is already " + booking.getStatus());
        }

        GatewayResult result = gateway.charge(booking.getTotalPaise(), idempotencyKey, simulateFailure);

        try {
            if (!result.success()) {
                return transactionService.recordFailure(bookingId, userId, idempotencyKey, result);
            }
            List<Long> seatIds = confirmationService.seatIdsOf(bookingId);
            PayResponse response = transactionService.confirmAndRecord(bookingId, userId, idempotencyKey, result);
            // The seats are now ours in Postgres, so the Redis holds have done their job.
            confirmationService.releaseHoldsAfterConfirmation(booking.getEvent().getId(), userId, seatIds);
            return response;
        } catch (DataIntegrityViolationException ex) {
            // Two requests raced with the same key: the unique index let exactly one through.
            log.info("Idempotency key {} raced with itself, replaying the stored result", idempotencyKey);
            return replayService.find(idempotencyKey, bookingId, userId).orElseThrow(() -> ex);
        }
    }
}
