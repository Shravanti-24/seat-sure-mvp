package dev.seatsure.queue;

import dev.seatsure.auth.AppUserPrincipal;
import dev.seatsure.queue.QueueDtos.QueueJoinResponse;
import dev.seatsure.queue.QueueDtos.QueueStatusResponse;
import dev.seatsure.ratelimit.RateLimited;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/queue")
@PreAuthorize("hasRole('ATTENDEE')")
public class QueueController {

    private final WaitingRoomService waitingRoomService;

    public QueueController(WaitingRoomService waitingRoomService) {
        this.waitingRoomService = waitingRoomService;
    }

    @PostMapping("/join")
    @RateLimited(group = "queue-status")
    public QueueJoinResponse join(@PathVariable Long eventId, @AuthenticationPrincipal AppUserPrincipal principal) {
        return waitingRoomService.join(eventId, principal.getId());
    }

    @GetMapping("/status")
    @RateLimited(group = "queue-status")
    public QueueStatusResponse status(@PathVariable Long eventId, @AuthenticationPrincipal AppUserPrincipal principal) {
        return waitingRoomService.status(eventId, principal.getId());
    }
}
