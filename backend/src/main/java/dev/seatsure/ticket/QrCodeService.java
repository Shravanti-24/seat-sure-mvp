package dev.seatsure.ticket;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import dev.seatsure.common.BadRequestException;
import dev.seatsure.common.SeatSureProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * QR payload is {@code ticketId.token.signature} where the signature is an HMAC-SHA256 of
 * {@code ticketId.token} with a server-side secret. Without the secret a ticket cannot be forged,
 * and the gate can reject a tampered code before it ever touches the database.
 */
@Service
public class QrCodeService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int QR_SIZE_PX = 420;

    private final byte[] secret;

    public QrCodeService(SeatSureProperties properties) {
        this.secret = properties.ticket().hmacSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String qrContent(Ticket ticket) {
        String payload = ticket.getId() + "." + ticket.getToken();
        return payload + "." + sign(payload);
    }

    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot sign ticket", ex);
        }
    }

    /** Splits and verifies a scanned code. Throws 400 when the signature does not match. */
    public ScannedTicket verify(String qrContent) {
        String[] parts = qrContent == null ? new String[0] : qrContent.trim().split("\\.");
        if (parts.length != 3) {
            throw new BadRequestException("That is not a SeatSure ticket");
        }
        long ticketId;
        try {
            ticketId = Long.parseLong(parts[0]);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("That is not a SeatSure ticket");
        }
        String expected = sign(parts[0] + "." + parts[1]);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new BadRequestException("This ticket has been tampered with");
        }
        return new ScannedTicket(ticketId, parts[1]);
    }

    public byte[] pngFor(Ticket ticket) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                    qrContent(ticket),
                    BarcodeFormat.QR_CODE,
                    QR_SIZE_PX,
                    QR_SIZE_PX,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Cannot render QR code", ex);
        }
    }

    public record ScannedTicket(long ticketId, String token) {}
}
