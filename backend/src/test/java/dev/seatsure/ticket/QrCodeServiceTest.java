package dev.seatsure.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.seatsure.common.BadRequestException;
import dev.seatsure.common.SeatSureProperties;
import dev.seatsure.ticket.QrCodeService.ScannedTicket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QrCodeServiceTest {

    private QrCodeService qrCodeService;

    @BeforeEach
    void setUp() {
        qrCodeService = new QrCodeService(properties("test-hmac-secret-for-unit-tests"));
    }

    @Test
    @DisplayName("a code this server signed verifies back to the same ticket")
    void verifiesOwnSignature() {
        Ticket ticket = ticket(42L, "8f14e45f-ceea-467a-9b1c-c9c0f1a1e3a1");

        ScannedTicket scanned = qrCodeService.verify(qrCodeService.qrContent(ticket));

        assertThat(scanned.ticketId()).isEqualTo(42L);
        assertThat(scanned.token()).isEqualTo(ticket.getToken());
    }

    @Test
    @DisplayName("editing the ticket id invalidates the signature")
    void rejectsTamperedTicketId() {
        Ticket ticket = ticket(42L, "8f14e45f-ceea-467a-9b1c-c9c0f1a1e3a1");
        String[] parts = qrCodeService.qrContent(ticket).split("\\.");
        String forged = "43." + parts[1] + "." + parts[2];

        assertThatThrownBy(() -> qrCodeService.verify(forged))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tampered");
    }

    @Test
    @DisplayName("a signature made with a different secret is rejected")
    void rejectsForeignSecret() {
        Ticket ticket = ticket(42L, "8f14e45f-ceea-467a-9b1c-c9c0f1a1e3a1");
        QrCodeService attacker = new QrCodeService(properties("the-attacker-guessed-wrong"));

        assertThatThrownBy(() -> qrCodeService.verify(attacker.qrContent(ticket)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tampered");
    }

    @Test
    @DisplayName("anything that is not three dot-separated parts is not a ticket")
    void rejectsMalformedContent() {
        assertThatThrownBy(() -> qrCodeService.verify("https://example.com/not-a-ticket"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> qrCodeService.verify("abc.def.ghi"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> qrCodeService.verify(null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("the QR renders as a PNG")
    void rendersPng() {
        byte[] png = qrCodeService.pngFor(ticket(7L, "a-token"));

        assertThat(png).isNotEmpty();
        // PNG magic number.
        assertThat(new byte[] {png[0], png[1], png[2], png[3]}).containsExactly((byte) 0x89, 'P', 'N', 'G');
    }

    private static Ticket ticket(Long id, String token) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setToken(token);
        return ticket;
    }

    private static SeatSureProperties properties(String hmacSecret) {
        return new SeatSureProperties(
                new SeatSureProperties.Jwt("jwt-secret-that-is-long-enough-for-hs256", 720),
                new SeatSureProperties.Hold(420, 6, 30, 30000),
                new SeatSureProperties.Ticket(hmacSecret),
                new SeatSureProperties.Queue(25, 5000, 600, 60),
                new SeatSureProperties.RateLimit(true, 60, java.util.Map.of()),
                new SeatSureProperties.Seed("a", "b", "c", 2));
    }
}
