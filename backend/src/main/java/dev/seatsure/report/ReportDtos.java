package dev.seatsure.report;

import dev.seatsure.queue.QueueDtos.QueueStatsResponse;
import java.time.Instant;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {}

    public record SectionSales(
            Long sectionId, String sectionName, long pricePaise, long totalSeats, long bookedSeats, long revenuePaise) {}

    public record EventReport(
            Long eventId,
            String title,
            String venueName,
            Instant startsAt,
            Instant saleOpensAt,
            boolean highDemand,
            String status,
            long totalSeats,
            long bookedSeats,
            long availableSeats,
            long revenuePaise,
            long confirmedBookings,
            long successfulPayments,
            long ticketsIssued,
            long ticketsCheckedIn,
            List<SectionSales> sections,
            QueueStatsResponse fairness) {}
}
