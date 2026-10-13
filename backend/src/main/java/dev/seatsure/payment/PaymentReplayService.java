package dev.seatsure.payment;

import dev.seatsure.booking.Booking;
import dev.seatsure.common.ConflictException;
import dev.seatsure.payment.PaymentDtos.PayResponse;
import dev.seatsure.ticket.Ticket;
import dev.seatsure.ticket.TicketRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read side of idempotency: given a key that has been seen before, rebuild exactly the response
 * the first call returned. Its own bean so the read transaction is really applied.
 */
@Service
public class PaymentReplayService {

    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;

    public PaymentReplayService(PaymentRepository paymentRepository, TicketRepository ticketRepository) {
        this.paymentRepository = paymentRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PayResponse> find(String idempotencyKey, Long bookingId, Long userId) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey).map(payment -> {
            Booking booking = payment.getBooking();
            if (!booking.getId().equals(bookingId) || !booking.getUser().getId().equals(userId)) {
                throw new ConflictException("That Idempotency-Key was already used for another booking");
            }
            List<Long> ticketIds = payment.getStatus() == PaymentStatus.SUCCESS
                    ? ticketRepository.findForUser(userId).stream()
                            .filter(ticket -> ticket.getBooking().getId().equals(booking.getId()))
                            .map(Ticket::getId)
                            .toList()
                    : List.of();
            return new PayResponse(
                    payment.getId(),
                    booking.getId(),
                    payment.getStatus(),
                    booking.getStatus(),
                    payment.getAmountPaise(),
                    payment.getGatewayRef(),
                    payment.getStatus() == PaymentStatus.SUCCESS
                            ? "Payment already confirmed"
                            : payment.getFailureReason(),
                    ticketIds,
                    true);
        });
    }
}
