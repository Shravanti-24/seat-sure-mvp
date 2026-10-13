package dev.seatsure.event;

import dev.seatsure.event.EventDtos.EventDetail;
import dev.seatsure.event.EventDtos.EventSummary;
import dev.seatsure.event.EventDtos.SeatMapResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final SeatMapService seatMapService;

    public EventController(EventService eventService, SeatMapService seatMapService) {
        this.eventService = eventService;
        this.seatMapService = seatMapService;
    }

    @GetMapping
    public List<EventSummary> list() {
        return eventService.listPublished();
    }

    @GetMapping("/{id}")
    public EventDetail get(@PathVariable Long id) {
        return eventService.get(id);
    }

    @GetMapping("/{id}/seatmap")
    public SeatMapResponse seatMap(@PathVariable Long id) {
        return seatMapService.seatMap(id);
    }
}
