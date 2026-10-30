package com.avtech.pams.signing;

import com.avtech.pams.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks a detached signature over a document's bytes and the signer's X.509 certificate chain
 * against the certificate authorities the agency trusts (PNPKI or the agency's private CA), all with
 * the JDK: the chain must lead to a trusted root, every certificate must be valid now, the signing
 * certificate must allow digital signatures, and the signature must match the file.
 */
@Component
public class CertificateVerifier {

    /** JCA names of the accepted algorithms, by the name clients send. */
    static final Map<String, String> ALGORITHMS = Map.of(
            "RSA-SHA256", "SHA256withRSA", "RSA-SHA384", "SHA384withRSA", "RSA-SHA512", "SHA512withRSA",
            "ECDSA-SHA256", "SHA256withECDSA", "ECDSA-SHA384", "SHA384withECDSA", "ECDSA-SHA512", "SHA512withECDSA");

    private final JdbcTemplate jdbc;

    public CertificateVerifier(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Verified(X509Certificate signer, Long trustedId, String email) {
    }

    /** Parses one or more PEM certificates; the first is the signer's, the rest its intermediates. */
    public static List<X509Certificate> parse(String pem) {
        if (pem == null || !pem.contains("-----BEGIN CERTIFICATE-----")) throw ApiException.validation("certificate_pem", "Paste the certificate in PEM format (-----BEGIN CERTIFICATE-----).");
        try {
            Collection<? extends java.security.cert.Certificate> certs = CertificateFactory.getInstance("X.509")
                    .generateCertificates(new ByteArrayInputStream(pem.trim().getBytes(StandardCharsets.US_ASCII)));
            List<X509Certificate> out = new ArrayList<>();
            for (var c : certs) out.add((X509Certificate) c);
            if (out.isEmpty()) throw ApiException.validation("certificate_pem", "No certificate was found.");
            return out;
        } catch (CertificateException e) {
            throw ApiException.validation("certificate_pem", "The certificate could not be read.");
        }
    }

    public static String fingerprint(X509Certificate c) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(c.getEncoded()));
        } catch (NoSuchAlgorithmException | java.security.cert.CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Verifies, or throws a validation error saying exactly what failed. */
    public Verified verify(byte[] document, String certificatePem, String signatureBase64, String algorithm) {
        String jca = algorithm == null ? null : ALGORITHMS.get(algorithm.toUpperCase());
        if (jca == null) throw ApiException.validation("algorithm", "Use one of: " + String.join(", ", new java.util.TreeSet<>(ALGORITHMS.keySet())) + ".");
        byte[] sig;
        try {
            sig = Base64.getDecoder().decode(signatureBase64 == null ? "" : signatureBase64.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("signature", "The signature must be Base64.");
        }
        if (sig.length == 0) throw ApiException.validation("signature", "The signature is required.");
        List<X509Certificate> chain = parse(certificatePem);
        X509Certificate signer = chain.get(0);
        try {
            signer.checkValidity();
        } catch (CertificateException e) {
            throw ApiException.validation("certificate_pem", "The signing certificate has expired or is not yet valid.");
        }
        boolean[] usage = signer.getKeyUsage();
        if (usage != null && !usage[0] && !usage[1]) throw ApiException.validation("certificate_pem", "This certificate is not meant for digital signatures.");
        Long trustedId = chainToTrusted(chain);
        try {
            Signature s = Signature.getInstance(jca);
            s.initVerify(signer.getPublicKey());
            s.update(document);
            if (!s.verify(sig)) throw ApiException.validation("signature", "The signature does not match this document and certificate.");
        } catch (java.security.GeneralSecurityException e) {
            throw ApiException.validation("signature", "The signature does not match this document and certificate.");
        }
        return new Verified(signer, trustedId, email(signer));
    }

    /** PKIX path validation to any trusted root (revocation is checked by the CA's own services, not here). */
    private Long chainToTrusted(List<X509Certificate> chain) {
        List<Map<String, Object>> trusted = jdbc.queryForList("select id, pem from trusted_certificates");
        if (trusted.isEmpty()) throw ApiException.validation("certificate_pem", "No trusted certificate authority is set up yet; an administrator adds them under Digital Signatures.");
        Set<TrustAnchor> anchors = new HashSet<>();
        Map<String, Long> idByFingerprint = new java.util.HashMap<>();
        for (Map<String, Object> t : trusted) {
            for (X509Certificate c : parse((String) t.get("pem"))) {
                anchors.add(new TrustAnchor(c, null));
                idByFingerprint.put(fingerprint(c), ((Number) t.get("id")).longValue());
            }
        }
        // A chain that ends in a trusted certificate itself is cut there.
        List<X509Certificate> path = new ArrayList<>();
        for (X509Certificate c : chain) {
            if (idByFingerprint.containsKey(fingerprint(c)) && !path.isEmpty()) break;
            path.add(c);
        }
        try {
            CertPath cp = CertificateFactory.getInstance("X.509").generateCertPath(path);
            PKIXParameters params = new PKIXParameters(anchors);
            params.setRevocationEnabled(false);
            PKIXCertPathValidatorResult r = (PKIXCertPathValidatorResult) CertPathValidator.getInstance("PKIX").validate(cp, params);
            return idByFingerprint.get(fingerprint(r.getTrustAnchor().getTrustedCert()));
        } catch (java.security.GeneralSecurityException e) {
            // A self-signed signer that is itself a trusted certificate.
            if (path.size() == 1 && idByFingerprint.containsKey(fingerprint(path.get(0)))) return idByFingerprint.get(fingerprint(path.get(0)));
            throw ApiException.validation("certificate_pem", "The certificate was not issued by a trusted certificate authority.");
        }
    }

    /** The email the certificate was issued to (subject alternative name, or EMAILADDRESS in the subject). */
    static String email(X509Certificate c) {
        try {
            if (c.getSubjectAlternativeNames() != null) {
                for (List<?> san : c.getSubjectAlternativeNames()) {
                    if (san.size() >= 2 && Integer.valueOf(1).equals(san.get(0))) return String.valueOf(san.get(1));
                }
            }
        } catch (java.security.cert.CertificateParsingException ignored) {
            // Fall through to the subject.
        }
        // toString() spells the attribute EMAILADDRESS=...; RFC 2253 form would give it as hex.
        for (String part : c.getSubjectX500Principal().toString().split(",")) {
            String p = part.trim();
            if (p.toUpperCase().startsWith("EMAILADDRESS=")) return p.substring(p.indexOf('=') + 1);
        }
        return null;
    }
}
