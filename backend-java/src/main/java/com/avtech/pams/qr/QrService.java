package com.avtech.pams.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * QR codes for items, assets and gate passes. Each code is a link to the app's scan page carrying a
 * signed checksum, so a phone camera opens the record and a forged or edited link is refused.
 * Codes use high error correction so a worn sticker still scans.
 */
@Service
public class QrService {

    public static final int DEFAULT_SIZE = 300;

    private final byte[] secret;
    private final String appUrl;

    public QrService(@Value("${pams.jwt.secret}") String jwtSecret, @Value("${pams.app-url}") String appUrl) {
        this.secret = ("qr:" + jwtSecret).getBytes(StandardCharsets.UTF_8);
        this.appUrl = appUrl.replaceAll("/+$", "");
    }

    /** First 12 hex digits of HMAC-SHA256 over "type:id:code". */
    public String checksum(String type, long id, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] h = mac.doFinal((type + ":" + id + ":" + (code == null ? "" : code)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h).substring(0, 12);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean verify(String type, long id, String code, String given) {
        if (given == null) return false;
        return MessageDigest.isEqual(checksum(type, id, code).getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    /** The link a code carries: {app}/scan/{type}/{id}?c={checksum}. */
    public String url(String type, long id, String code) {
        return appUrl + "/scan/" + type + "/" + id + "?c=" + checksum(type, id, code);
    }

    public byte[] png(String content, int size) {
        int px = Math.max(100, Math.min(size, 1200));
        try {
            BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, px, px,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H, EncodeHintType.MARGIN, 1, EncodeHintType.CHARACTER_SET, "UTF-8"));
            BufferedImage img = new BufferedImage(m.getWidth(), m.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < m.getHeight(); y++) for (int x = 0; x < m.getWidth(); x++) img.setRGB(x, y, m.get(x, y) ? 0x000000 : 0xFFFFFF);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (WriterException e) {
            throw new IllegalArgumentException("Cannot encode QR content", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String dataUri(String content, int size) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png(content, size));
    }
}
