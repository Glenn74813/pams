package com.avtech.pams.integration;

import com.avtech.pams.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calls another system's API for PAMS, read-only: a GET with the configured credentials, no redirects, a time limit
 * and a size limit, and never to the server itself or cloud metadata addresses (so an administrator's URL cannot be
 * turned against PAMS's own network). The JSON that comes back is cut down to rows and named columns.
 * Another system on the same machine is reached only when its host and port are in
 * {@code pams.integrations.allowed-hosts}. With OAuth 2.0 client credentials, PAMS gets an access token from the
 * token address and keeps it until shortly before it expires.
 */
@Component
public class ExternalApiClient {

    /** Largest response read: enough for a page of records, not for a whole database. */
    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_ROWS = 500;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");

    private final ObjectMapper json;
    private final SecretKeySpec key;
    private final Set<String> allowedHosts;
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    // HTTP/1.1: on plain http the default HTTP/2 upgrade request hangs some servers and proxies (e.g. Vite's).
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10)).build();

    public ExternalApiClient(ObjectMapper json, @Value("${pams.integrations.key:${pams.audit.seal-key:${pams.jwt.secret}}}") String secret,
                             @Value("${pams.integrations.allowed-hosts:}") String allowedHosts) {
        this.json = json;
        this.allowedHosts = new java.util.HashSet<>();
        for (String h : allowedHosts.split(",")) {
            if (!h.isBlank()) this.allowedHosts.add(h.trim().toLowerCase());
        }
        try {
            this.key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(("pams-integrations:" + secret).getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** One table of what the other system returned. */
    public record Result(List<String> columns, List<List<Object>> rows, boolean truncated, int status) {
    }

    /** A column to show: its heading and where the value sits in each record ("amount", "payee.name"). */
    public record Column(String label, String field) {
    }

    public record Call(String baseUrl, String path, String authType, String authHeader, String secret, String tokenUrl, String clientId,
                       String dataPath, List<Column> columns, int timeoutSeconds) {
    }

    private record Token(String value, Instant expires) {
    }

    private record Answer(int status, byte[] body) {
    }

    // ───────────────────────── secrets ─────────────────────────

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return null;
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] body = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(body, 0, all, iv.length, body.length);
            return Base64.getEncoder().encodeToString(all);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt the secret", e);
        }
    }

    public String decrypt(String cipher) {
        if (cipher == null || cipher.isEmpty()) return null;
        try {
            byte[] all = Base64.getDecoder().decode(cipher);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw ApiException.of(HttpStatus.CONFLICT, "The stored secret can no longer be read (the server key changed); enter it again.");
        }
    }

    // ───────────────────────── calls ─────────────────────────

    /** Builds the address, filling {placeholders} from the values given, each URL-encoded. */
    public URI address(String baseUrl, String path, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(path == null ? "" : path);
        StringBuilder filled = new StringBuilder();
        while (m.find()) {
            String v = values.get(m.group(1));
            if (v == null) throw ApiException.validation("path", "There is no value for {" + m.group(1) + "} here.");
            m.appendReplacement(filled, Matcher.quoteReplacement(URLEncoder.encode(v, StandardCharsets.UTF_8)));
        }
        m.appendTail(filled);
        String base = baseUrl.replaceAll("/+$", "");
        String rest = filled.toString();
        URI uri;
        try {
            uri = URI.create(rest.isEmpty() ? base : base + (rest.startsWith("/") || rest.startsWith("?") ? "" : "/") + rest);
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("base_url", "That is not a valid address.");
        }
        checkAddress(uri);
        return uri;
    }

    public void checkAddress(URI uri) {
        checkAddress(uri, "base_url");
    }

    /**
     * http or https to a named host that is not a link-local or cloud metadata address, nor this machine unless its
     * host and port are in pams.integrations.allowed-hosts.
     */
    public void checkAddress(URI uri, String field) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("https") && !scheme.equals("http")) throw ApiException.validation(field, "Use an http or https address.");
        if (uri.getHost() == null || uri.getHost().isBlank()) throw ApiException.validation(field, "The address needs a host name.");
        if (uri.getUserInfo() != null) throw ApiException.validation(field, "Put credentials in the sign-in fields, not in the address.");
        int port = uri.getPort() != -1 ? uri.getPort() : scheme.equals("https") ? 443 : 80;
        boolean listed = allowedHosts.contains(uri.getHost().toLowerCase() + ":" + port);
        try {
            for (InetAddress a : InetAddress.getAllByName(uri.getHost())) {
                if (a.isLinkLocalAddress() || a.isMulticastAddress()) {
                    throw ApiException.validation(field, "PAMS does not call link-local or cloud metadata addresses.");
                }
                if ((a.isLoopbackAddress() || a.isAnyLocalAddress()) && !listed) {
                    throw ApiException.validation(field, uri.getHost() + ":" + port + " is on the PAMS server itself. If another system runs there, "
                            + "add " + uri.getHost() + ":" + port + " to PAMS_INTEGRATIONS_ALLOWED_HOSTS in the server's .env and restart PAMS.");
                }
            }
        } catch (UnknownHostException e) {
            throw ApiException.validation(field, "The host " + uri.getHost() + " could not be found.");
        }
    }

    public Result fetch(Call call, Map<String, String> values) {
        URI uri = address(call.baseUrl(), call.path(), values);
        Answer res = send(request(call, uri, false), call.timeoutSeconds());
        if ("oauth".equals(call.authType()) && res.status() == 401) {
            // The token may have been revoked or expired early: get a new one once.
            res = send(request(call, uri, true), call.timeoutSeconds());
        }
        if (res.status() >= 300 && res.status() < 400) throw ApiException.of(HttpStatus.BAD_GATEWAY, "The other system answered with a redirect (" + res.status() + "); enter the final address.");
        if (res.status() == 401 || res.status() == 403) throw ApiException.of(HttpStatus.BAD_GATEWAY, "The other system refused the credentials (" + res.status() + ").");
        if (res.status() == 404) throw ApiException.of(HttpStatus.BAD_GATEWAY, "The other system has nothing at " + uri.getPath() + " (404); check the path.");
        if (res.status() >= 400) throw ApiException.of(HttpStatus.BAD_GATEWAY, "The other system answered with an error (" + res.status() + ").");
        return table(parse(res.body(), "the address"), call.dataPath(), call.columns(), res.status());
    }

    private HttpRequest request(Call call, URI uri, boolean freshToken) {
        HttpRequest.Builder req = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofSeconds(Math.max(1, Math.min(60, call.timeoutSeconds()))))
                .header("Accept", "application/json").header("User-Agent", "PAMS-integration");
        String secret = call.secret();
        switch (call.authType()) {
            case "bearer" -> req.header("Authorization", "Bearer " + secret);
            case "basic" -> req.header("Authorization", "Basic " + Base64.getEncoder().encodeToString((secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8)));
            case "header" -> req.header(call.authHeader(), secret == null ? "" : secret);
            case "oauth" -> req.header("Authorization", "Bearer " + accessToken(call, freshToken));
            default -> {
            }
        }
        return req.build();
    }

    /** An OAuth 2.0 client-credentials token (RFC 6749 4.4), kept until 30 seconds before it expires. */
    private String accessToken(Call call, boolean fresh) {
        String cacheKey = call.tokenUrl() + "\n" + call.clientId() + "\n" + digest(call.secret());
        Token t = tokens.get(cacheKey);
        if (!fresh && t != null && t.expires().isAfter(Instant.now().plusSeconds(30))) return t.value();
        URI uri;
        try {
            uri = URI.create(call.tokenUrl());
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("token_url", "The token address is not valid.");
        }
        checkAddress(uri, "token_url");
        String id = URLEncoder.encode(call.clientId() == null ? "" : call.clientId(), StandardCharsets.UTF_8);
        String secret = URLEncoder.encode(call.secret() == null ? "" : call.secret(), StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(Math.max(1, Math.min(60, call.timeoutSeconds()))))
                .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json").header("User-Agent", "PAMS-integration")
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((id + ":" + secret).getBytes(StandardCharsets.UTF_8)))
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")).build();
        Answer res = send(req, call.timeoutSeconds());
        if (res.status() == 400 || res.status() == 401) throw ApiException.of(HttpStatus.BAD_GATEWAY, "The other system refused the client id or secret (" + res.status() + ").");
        if (res.status() >= 300) throw ApiException.of(HttpStatus.BAD_GATEWAY, "The token address answered with " + res.status() + "; check it.");
        JsonNode root = parse(res.body(), "the token address");
        JsonNode token = root.get("access_token");
        if (token == null || !token.isString() || token.asString().isBlank()) throw ApiException.of(HttpStatus.BAD_GATEWAY, "The token address did not return an access_token.");
        JsonNode expiresIn = root.get("expires_in");
        long seconds = expiresIn != null && expiresIn.isNumber() ? expiresIn.asLong() : 300;
        tokens.put(cacheKey, new Token(token.asString(), Instant.now().plusSeconds(Math.max(1, seconds))));
        return token.asString();
    }

    private Answer send(HttpRequest req, int timeoutSeconds) {
        try {
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                return new Answer(res.statusCode(), readLimited(in));
            }
        } catch (java.net.http.HttpTimeoutException e) {
            throw ApiException.of(HttpStatus.BAD_GATEWAY, "The other system did not answer within " + timeoutSeconds + " seconds.");
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.of(HttpStatus.BAD_GATEWAY, "The call to the other system was interrupted.");
        } catch (Exception e) {
            throw ApiException.of(HttpStatus.BAD_GATEWAY, "Could not reach the other system: " + e.getClass().getSimpleName() + ".");
        }
    }

    private JsonNode parse(byte[] body, String where) {
        String start = new String(body, 0, Math.min(body.length, 200), StandardCharsets.UTF_8).stripLeading();
        if (start.startsWith("<")) {
            throw ApiException.of(HttpStatus.BAD_GATEWAY, "Answered with a web page, not JSON: " + where + " is a page of the other system, not its API. "
                    + "API addresses often end in /api or /api/v1.");
        }
        try {
            return json.readTree(body);
        } catch (Exception e) {
            throw ApiException.of(HttpStatus.BAD_GATEWAY, "The other system did not answer with JSON.");
        }
    }

    private static String digest(String s) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] readLimited(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            if (out.size() + n > MAX_BYTES) throw ApiException.of(HttpStatus.BAD_GATEWAY, "The answer is larger than 2 MB; ask the other system for fewer records per call.");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** The records at dataPath (the whole answer when blank) as rows; without columns, the first record's plain fields. */
    static Result table(JsonNode root, String dataPath, List<Column> columns, int status) {
        JsonNode data = at(root, dataPath);
        if (data == null || data.isMissingNode() || data.isNull()) throw ApiException.of(HttpStatus.BAD_GATEWAY, "Nothing was found at \"" + dataPath + "\" in the answer.");
        List<JsonNode> records = new ArrayList<>();
        if (data.isArray()) data.forEach(records::add);
        else records.add(data);
        List<Column> cols = columns == null || columns.isEmpty() ? detect(records) : columns;
        List<List<Object>> rows = new ArrayList<>();
        for (JsonNode r : records) {
            if (rows.size() >= MAX_ROWS) break;
            List<Object> row = new ArrayList<>();
            for (Column c : cols) row.add(value(at(r, c.field())));
            rows.add(row);
        }
        return new Result(cols.stream().map(Column::label).toList(), rows, records.size() > MAX_ROWS, status);
    }

    private static List<Column> detect(List<JsonNode> records) {
        List<Column> out = new ArrayList<>();
        if (records.isEmpty() || !records.get(0).isObject()) {
            out.add(new Column("Value", ""));
            return out;
        }
        for (Map.Entry<String, JsonNode> e : records.get(0).properties()) {
            if (e.getValue().isValueNode() && out.size() < 12) out.add(new Column(e.getKey(), e.getKey()));
        }
        return out;
    }

    /** Follows a dotted path ("data.items", "payee.name"); blank means the node itself. */
    static JsonNode at(JsonNode node, String path) {
        if (path == null || path.isBlank()) return node;
        JsonNode cur = node;
        for (String part : path.split("\\.")) {
            if (cur == null) return null;
            cur = cur.isArray() && part.matches("\\d+") ? cur.get(Integer.parseInt(part)) : cur.get(part);
        }
        return cur;
    }

    private static Object value(JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) return null;
        if (v.isNumber()) return v.numberValue();
        if (v.isBoolean()) return v.asBoolean();
        if (v.isValueNode()) return v.asString();
        return v.toString();
    }

    /** The values a payable-context API can use in its path. */
    public static Map<String, String> payableValues(String reference, String poNumber, String contractNumber, String supplierTin, String supplierName) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("reference", reference);
        if (poNumber != null) m.put("po_number", poNumber);
        if (contractNumber != null) m.put("contract_number", contractNumber);
        if (supplierTin != null) m.put("supplier_tin", supplierTin);
        if (supplierName != null) m.put("supplier_name", supplierName);
        return m;
    }
}
