package dev.seatsure.payment;

import dev.seatsure.booking.Booking;
import dev.seatsure.booking.BookingConfirmationService;
import dev.seatsure.booking.BookingStatus;
import dev.seatsure.common.ConflictException;
import dev.seatsure.event.EventSeat;
import dev.seatsure.payment.PaymentDtos.PayResponse;
import dev.seatsure.payment.PaymentGateway.GatewayResult;
import dev.seatsure.ticket.Ticket;
import dev.seatsure.ticket.TicketService;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one transaction that matters: record the payment, flip the seats under optimistic locking,
 * confirm the booking and mint the tickets. Anything that goes wrong rolls all four back together.
 */
@Service
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final BookingConfirmationService confirmationService;
    private final TicketService ticketService;
    private final Clock clock;

    public PaymentTransactionService(
            PaymentRepository paymentRepository,
            BookingConfirmationService confirmationService,
            TicketService ticketService,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.confirmationService = confirmationService;
        this.ticketService = ticketService;
        this.clock = clock;
    }

    @Transactional
    public PayResponse recordFailure(Long bookingId, Long userId, String idempotencyKey, GatewayResult result) {
        Booking booking = confirmationService.requireOwnPendingBooking(bookingId, userId);
        Payment payment = save(booking, idempotencyKey, PaymentStatus.FAILED, result);
        return new PayResponse(
                payment.getId(),
                booking.getId(),
                PaymentStatus.FAILED,
                booking.getStatus(),
                booking.getTotalPaise(),
                null,
                result.failureReason(),
                List.of(),
                false);
    }

    @Transactional
    public PayResponse confirmAndRecord(Long bookingId, Long userId, String idempotencyKey, GatewayResult result) {
        Booking booking = confirmationService.requireOwnPendingBooking(bookingId, userId);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("That booking is already " + booking.getStatus());
        }

        Payment payment = save(booking, idempotencyKey, PaymentStatus.SUCCESS, result);
        List<EventSeat> seats = confirmationService.confirm(booking);
        List<Ticket> tickets = ticketService.issue(booking, seats);

        return new PayResponse(
                payment.getId(),
                booking.getId(),
                PaymentStatus.SUCCESS,
                booking.getStatus(),
                booking.getTotalPaise(),
                payment.getGatewayRef(),
                "Payment confirmed",
                tickets.stream().map(Ticket::getId).toList(),
                false);
    }

    private Payment save(Booking booking, String idempotencyKey, PaymentStatus status, GatewayResult result) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setAmountPaise(booking.getTotalPaise());
        payment.setStatus(status);
        payment.setGatewayRef(result.reference());
        payment.setFailureReason(result.failureReason());
        payment.setCreatedAt(clock.instant());
        return paymentRepository.saveAndFlush(payment);
    }
}
