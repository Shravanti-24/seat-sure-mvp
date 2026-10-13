package dev.seatsure.booking;

import dev.seatsure.auth.AppUserPrincipal;
import dev.seatsure.booking.BookingDtos.BookingResponse;
import dev.seatsure.booking.BookingDtos.CreateHoldRequest;
import dev.seatsure.ratelimit.RateLimited;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('ATTENDEE')")
public class BookingController {

    private final HoldService holdService;
    private final BookingQueryService bookingQueryService;

    public BookingController(HoldService holdService, BookingQueryService bookingQueryService) {
        this.holdService = holdService;
        this.bookingQueryService = bookingQueryService;
    }

    @PostMapping("/api/events/{eventId}/holds")
    @RateLimited(group = "holds")
    public ResponseEntity<BookingResponse> hold(
            @PathVariable Long eventId,
            @Valid @RequestBody CreateHoldRequest request,
            @RequestHeader(value = "X-Admission-Token", required = false) String admissionToken,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        BookingResponse response = holdService.hold(eventId, principal.getId(), request.seatIds(), admissionToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/api/holds/{bookingId}")
    public ResponseEntity<Void> release(
            @PathVariable Long bookingId, @AuthenticationPrincipal AppUserPrincipal principal) {
        holdService.release(bookingId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/bookings/{bookingId}")
    public BookingResponse get(@PathVariable Long bookingId, @AuthenticationPrincipal AppUserPrincipal principal) {
        return bookingQueryService.get(bookingId, principal.getId());
    }

    @GetMapping("/api/me/bookings")
    public List<BookingResponse> myBookings(@AuthenticationPrincipal AppUserPrincipal principal) {
        return bookingQueryService.listForUser(principal.getId());
    }
}
