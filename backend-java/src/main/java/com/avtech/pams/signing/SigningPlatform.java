package com.avtech.pams.signing;

import com.avtech.pams.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The link to an external digital signing platform. When {@code pams.signing.platform-url} is set,
 * each new signing request is posted there as JSON (after the request is saved); the platform fetches
 * the file and posts the signature back to /api/signing/platform/..., sending
 * {@code pams.signing.platform-key} in the X-Signing-Key header.
 */
@Component
public class SigningPlatform {

    private static final Logger log = LoggerFactory.getLogger(SigningPlatform.class);

    private final String url;
    private final String key;
    private final String appUrl;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SigningPlatform(@Value("${pams.signing.platform-url:}") String url, @Value("${pams.signing.platform-key:}") String key,
                           @Value("${pams.app-url}") String appUrl, ObjectMapper json) {
        this.url = url == null ? "" : url.trim();
        this.key = key == null ? "" : key.trim();
        this.appUrl = appUrl.replaceAll("/+$", "");
        this.json = json;
    }

    public boolean enabled() {
        return !key.isEmpty();
    }

    /** Refuses platform calls unless a key is configured and the caller sent it. */
    void check(String given) {
        if (key.isEmpty()) throw ApiException.of(HttpStatus.NOT_FOUND, "No signing platform is configured.");
        if (given == null || !SigningController.same(given, key)) throw ApiException.of(HttpStatus.UNAUTHORIZED, "Invalid signing platform key.");
    }

    void announce(String number, String title, String sha256, String signerEmail) {
        if (url.isEmpty() || key.isEmpty()) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("request_number", number);
        body.put("title", title);
        body.put("sha256", sha256);
        body.put("signer_email", signerEmail);
        body.put("file_url", appUrl + "/api/signing/platform/requests/" + number + "/file");
        body.put("callback_url", appUrl + "/api/signing/platform/signatures");
        Runnable send = () -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json").header("X-Signing-Key", key)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((res, err) -> {
                if (err != null) log.warn("Could not notify the signing platform about {}: {}", number, err.toString());
                else if (res.statusCode() >= 300) log.warn("The signing platform answered {} for {}", res.statusCode(), number);
            });
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
