package dev.seatsure.ticket;

import dev.seatsure.auth.AppUserPrincipal;
import dev.seatsure.ticket.TicketDtos.TicketResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('ATTENDEE')")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/api/me/tickets")
    public List<TicketResponse> myTickets(@AuthenticationPrincipal AppUserPrincipal principal) {
        return ticketService.listForUser(principal.getId());
    }

    @GetMapping(value = "/api/tickets/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(ticketService.qrPngForOwner(id, principal.getId()));
    }
}
