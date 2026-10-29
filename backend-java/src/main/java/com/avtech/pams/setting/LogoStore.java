package com.avtech.pams.setting;

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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Document logo images under the public storage dir (shared with the Laravel app while both run). */
@Component
public class LogoStore {

    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.of("png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "svg", "image/svg+xml");

    private final Path root;

    public LogoStore(@Value("${pams.storage.public-dir}") String publicDir) {
        this.root = Path.of(publicDir).toAbsolutePath().normalize();
    }

    public String dataUri(String relativePath) {
        Path file = resolve(relativePath);
        if (file == null || !Files.isRegularFile(file)) return null;
        try {
            return "data:" + TYPES.getOrDefault(extension(relativePath), "image/png") + ";base64,"
                    + Base64.getEncoder().encodeToString(Files.readAllBytes(file));
        } catch (IOException e) {
            return null;
        }
    }

    /** Stores the upload as document-logos/{random}.{ext} and returns that relative path. */
    public String save(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) throw ApiException.validation("logo", "Choose an image file.");
        if (upload.getSize() > MAX_BYTES) throw ApiException.validation("logo", "The logo must be 2 MB or smaller.");
        String ext = extension(upload.getOriginalFilename());
        if (!TYPES.containsKey(ext)) throw ApiException.validation("logo", "Upload a PNG, JPG or SVG image.");
        String relative = "document-logos/" + UUID.randomUUID() + "." + ext;
        Path target = resolve(relative);
        try (InputStream in = upload.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target);
        } catch (IOException e) {
            throw ApiException.of(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the logo.");
        }
        return relative;
    }

    public void delete(String relativePath) {
        Path file = resolve(relativePath);
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // A stale file is harmless; the row is removed regardless.
        }
    }

    private Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return null;
        Path p = root.resolve(relativePath).normalize();
        return p.startsWith(root) ? p : null;
    }

    private static String extension(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
