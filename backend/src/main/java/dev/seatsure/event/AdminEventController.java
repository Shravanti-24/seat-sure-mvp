package dev.seatsure.event;

import dev.seatsure.event.EventDtos.CreateEventRequest;
import dev.seatsure.event.EventDtos.EventDetail;
import dev.seatsure.event.EventDtos.EventSummary;
import dev.seatsure.event.EventDtos.UpdateEventRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/events")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEventController {

    private final EventService eventService;

    public AdminEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<EventSummary> list() {
        return eventService.listAll();
    }

    @GetMapping("/{id}")
    public EventDetail get(@PathVariable Long id) {
        return eventService.get(id);
    }

    @PostMapping
    public ResponseEntity<EventDetail> create(@Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.create(request));
    }

    @PutMapping("/{id}")
    public EventDetail update(@PathVariable Long id, @Valid @RequestBody UpdateEventRequest request) {
        return eventService.update(id, request);
    }
}
