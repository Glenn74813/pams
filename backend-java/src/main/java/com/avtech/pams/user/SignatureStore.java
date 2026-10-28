package com.avtech.pams.user;

import com.avtech.pams.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

/** Signature images under the public storage dir (shared with the Laravel app while both run). */
@Component
public class SignatureStore {

    private final Path root;

    public SignatureStore(@Value("${pams.storage.public-dir}") String publicDir) {
        this.root = Path.of(publicDir).toAbsolutePath().normalize();
    }

    public String dataUri(String relativePath) {
        Path file = resolve(relativePath);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            String mime = relativePath.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file));
        } catch (IOException e) {
            return null;
        }
    }

    /** Stores the upload as signatures/{userId}/{random}.png|jpg and returns that relative path. */
    public String save(Long userId, MultipartFile upload) {
        String ext = imageExtension(upload);
        String relative = "signatures/" + userId + "/" + UUID.randomUUID() + "." + ext;
        Path target = resolve(relative);
        try (InputStream in = upload.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target);
        } catch (IOException e) {
            throw ApiException.of(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the signature.");
        }
        return relative;
    }

    public void delete(String relativePath) {
        Path file = resolve(relativePath);
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // A stale file is harmless; the reference is cleared regardless.
            }
        }
    }

    /** Checks the file's magic bytes, not just its name. */
    private static String imageExtension(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw ApiException.validation("signature", "The signature file is required.");
        }
        if (upload.getSize() > 2 * 1024 * 1024) {
            throw ApiException.validation("signature", "The signature may not be larger than 2 MB.");
        }
        try (InputStream in = upload.getInputStream()) {
            byte[] head = in.readNBytes(4);
            if (head.length >= 4 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
                return "png";
            }
            if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
                return "jpg";
            }
        } catch (IOException e) {
            throw ApiException.validation("signature", "The signature could not be read.");
        }
        throw ApiException.validation("signature", "The signature must be a PNG or JPEG image.");
    }

    /** Resolves inside the storage root only; anything that escapes it is ignored. */
    private Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path p = root.resolve(relativePath).normalize();
        return p.startsWith(root) ? p : null;
    }
}
