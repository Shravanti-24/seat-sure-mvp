package dev.seatsure.event;

import dev.seatsure.event.EventDtos.SeatMapResponse;
import dev.seatsure.event.EventDtos.SeatMapSeat;
import dev.seatsure.event.EventDtos.SeatMapSection;
import dev.seatsure.event.EventDtos.SeatMapStatus;
import dev.seatsure.venue.VenueSection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the public seat map by merging the two sources of truth: Postgres says AVAILABLE or BOOKED,
 * Redis says which of the available seats somebody is paying for right now.
 */
@Service
public class SeatMapService {

    private final EventService eventService;
    private final EventSeatRepository eventSeatRepository;
    private final EventSectionPriceRepository priceRepository;
    private final HeldSeatLookup heldSeatLookup;

    public SeatMapService(
            EventService eventService,
            EventSeatRepository eventSeatRepository,
            EventSectionPriceRepository priceRepository,
            HeldSeatLookup heldSeatLookup) {
        this.eventService = eventService;
        this.eventSeatRepository = eventSeatRepository;
        this.priceRepository = priceRepository;
        this.heldSeatLookup = heldSeatLookup;
    }

    @Transactional(readOnly = true)
    public SeatMapResponse seatMap(Long eventId) {
        Event event = eventService.require(eventId);
        List<EventSeat> eventSeats = eventSeatRepository.findSeatMap(eventId);

        List<Long> availableSeatIds = eventSeats.stream()
                .filter(eventSeat -> eventSeat.getStatus() == EventSeatStatus.AVAILABLE)
                .map(eventSeat -> eventSeat.getSeat().getId())
                .toList();
        Set<Long> heldSeatIds = heldSeatLookup.heldSeatIds(eventId, availableSeatIds);

        Map<Long, Long> pricesBySection = new LinkedHashMap<>();
        priceRepository.findByKeyEventId(eventId)
                .forEach(price -> pricesBySection.put(price.getKey().getSectionId(), price.getPricePaise()));

        Map<Long, List<SeatMapSeat>> seatsBySection = new LinkedHashMap<>();
        Map<Long, VenueSection> sections = new LinkedHashMap<>();
        for (EventSeat eventSeat : eventSeats) {
            VenueSection section = eventSeat.getSeat().getSection();
            sections.putIfAbsent(section.getId(), section);
            SeatMapStatus status;
            if (eventSeat.getStatus() == EventSeatStatus.BOOKED) {
                status = SeatMapStatus.BOOKED;
            } else if (heldSeatIds.contains(eventSeat.getSeat().getId())) {
                status = SeatMapStatus.HELD;
            } else {
                status = SeatMapStatus.AVAILABLE;
            }
            seatsBySection
                    .computeIfAbsent(section.getId(), key -> new ArrayList<>())
                    .add(new SeatMapSeat(
                            eventSeat.getSeat().getId(),
                            eventSeat.getId(),
                            eventSeat.getSeat().getRowLabel(),
                            eventSeat.getSeat().getSeatNumber(),
                            eventSeat.getSeat().label(),
                            status));
        }

        List<SeatMapSection> sectionResponses = sections.values().stream()
                .map(section -> new SeatMapSection(
                        section.getId(),
                        section.getName(),
                        pricesBySection.getOrDefault(section.getId(), 0L),
                        section.getRowCount(),
                        section.getSeatsPerRow(),
                        seatsBySection.getOrDefault(section.getId(), List.of())))
                .toList();

        return new SeatMapResponse(
                event.getId(),
                event.getStatus(),
                event.isHighDemand(),
                event.getMaxSeatsPerUser(),
                event.getSaleOpensAt(),
                sectionResponses);
    }
}
