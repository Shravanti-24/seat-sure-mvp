package dev.seatsure.ticket;

import dev.seatsure.booking.Booking;
import dev.seatsure.common.NotFoundException;
import dev.seatsure.event.EventSeat;
import dev.seatsure.ticket.TicketDtos.TicketResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final QrCodeService qrCodeService;

    public TicketService(TicketRepository ticketRepository, QrCodeService qrCodeService) {
        this.ticketRepository = ticketRepository;
        this.qrCodeService = qrCodeService;
    }

    /** Called inside the payment transaction: one ticket per confirmed seat. */
    public List<Ticket> issue(Booking booking, List<EventSeat> eventSeats) {
        List<Ticket> tickets = new ArrayList<>();
        for (EventSeat eventSeat : eventSeats) {
            Ticket ticket = new Ticket();
            ticket.setBooking(booking);
            ticket.setEventSeat(eventSeat);
            ticket.setToken(UUID.randomUUID().toString());
            tickets.add(ticket);
        }
        return ticketRepository.saveAll(tickets);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> listForUser(Long userId) {
        return ticketRepository.findForUser(userId).stream().map(TicketService::toResponse).toList();
    }

    /** Only the attendee who owns the ticket may download its QR code. */
    @Transactional(readOnly = true)
    public byte[] qrPngForOwner(Long ticketId, Long userId) {
        Ticket ticket = ticketRepository.findDetailed(ticketId)
                .orElseThrow(() -> new NotFoundException("No ticket with id " + ticketId));
        if (!ticket.getBooking().getUser().getId().equals(userId)) {
            throw new NotFoundException("No ticket with id " + ticketId);
        }
        return qrCodeService.pngFor(ticket);
    }

    static TicketResponse toResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getBooking().getId(),
                ticket.getBooking().getEvent().getId(),
                ticket.getBooking().getEvent().getTitle(),
                ticket.getBooking().getEvent().getVenue().getName(),
                ticket.getBooking().getEvent().getVenue().getCity(),
                ticket.getBooking().getEvent().getStartsAt(),
                ticket.getEventSeat().getSeat().getSection().getName(),
                ticket.getEventSeat().getSeat().label(),
                ticket.getCheckedInAt());
    }
}
