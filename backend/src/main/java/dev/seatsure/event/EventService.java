package dev.seatsure.event;

import dev.seatsure.common.BadRequestException;
import dev.seatsure.common.NotFoundException;
import dev.seatsure.event.EventDtos.CreateEventRequest;
import dev.seatsure.event.EventDtos.EventDetail;
import dev.seatsure.event.EventDtos.EventSummary;
import dev.seatsure.event.EventDtos.SectionPrice;
import dev.seatsure.event.EventDtos.SectionPriceRequest;
import dev.seatsure.event.EventDtos.UpdateEventRequest;
import dev.seatsure.venue.Seat;
import dev.seatsure.venue.SeatRepository;
import dev.seatsure.venue.Venue;
import dev.seatsure.venue.VenueRepository;
import dev.seatsure.venue.VenueSection;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventSeatRepository eventSeatRepository;
    private final EventSectionPriceRepository priceRepository;
    private final VenueRepository venueRepository;
    private final SeatRepository seatRepository;
    private final Clock clock;

    public EventService(
            EventRepository eventRepository,
            EventSeatRepository eventSeatRepository,
            EventSectionPriceRepository priceRepository,
            VenueRepository venueRepository,
            SeatRepository seatRepository,
            Clock clock) {
        this.eventRepository = eventRepository;
        this.eventSeatRepository = eventSeatRepository;
        this.priceRepository = priceRepository;
        this.venueRepository = venueRepository;
        this.seatRepository = seatRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EventSummary> listPublished() {
        return eventRepository.findPublished().stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<EventSummary> listAll() {
        return eventRepository.findAllWithVenue().stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public EventDetail get(Long id) {
        Event event = require(id);
        return new EventDetail(toSummary(event), event.getDescription(), prices(event));
    }

    @Transactional(readOnly = true)
    public Event require(Long id) {
        return eventRepository.findByIdWithVenue(id)
                .orElseThrow(() -> new NotFoundException("No event with id " + id));
    }

    /** Creating an event materialises one event_seat row per venue seat. */
    @Transactional
    public EventDetail create(CreateEventRequest request) {
        Venue venue = venueRepository.findById(request.venueId())
                .orElseThrow(() -> new NotFoundException("No venue with id " + request.venueId()));

        Map<Long, VenueSection> sectionsById = venue.getSections().stream()
                .collect(java.util.stream.Collectors.toMap(VenueSection::getId, Function.identity()));
        for (SectionPriceRequest price : request.prices()) {
            if (!sectionsById.containsKey(price.sectionId())) {
                throw new BadRequestException("Section " + price.sectionId() + " does not belong to this venue");
            }
        }
        if (request.prices().size() != sectionsById.size()) {
            throw new BadRequestException("Every section of the venue needs a price");
        }

        Event event = new Event();
        event.setVenue(venue);
        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setStartsAt(request.startsAt());
        event.setSaleOpensAt(request.saleOpensAt());
        event.setHighDemand(request.highDemand());
        event.setMaxSeatsPerUser(request.maxSeatsPerUser() == null ? 6 : request.maxSeatsPerUser());
        event.setStatus(request.status());
        event.setCreatedAt(clock.instant());
        eventRepository.save(event);

        for (SectionPriceRequest price : request.prices()) {
            EventSectionPrice entity = new EventSectionPrice();
            entity.setKey(new EventSectionPrice.Key(event.getId(), price.sectionId()));
            entity.setPricePaise(price.pricePaise());
            priceRepository.save(entity);
        }

        List<EventSeat> eventSeats = new ArrayList<>();
        for (VenueSection section : venue.getSections()) {
            for (Seat seat : seatRepository.findBySectionIdOrderByRowLabelAscSeatNumberAsc(section.getId())) {
                EventSeat eventSeat = new EventSeat();
                eventSeat.setEvent(event);
                eventSeat.setSeat(seat);
                eventSeat.setStatus(EventSeatStatus.AVAILABLE);
                eventSeats.add(eventSeat);
            }
        }
        eventSeatRepository.saveAll(eventSeats);

        return new EventDetail(toSummary(event), event.getDescription(), prices(event));
    }

    @Transactional
    public EventDetail update(Long id, UpdateEventRequest request) {
        Event event = require(id);
        if (request.title() != null) {
            event.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            event.setDescription(request.description());
        }
        if (request.startsAt() != null) {
            event.setStartsAt(request.startsAt());
        }
        if (request.saleOpensAt() != null) {
            event.setSaleOpensAt(request.saleOpensAt());
        }
        if (request.highDemand() != null) {
            event.setHighDemand(request.highDemand());
        }
        if (request.maxSeatsPerUser() != null) {
            event.setMaxSeatsPerUser(request.maxSeatsPerUser());
        }
        if (request.status() != null) {
            event.setStatus(request.status());
        }
        return new EventDetail(toSummary(event), event.getDescription(), prices(event));
    }

    List<SectionPrice> prices(Event event) {
        Map<Long, String> sectionNames = event.getVenue().getSections().stream()
                .collect(java.util.stream.Collectors.toMap(VenueSection::getId, VenueSection::getName));
        return priceRepository.findByKeyEventId(event.getId()).stream()
                .map(price -> new SectionPrice(
                        price.getKey().getSectionId(),
                        sectionNames.getOrDefault(price.getKey().getSectionId(), "Section"),
                        price.getPricePaise()))
                .sorted(Comparator.comparing(SectionPrice::pricePaise).reversed())
                .toList();
    }

    EventSummary toSummary(Event event) {
        List<EventSectionPrice> prices = priceRepository.findByKeyEventId(event.getId());
        Long min = prices.stream().mapToLong(EventSectionPrice::getPricePaise).min().stream().boxed().findFirst().orElse(null);
        Long max = prices.stream().mapToLong(EventSectionPrice::getPricePaise).max().stream().boxed().findFirst().orElse(null);
        long available = eventSeatRepository.countByEventIdAndStatus(event.getId(), EventSeatStatus.AVAILABLE);
        long booked = eventSeatRepository.countByEventIdAndStatus(event.getId(), EventSeatStatus.BOOKED);
        return new EventSummary(
                event.getId(),
                event.getTitle(),
                event.getVenue().getName(),
                event.getVenue().getCity(),
                event.getStartsAt(),
                event.getSaleOpensAt(),
                event.isHighDemand(),
                event.getStatus(),
                event.getMaxSeatsPerUser(),
                min,
                max,
                available,
                available + booked);
    }
}
