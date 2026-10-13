package dev.seatsure.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.seatsure.auth.User;
import dev.seatsure.booking.Booking;
import dev.seatsure.booking.BookingConfirmationService;
import dev.seatsure.booking.BookingStatus;
import dev.seatsure.event.Event;
import dev.seatsure.payment.PaymentDtos.PayResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    private static final long BOOKING_ID = 3L;
    private static final long USER_ID = 11L;
    private static final String KEY = "0f4b21a7-0d25-4c58-9d42-2a9a2f6d30ce";

    @Mock private PaymentTransactionService transactionService;
    @Mock private PaymentGateway gateway;
    @Mock private BookingConfirmationService confirmationService;
    @Mock private PaymentReplayService replayService;

    private PaymentService paymentService() {
        return new PaymentService(transactionService, gateway, confirmationService, replayService);
    }

    @Test
    @DisplayName("a key that has been seen before returns the stored result without charging again")
    void replaysStoredResultForKnownKey() {
        PayResponse stored = new PayResponse(
                9L, BOOKING_ID, PaymentStatus.SUCCESS, BookingStatus.CONFIRMED, 300000L, "mock_ref",
                "Payment already confirmed", List.of(1L, 2L), true);
        when(replayService.find(KEY, BOOKING_ID, USER_ID)).thenReturn(Optional.of(stored));

        PayResponse response = paymentService().pay(BOOKING_ID, USER_ID, KEY, false);

        assertThat(response).isEqualTo(stored);
        assertThat(response.replayed()).isTrue();
        verify(gateway, never()).charge(anyLong(), anyString(), anyBoolean());
        verify(transactionService, never()).confirmAndRecord(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("a fresh key charges the gateway once and confirms the booking")
    void chargesOnceForFreshKey() {
        when(replayService.find(KEY, BOOKING_ID, USER_ID)).thenReturn(Optional.empty());
        when(confirmationService.requireOwnPendingBooking(BOOKING_ID, USER_ID)).thenReturn(pendingBooking());
        when(confirmationService.seatIdsOf(BOOKING_ID)).thenReturn(List.of(5L, 6L));
        when(gateway.charge(anyLong(), anyString(), anyBoolean()))
                .thenReturn(new PaymentGateway.GatewayResult(true, "mock_ref", null));
        when(transactionService.confirmAndRecord(anyLong(), anyLong(), anyString(), any()))
                .thenReturn(new PayResponse(1L, BOOKING_ID, PaymentStatus.SUCCESS, BookingStatus.CONFIRMED,
                        300000L, "mock_ref", "Payment confirmed", List.of(1L, 2L), false));

        PayResponse response = paymentService().pay(BOOKING_ID, USER_ID, KEY, false);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.replayed()).isFalse();
        verify(gateway).charge(300000L, KEY, false);
        // Holds are only released once the seats are safely ours in Postgres.
        verify(confirmationService).releaseHoldsAfterConfirmation(1L, USER_ID, List.of(5L, 6L));
    }

    @Test
    @DisplayName("a declined payment is recorded but the seats stay held so the user can retry")
    void recordsFailureWithoutConfirming() {
        when(replayService.find(KEY, BOOKING_ID, USER_ID)).thenReturn(Optional.empty());
        when(confirmationService.requireOwnPendingBooking(BOOKING_ID, USER_ID)).thenReturn(pendingBooking());
        when(gateway.charge(anyLong(), anyString(), anyBoolean()))
                .thenReturn(new PaymentGateway.GatewayResult(false, null, "Card declined by issuer"));
        when(transactionService.recordFailure(anyLong(), anyLong(), anyString(), any()))
                .thenReturn(new PayResponse(1L, BOOKING_ID, PaymentStatus.FAILED, BookingStatus.PENDING_PAYMENT,
                        300000L, null, "Card declined by issuer", List.of(), false));

        PayResponse response = paymentService().pay(BOOKING_ID, USER_ID, KEY, true);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        verify(transactionService, never()).confirmAndRecord(anyLong(), anyLong(), anyString(), any());
        verify(confirmationService, never()).releaseHoldsAfterConfirmation(anyLong(), anyLong(), anyList());
    }

    @Test
    @DisplayName("a missing or oversized Idempotency-Key is a 400, not a charge")
    void rejectsBadIdempotencyKey() {
        PaymentService service = paymentService();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.pay(BOOKING_ID, USER_ID, null, false))
                .isInstanceOf(dev.seatsure.common.BadRequestException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.pay(BOOKING_ID, USER_ID, "  ", false))
                .isInstanceOf(dev.seatsure.common.BadRequestException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.pay(BOOKING_ID, USER_ID, "x".repeat(65), false))
                .isInstanceOf(dev.seatsure.common.BadRequestException.class);
        verify(gateway, never()).charge(anyLong(), anyString(), anyBoolean());
    }

    private static Booking pendingBooking() {
        Event event = new Event();
        event.setId(1L);
        User user = new User();
        user.setId(USER_ID);
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setEvent(event);
        booking.setUser(user);
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setTotalPaise(300000L);
        return booking;
    }
}
