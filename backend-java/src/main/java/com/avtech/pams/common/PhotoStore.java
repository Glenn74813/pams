package com.avtech.pams.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Pictures of assets and items under the public storage dir: photos/{kind}/{id}/{random}.png|jpg|webp. */
@Component
public class PhotoStore {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private final Path root;

    public PhotoStore(@Value("${pams.storage.public-dir}") String publicDir) {
        this.root = Path.of(publicDir).toAbsolutePath().normalize();
    }

    public record Photo(byte[] data, String contentType) {
    }

    public String save(String kind, Long id, MultipartFile upload) {
        String ext = extension(upload);
        String relative = "photos/" + kind + "/" + id + "/" + UUID.randomUUID() + "." + ext;
        Path target = resolve(relative);
        try (InputStream in = upload.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target);
        } catch (IOException e) {
            throw ApiException.of(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the photo.");
        }
        return relative;
    }

    public Photo read(String relativePath) {
        Path file = resolve(relativePath);
        if (file == null || !Files.isRegularFile(file)) return null;
        try {
            String lower = relativePath.toLowerCase();
            String type = lower.endsWith(".png") ? "image/png" : lower.endsWith(".webp") ? "image/webp" : "image/jpeg";
            return new Photo(Files.readAllBytes(file), type);
        } catch (IOException e) {
            return null;
        }
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
    private static String extension(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) throw ApiException.validation("photo", "Choose a photo to upload.");
        if (upload.getSize() > MAX_BYTES) throw ApiException.validation("photo", "The photo may not be larger than 5 MB.");
        try (InputStream in = upload.getInputStream()) {
            byte[] h = in.readNBytes(12);
            if (h.length >= 4 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "png";
            if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "jpg";
            if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "webp";
        } catch (IOException e) {
            throw ApiException.validation("photo", "The photo could not be read.");
        }
        throw ApiException.validation("photo", "The photo must be a PNG, JPEG or WebP image.");
    }

    /** Resolves inside the storage root only; anything that escapes it is ignored. */
    private Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return null;
        Path p = root.resolve(relativePath).normalize();
        return p.startsWith(root) ? p : null;
    }
}
