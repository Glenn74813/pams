package com.avtech.pams.auth;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.user.User;
import com.avtech.pams.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single sign-on (TOR 1.3.1) with an OpenID Connect provider, authorization-code flow with PKCE.
 * The browser goes to /api/auth/sso/start, signs in at the provider, and comes back to
 * /api/auth/sso/callback; the ID token's signature, issuer, audience, expiry and nonce are checked,
 * the person is matched to an existing active PAMS account (by the provider's subject id once
 * linked, else by email), and the browser is sent to the login page with a one-time code that it
 * swaps for the usual PAMS token. PAMS roles and permissions still apply.
 */
@RestController
@RequestMapping("/api/auth/sso")
public class SsoController {

    private static final Logger log = LoggerFactory.getLogger(SsoController.class);
    private static final Duration FLOW_TTL = Duration.ofMinutes(10);
    private static final Duration CODE_TTL = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String issuer;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String label;
    private final String scopes;
    private final String appUrl;
    private final AuthService auth;
    private final AuthController authController;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private record Flow(String nonce, String verifier, Instant expires) {
    }

    private record Code(Long userId, JwtService.IssuedToken token, Instant expires) {
    }

    private final Map<String, Flow> flows = new ConcurrentHashMap<>();
    private final Map<String, Code> codes = new ConcurrentHashMap<>();
    private volatile JsonNode discovery;
    private volatile JwkSet jwks;
    private volatile Instant jwksFetched = Instant.EPOCH;
    /** How long the provider's key set is reused before it is fetched again. */
    private static final Duration JWKS_TTL = Duration.ofHours(1);

    public SsoController(@Value("${pams.sso.issuer:}") String issuer, @Value("${pams.sso.client-id:}") String clientId,
                         @Value("${pams.sso.client-secret:}") String clientSecret, @Value("${pams.sso.redirect-uri:}") String redirectUri,
                         @Value("${pams.sso.button-label:Sign in with single sign-on}") String label,
                         @Value("${pams.sso.scopes:openid email profile}") String scopes, @Value("${pams.app-url}") String appUrl,
                         AuthService auth, AuthController authController, UserRepository users, JdbcTemplate jdbc, ObjectMapper json) {
        this.issuer = issuer == null ? "" : issuer.trim().replaceAll("/+$", "");
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.appUrl = appUrl.replaceAll("/+$", "");
        this.redirectUri = redirectUri == null || redirectUri.isBlank() ? this.appUrl + "/api/auth/sso/callback" : redirectUri.trim();
        this.label = label;
        this.scopes = scopes.contains("openid") ? scopes : "openid " + scopes;
        this.auth = auth;
        this.authController = authController;
        this.users = users;
        this.jdbc = jdbc;
        this.json = json;
    }

    private boolean enabled() {
        return !issuer.isEmpty() && !clientId.isEmpty();
    }

    /** What the login page needs to show (or hide) the SSO button. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled());
        m.put("label", label);
        return m;
    }

    @GetMapping("/start")
    public ResponseEntity<Void> start() {
        if (!enabled()) return back("Single sign-on is not set up.");
        try {
            JsonNode d = discovery();
            sweep();
            String state = random();
            String nonce = random();
            String verifier = random() + random();
            flows.put(state, new Flow(nonce, verifier, Instant.now().plus(FLOW_TTL)));
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            String url = d.get("authorization_endpoint").asString() + "?response_type=code"
                    + "&client_id=" + enc(clientId) + "&redirect_uri=" + enc(redirectUri) + "&scope=" + enc(scopes)
                    + "&state=" + enc(state) + "&nonce=" + enc(nonce) + "&code_challenge=" + enc(challenge) + "&code_challenge_method=S256";
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, url).build();
        } catch (Exception e) {
            log.warn("SSO start failed: {}", e.toString());
            return back("The single sign-on provider could not be reached.");
        }
    }

    @GetMapping("/callback")
    @Transactional
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error, HttpServletRequest request) {
        String ip = ClientIp.of(request);
        String ua = request.getHeader("User-Agent");
        Flow flow = state == null ? null : flows.remove(state);
        if (error != null) return back("Single sign-on was cancelled or refused.");
        if (flow == null || flow.expires().isBefore(Instant.now()) || code == null) return back("The sign-in took too long or was not started here. Try again.");
        Claims claims;
        try {
            claims = exchange(code, flow);
        } catch (Exception e) {
            log.warn("SSO callback failed: {}", e.toString());
            auth.ssoRefused(null, ip, ua, "SSO token rejected");
            return back("Single sign-on failed; the provider's answer could not be verified.");
        }
        String subject = claims.getSubject();
        String email = email(claims);
        Optional<User> found = subject == null ? Optional.empty()
                : jdbc.queryForList("select id from users where sso_subject = ?", Long.class, subject).stream().findFirst().flatMap(users::findById);
        if (found.isEmpty() && email != null) {
            found = users.findByEmailIgnoreCase(email);
            // Link the provider's id at the first SSO sign-in, unless the account is already linked to someone else's.
            if (found.isPresent()) {
                String linked = jdbc.queryForObject("select sso_subject from users where id = ?", String.class, found.get().getId());
                if (linked != null && !linked.equals(subject)) found = Optional.empty();
                else if (linked == null && subject != null) jdbc.update("update users set sso_subject = ? where id = ?", subject, found.get().getId());
            }
        }
        if (found.isEmpty()) {
            auth.ssoRefused(email, ip, ua, "No PAMS account for SSO user");
            return back("No PAMS account matches " + (email == null ? "your sign-in" : email) + ". Ask your system administrator to add you.");
        }
        JwtService.IssuedToken token;
        try {
            token = auth.ssoLogin(found.get(), ip, ua);
        } catch (ApiException e) {
            return back(e.getMessage());
        }
        sweep();
        String oneTime = random();
        codes.put(oneTime, new Code(found.get().getId(), token, Instant.now().plus(CODE_TTL)));
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, appUrl + "/login?sso_code=" + enc(oneTime)).build();
    }

    public record ExchangeRequest(String code) {
    }

    /** Swaps the one-time code from the callback for a PAMS token (the same response as a password login). */
    @PostMapping("/exchange")
    public Map<String, Object> exchangeCode(@RequestBody ExchangeRequest body) {
        Code c = body == null || body.code() == null ? null : codes.remove(body.code());
        if (c == null || c.expires().isBefore(Instant.now())) throw ApiException.of(HttpStatus.UNAUTHORIZED, "The sign-in link has expired. Sign in again.");
        User user = users.findWithRoleById(c.userId()).orElseThrow(() -> ApiException.of(HttpStatus.UNAUTHORIZED, "The account no longer exists."));
        if (!user.isActive()) throw ApiException.of(HttpStatus.FORBIDDEN, "This account has been deactivated. Ask your system administrator.");
        return authController.tokenResponse(c.token(), user);
    }

