package dev.seatsure.report;

import dev.seatsure.booking.BookingRepository;
import dev.seatsure.booking.BookingStatus;
import dev.seatsure.event.Event;
import dev.seatsure.event.EventSeat;
import dev.seatsure.event.EventSeatRepository;
import dev.seatsure.event.EventSeatStatus;
import dev.seatsure.event.EventSectionPrice;
import dev.seatsure.event.EventSectionPriceRepository;
import dev.seatsure.event.EventService;
import dev.seatsure.payment.PaymentRepository;
import dev.seatsure.payment.PaymentStatus;
import dev.seatsure.queue.QueueStatsService;
import dev.seatsure.report.ReportDtos.EventReport;
import dev.seatsure.report.ReportDtos.SectionSales;
import dev.seatsure.ticket.TicketRepository;
import dev.seatsure.venue.VenueSection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sales plus the fairness numbers an organiser actually wants after a drop. */
@Service
public class ReportService {

    private final EventService eventService;
    private final EventSeatRepository eventSeatRepository;
    private final EventSectionPriceRepository priceRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final QueueStatsService queueStatsService;

    public ReportService(
            EventService eventService,
            EventSeatRepository eventSeatRepository,
            EventSectionPriceRepository priceRepository,
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            TicketRepository ticketRepository,
            QueueStatsService queueStatsService) {
        this.eventService = eventService;
        this.eventSeatRepository = eventSeatRepository;
        this.priceRepository = priceRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.ticketRepository = ticketRepository;
        this.queueStatsService = queueStatsService;
    }

    @Transactional(readOnly = true)
    public EventReport eventReport(Long eventId) {
        Event event = eventService.require(eventId);

        Map<Long, Long> priceBySection = new HashMap<>();
        for (EventSectionPrice price : priceRepository.findByKeyEventId(eventId)) {
            priceBySection.put(price.getKey().getSectionId(), price.getPricePaise());
        }

        Map<Long, long[]> perSection = new LinkedHashMap<>();
        Map<Long, String> sectionNames = new LinkedHashMap<>();
        for (EventSeat eventSeat : eventSeatRepository.findSeatMap(eventId)) {
            VenueSection section = eventSeat.getSeat().getSection();
            sectionNames.putIfAbsent(section.getId(), section.getName());
            long[] counts = perSection.computeIfAbsent(section.getId(), key -> new long[2]);
            counts[0]++;
            if (eventSeat.getStatus() == EventSeatStatus.BOOKED) {
                counts[1]++;
            }
        }

        List<SectionSales> sections = new ArrayList<>();
        long totalSeats = 0;
        long bookedSeats = 0;
        long revenue = 0;
        for (Map.Entry<Long, long[]> entry : perSection.entrySet()) {
            long price = priceBySection.getOrDefault(entry.getKey(), 0L);
            long seats = entry.getValue()[0];
            long booked = entry.getValue()[1];
            sections.add(new SectionSales(
                    entry.getKey(), sectionNames.get(entry.getKey()), price, seats, booked, booked * price));
            totalSeats += seats;
            bookedSeats += booked;
            revenue += booked * price;
        }

        return new EventReport(
                event.getId(),
                event.getTitle(),
                event.getVenue().getName(),
                event.getStartsAt(),
                event.getSaleOpensAt(),
                event.isHighDemand(),
                event.getStatus().name(),
                totalSeats,
                bookedSeats,
                totalSeats - bookedSeats,
                revenue,
                bookingRepository.countByEventIdAndStatus(eventId, BookingStatus.CONFIRMED),
                paymentRepository.countByBookingEventIdAndStatus(eventId, PaymentStatus.SUCCESS),
                ticketRepository.countByBookingEventId(eventId),
                ticketRepository.countByBookingEventIdAndCheckedInAtIsNotNull(eventId),
                sections,
                queueStatsService.report(eventId));
    }
}
