package com.avtech.pams.auth;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * RFC 6238 TOTP (SHA1, 6 digits, 30 s), compatible with Google Authenticator / Authy and with the
 * secrets the Laravel app already stored.
 */
@Service
public class TotpService {

    private static final int PERIOD = 30;
    private static final int DIGITS = 6;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateSecret() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(BASE32.charAt(RANDOM.nextInt(32)));
        }
        return sb.toString();
    }

    public String otpauthUri(String secret, String account) {
        String issuer = "Inventory System";
        String label = URLEncoder.encode(issuer + ":" + account, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret
                + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD;
    }

    /** Accepts the current code or one step either side, for clock drift. */
    public boolean verify(String secret, String code) {
        if (secret == null || code == null) {
            return false;
        }
        String c = code.replaceAll("\\s+", "");
        if (!c.matches("\\d{6}")) {
            return false;
        }
        long counter = System.currentTimeMillis() / 1000 / PERIOD;
        for (long i = -1; i <= 1; i++) {
            if (MessageDigest.isEqual(codeFor(secret, counter + i).getBytes(), c.getBytes())) {
                return true;
            }
        }
        return false;
    }

    private static String codeFor(String secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(base32Decode(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int value = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", value % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] base32Decode(String b32) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char ch : b32.toUpperCase().toCharArray()) {
            int pos = BASE32.indexOf(ch);
            if (pos < 0) {
                continue;
            }
            buffer = (buffer << 5) | pos;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xFF);
            }
        }
        return out.toByteArray();
    }
}
