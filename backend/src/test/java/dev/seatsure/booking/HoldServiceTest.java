package dev.seatsure.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.seatsure.common.ConflictException;
import dev.seatsure.common.ForbiddenException;
import dev.seatsure.common.SeatSureProperties;
import dev.seatsure.event.Event;
import dev.seatsure.event.EventSeat;
import dev.seatsure.event.EventSeatRepository;
import dev.seatsure.event.EventSeatStatus;
import dev.seatsure.event.EventService;
import dev.seatsure.event.EventStatus;
import dev.seatsure.queue.AdmissionService;
import dev.seatsure.venue.Seat;
import dev.seatsure.venue.VenueSection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HoldServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-01T12:00:00Z");
    private static final long USER_ID = 11L;
    private static final long EVENT_ID = 1L;

    @Mock private EventService eventService;
    @Mock private EventSeatRepository eventSeatRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingSeatRepository bookingSeatRepository;
    @Mock private PendingBookingService pendingBookingService;
    @Mock private HoldRegistry holdRegistry;
    @Mock private AdmissionService admissionService;
    @Mock private BookingQueryService bookingQueryService;

    private HoldService holdService(Event event) {
        when(eventService.require(EVENT_ID)).thenReturn(event);
        return new HoldService(
                eventService,
                eventSeatRepository,
                bookingRepository,
                bookingSeatRepository,
                pendingBookingService,
                holdRegistry,
                admissionService,
                bookingQueryService,
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("anti-hoarding: the request is refused when it would push the user past the event limit")
    void refusesWhenOverSeatLimit() {
        Event event = event(false, 6);
        HoldService service = holdService(event);
        when(eventSeatRepository.findByEventIdAndSeatIds(anyLong(), anyList()))
                .thenReturn(List.of(eventSeat(1L, 101L), eventSeat(2L, 102L)));
        // The user already holds or owns five seats for this event.
        when(bookingSeatRepository.countSeatsForUser(anyLong(), anyLong(), any())).thenReturn(5L);

        assertThatThrownBy(() -> service.hold(EVENT_ID, USER_ID, List.of(1L, 2L), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("at most 6 seats")
                .hasMessageContaining("already have 5");

        // The limit is checked before Redis is touched, so no seat is ever locked for a doomed request.
        verify(holdRegistry, never()).acquire(anyLong(), anyLong(), anyList());
    }

    @Test
    @DisplayName("anti-hoarding: a request that exactly reaches the limit is allowed")
    void allowsUpToSeatLimit() {
        Event event = event(false, 6);
        HoldService service = holdService(event);
        when(eventSeatRepository.findByEventIdAndSeatIds(anyLong(), anyList()))
                .thenReturn(List.of(eventSeat(1L, 101L)));
        when(bookingSeatRepository.countSeatsForUser(anyLong(), anyLong(), any())).thenReturn(5L);
        when(holdRegistry.acquire(EVENT_ID, USER_ID, List.of(1L))).thenReturn(List.of());
        when(pendingBookingService.create(any(), anyLong(), anyList())).thenReturn(77L);

        service.hold(EVENT_ID, USER_ID, List.of(1L), null);

        verify(holdRegistry).acquire(EVENT_ID, USER_ID, List.of(1L));
        verify(bookingQueryService).get(77L, USER_ID);
    }

    @Test
    @DisplayName("all-or-nothing: when Redis reports a taken seat no booking is created")
    void doesNotCreateBookingWhenAnySeatIsTaken() {
        Event event = event(false, 6);
        HoldService service = holdService(event);
        when(eventSeatRepository.findByEventIdAndSeatIds(anyLong(), anyList()))
                .thenReturn(List.of(eventSeat(1L, 101L), eventSeat(2L, 102L), eventSeat(3L, 103L)));
        when(bookingSeatRepository.countSeatsForUser(anyLong(), anyLong(), any())).thenReturn(0L);
        when(holdRegistry.acquire(EVENT_ID, USER_ID, List.of(1L, 2L, 3L))).thenReturn(List.of(2L));

        assertThatThrownBy(() -> service.hold(EVENT_ID, USER_ID, List.of(1L, 2L, 3L), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Someone just grabbed")
                .extracting(exception -> ((ConflictException) exception).getDetails())
                .isEqualTo(List.of(2L));

        verify(pendingBookingService, never()).create(any(), anyLong(), anyList());
    }

    @Test
    @DisplayName("all-or-nothing: if the booking write fails the holds are given straight back")
    void releasesHoldsWhenBookingWriteFails() {
        Event event = event(false, 6);
        HoldService service = holdService(event);
        when(eventSeatRepository.findByEventIdAndSeatIds(anyLong(), anyList()))
                .thenReturn(List.of(eventSeat(1L, 101L)));
        when(bookingSeatRepository.countSeatsForUser(anyLong(), anyLong(), any())).thenReturn(0L);
        when(holdRegistry.acquire(anyLong(), anyLong(), anyList())).thenReturn(List.of());
        when(pendingBookingService.create(any(), anyLong(), anyList()))
                .thenThrow(new IllegalStateException("database is on fire"));

        assertThatThrownBy(() -> service.hold(EVENT_ID, USER_ID, List.of(1L), null))
                .isInstanceOf(IllegalStateException.class);

        verify(holdRegistry).release(EVENT_ID, USER_ID, List.of(1L));
    }

    @Test
    @DisplayName("a seat that is already BOOKED is reported before Redis is asked")
    void refusesBookedSeats() {
        Event event = event(false, 6);
        HoldService service = holdService(event);
        EventSeat booked = eventSeat(2L, 102L);
        booked.setStatus(EventSeatStatus.BOOKED);
        when(eventSeatRepository.findByEventIdAndSeatIds(anyLong(), anyList()))
                .thenReturn(List.of(eventSeat(1L, 101L), booked));

        assertThatThrownBy(() -> service.hold(EVENT_ID, USER_ID, List.of(1L, 2L), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already booked");
        verify(holdRegistry, never()).acquire(anyLong(), anyLong(), anyList());
    }

    @Test
    @DisplayName("a high-demand event needs a valid admission token")
    void requiresAdmissionForHighDemandEvent() {
        Event event = event(true, 6);
        HoldService service = holdService(event);
        org.mockito.Mockito.doThrow(new ForbiddenException("Join the queue"))
                .when(admissionService)
                .requireAdmission(anyLong(), anyLong(), any());

        assertThatThrownBy(() -> service.hold(EVENT_ID, USER_ID, List.of(1L), null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Join the queue");
    }

    @Test
    @DisplayName("holding is refused before the sale opens")
    void refusesBeforeSaleOpens() {
        Event event = event(false, 6);
        event.setSaleOpensAt(NOW.plusSeconds(600));
        HoldService service = holdService(event);

        assertThatThrownBy(() -> service.hold(EVENT_ID, USER_ID, List.of(1L), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not on sale");
    }

    @Test
    @DisplayName("duplicate seat ids in one request are collapsed, not counted twice")
    void deduplicatesRequestedSeats() {
        Event event = event(false, 6);
        HoldService service = holdService(event);
        when(eventSeatRepository.findByEventIdAndSeatIds(anyLong(), anyList()))
                .thenReturn(List.of(eventSeat(1L, 101L)));
        when(bookingSeatRepository.countSeatsForUser(anyLong(), anyLong(), any())).thenReturn(0L);
        when(holdRegistry.acquire(EVENT_ID, USER_ID, List.of(1L))).thenReturn(List.of());
        when(pendingBookingService.create(any(), anyLong(), anyList())).thenReturn(5L);

        service.hold(EVENT_ID, USER_ID, List.of(1L, 1L, 1L), null);

        verify(holdRegistry).acquire(EVENT_ID, USER_ID, List.of(1L));
    }

    private static Event event(boolean highDemand, int maxSeatsPerUser) {
        Event event = new Event();
        event.setId(EVENT_ID);
        event.setHighDemand(highDemand);
        event.setMaxSeatsPerUser(maxSeatsPerUser);
        event.setStatus(EventStatus.ON_SALE);
        event.setSaleOpensAt(NOW.minusSeconds(60));
        return event;
    }

    private static EventSeat eventSeat(Long seatId, Long eventSeatId) {
        VenueSection section = new VenueSection();
        section.setId(9L);
        section.setName("Gold");
        Seat seat = new Seat();
        seat.setId(seatId);
        seat.setSection(section);
        seat.setRowLabel("A");
        seat.setSeatNumber(seatId.intValue());
        EventSeat eventSeat = new EventSeat();
        eventSeat.setId(eventSeatId);
        eventSeat.setSeat(seat);
        eventSeat.setStatus(EventSeatStatus.AVAILABLE);
        return eventSeat;
    }

    private static SeatSureProperties properties() {
        return new SeatSureProperties(
                new SeatSureProperties.Jwt("jwt-secret-that-is-long-enough-for-hs256", 720),
                new SeatSureProperties.Hold(420, 6, 30, 30000),
                new SeatSureProperties.Ticket("hmac"),
                new SeatSureProperties.Queue(25, 5000, 600, 60),
                new SeatSureProperties.RateLimit(false, 60, Map.of()),
                new SeatSureProperties.Seed("a", "b", "c", 2));
    }
}
