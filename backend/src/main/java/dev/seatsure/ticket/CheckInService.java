package dev.seatsure.ticket;

import dev.seatsure.auth.User;
import dev.seatsure.auth.UserRepository;
import dev.seatsure.booking.BookingStatus;
import dev.seatsure.common.ConflictException;
import dev.seatsure.common.NotFoundException;
import dev.seatsure.ticket.QrCodeService.ScannedTicket;
import dev.seatsure.ticket.TicketDtos.CheckInResponse;
import dev.seatsure.ticket.TicketDtos.Result;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gate scanning. Every scan is one conditional UPDATE, so a replay can never check in twice. */
@Service
public class CheckInService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final QrCodeService qrCodeService;
    private final Clock clock;
    private final ZoneId displayZone;

    public CheckInService(
            TicketRepository ticketRepository,
            UserRepository userRepository,
            QrCodeService qrCodeService,
            Clock clock) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.qrCodeService = qrCodeService;
        this.clock = clock;
        this.displayZone = ZoneId.systemDefault();
    }

    @Transactional
    public CheckInResponse checkIn(String qrContent, Long staffUserId) {
        ScannedTicket scanned = qrCodeService.verify(qrContent);

        Ticket ticket = ticketRepository.findDetailed(scanned.ticketId())
                .orElseThrow(() -> new NotFoundException("Unknown ticket"));
        if (!ticket.getToken().equals(scanned.token())) {
            throw new NotFoundException("Unknown ticket");
        }
        if (ticket.getBooking().getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("That booking is not confirmed");
        }

        User staff = userRepository.findById(staffUserId).orElseThrow();
        Instant now = clock.instant();
        int updated = ticketRepository.markCheckedIn(ticket.getId(), now, staff);

        if (updated == 0) {
            // The ticket we loaded at the top of this transaction already carries the earlier scan.
            Instant usedAt = ticket.getCheckedInAt();
            String when = usedAt == null ? "earlier" : TIME_FORMAT.format(usedAt.atZone(displayZone));
            return response(ticket, Result.ALREADY_USED, "Already used at " + when, usedAt);
        }
        return response(ticket, Result.OK, "Welcome in", now);
    }

    private CheckInResponse response(Ticket ticket, Result result, String message, Instant checkedInAt) {
        return new CheckInResponse(
                result,
                message,
                ticket.getBooking().getUser().getName(),
                ticket.getBooking().getEvent().getTitle(),
                ticket.getEventSeat().getSeat().getSection().getName(),
                ticket.getEventSeat().getSeat().label(),
                checkedInAt);
    }
}
