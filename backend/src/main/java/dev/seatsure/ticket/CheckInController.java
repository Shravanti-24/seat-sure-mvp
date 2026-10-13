package dev.seatsure.ticket;

import dev.seatsure.auth.AppUserPrincipal;
import dev.seatsure.ticket.TicketDtos.CheckInRequest;
import dev.seatsure.ticket.TicketDtos.CheckInResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckInController {

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @PostMapping("/api/checkin")
    @PreAuthorize("hasRole('STAFF')")
    public CheckInResponse checkIn(
            @Valid @RequestBody CheckInRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        return checkInService.checkIn(request.qrContent(), principal.getId());
    }
}