    // ───────────────────────── OpenID Connect ─────────────────────────

    private Claims exchange(String code, Flow flow) throws Exception {
        JsonNode d = discovery();
        StringBuilder form = new StringBuilder("grant_type=authorization_code&code=").append(enc(code))
                .append("&redirect_uri=").append(enc(redirectUri)).append("&client_id=").append(enc(clientId))
                .append("&code_verifier=").append(enc(flow.verifier()));
        if (!clientSecret.isEmpty()) form.append("&client_secret=").append(enc(clientSecret));
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(d.get("token_endpoint").asString())).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString())).build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("token endpoint answered " + res.statusCode());
        JsonNode token = json.readTree(res.body());
        if (token.get("id_token") == null) throw new IllegalStateException("no id_token");
        Claims claims = Jwts.parser()
                .keyLocator(new LocatorAdapter<Key>() {
                    @Override
                    protected Key locate(JwsHeader header) {
                        return key(header.getKeyId());
                    }
                })
                .requireIssuer(d.get("issuer").asString())
                .requireAudience(clientId)
                .clockSkewSeconds(60)
                .build().parseSignedClaims(token.get("id_token").asString()).getPayload();
        if (!flow.nonce().equals(claims.get("nonce", String.class))) throw new JwtException("nonce mismatch");
        return claims;
    }

    private JsonNode discovery() throws Exception {
        JsonNode d = discovery;
        if (d == null) {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(issuer + "/.well-known/openid-configuration")).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new IllegalStateException("discovery answered " + res.statusCode());
            d = json.readTree(res.body());
            if (!issuer.equals(d.get("issuer").asString().replaceAll("/+$", ""))) throw new IllegalStateException("discovery issuer mismatch");
            discovery = d;
        }
        return d;
    }

    /** The provider's signing key by id; the key set is fetched again once when an unknown key appears (key rotation). */
    private Key key(String kid) {
        for (int attempt = 0; attempt < 2; attempt++) {
            JwkSet set = jwks;
            if (set == null || attempt == 1 || jwksFetched.plus(JWKS_TTL).isBefore(Instant.now())) {
                try {
                    HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(discovery().get("jwks_uri").asString())).timeout(Duration.ofSeconds(10)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    set = Jwks.setParser().build().parse(res.body());
                    jwks = set;
                    jwksFetched = Instant.now();
                } catch (Exception e) {
                    throw new JwtException("could not load the provider's keys: " + e);
                }
            }
            for (Jwk<?> k : set.getKeys()) {
                if (kid == null || kid.equals(k.getId())) return (Key) k.toKey();
            }
        }
        throw new JwtException("unknown signing key " + kid);
    }

    private static String email(Claims c) {
        if (Boolean.FALSE.equals(c.get("email_verified", Boolean.class))) return null;
        for (String claim : List.of("email", "upn", "preferred_username")) {
            String v = c.get(claim, String.class);
            if (v != null && v.contains("@")) return v.trim();
        }
        return null;
    }

    private ResponseEntity<Void> back(String message) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, appUrl + "/login?sso_error=" + enc(message)).build();
    }

    private void sweep() {
        Instant now = Instant.now();
        flows.values().removeIf(f -> f.expires().isBefore(now));
        codes.values().removeIf(c -> c.expires().isBefore(now));
    }

    private static String random() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
