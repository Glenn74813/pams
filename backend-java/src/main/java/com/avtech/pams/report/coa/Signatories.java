package com.avtech.pams.report.coa;

import com.avtech.pams.setting.SettingService;
import com.avtech.pams.setting.SettingsController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Laravel's SignatureService + SignatureBlock for the Excel forms: who signs for a COA role, and
 * stamping their uploaded signature and printed name onto a form.
 */
@Component
public class Signatories {

    /** {@code name} is null when nobody is assigned; {@code image} holds the signature file bytes, if any. */
    public record Signatory(String name, String designation, byte[] image, String imageExt) {
    }

    private final JdbcTemplate jdbc;
    private final SettingService settings;
    private final Path root;

    public Signatories(JdbcTemplate jdbc, SettingService settings, @Value("${pams.storage.public-dir}") String publicDir) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.root = Path.of(publicDir).toAbsolutePath().normalize();
    }

    public Signatory resolve(String roleKey) {
        String designation = SettingsController.SIGNATORY_ROLES.getOrDefault(roleKey, roleKey.replace('_', ' '));
        String v = settings.get("signatory_" + roleKey, "");
        if (v == null || !v.matches("\\d+")) return new Signatory(null, designation, null, null);
        List<Map<String, Object>> rows = jdbc.queryForList("select name, signature_path from users where id = ?", Long.parseLong(v));
        if (rows.isEmpty()) return new Signatory(null, designation, null, null);
        String name = (String) rows.get(0).get("name");
        String path = (String) rows.get(0).get("signature_path");
        byte[] image = null;
        String ext = null;
        if (path != null && !path.isBlank()) {
            Path file = root.resolve(path).normalize();
            if (file.startsWith(root) && Files.isRegularFile(file)) {
                try {
                    image = Files.readAllBytes(file);
                    ext = path.toLowerCase().endsWith(".png") ? "png" : "jpeg";
                } catch (IOException ignored) {
                    // Without the image the printed name still goes on the form.
                }
            }
        }
        return new Signatory(name, designation, image, ext);
    }

    /**
     * SignatureBlock::stamp — the signature image at {@code imageCell} (height in pixels, offset 6,2)
     * and the printed name, bold 9pt, at {@code nameCell}.
     */
    public static void stamp(Xlsx x, String imageCell, String nameCell, Signatory s, int height) {
        if (s.image() != null) x.image(imageCell, s.image(), s.imageExt(), height, 6, 2);
        x.set(nameCell, s.name() == null ? "________________________" : s.name());
        x.style(nameCell).bold().size(9);
    }

    public static void stamp(Xlsx x, String imageCell, String nameCell, Signatory s) {
        stamp(x, imageCell, nameCell, s, 42);
    }
}
