package com.avtech.pams.dataio;

import com.avtech.pams.common.ApiException;
import com.avtech.pams.common.PageResponse;
import com.avtech.pams.dataio.ImportEntity.Column;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** TOR Technical X / C 13.1: bulk import of master data with a dry run, commit, history and exception reports. */
@RestController
@RequestMapping("/api/data/import")
@PreAuthorize("@perm.any('manage_system_settings')")
public class DataImportController {

    private final DataImportService service;

    public DataImportController(DataImportService service) {
        this.service = service;
    }

    /** What can be imported and the columns each takes. */
    @GetMapping("/entities")
    public Map<String, Object> entities() {
        List<Map<String, Object>> out = Arrays.stream(ImportEntity.values()).map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", e.key);
            m.put("label", e.label);
            m.put("columns", e.columns.stream().map(c -> {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("name", c.name());
                cm.put("required", c.required());
                cm.put("hint", c.hint());
                cm.put("aliases", c.aliases());
                return cm;
            }).toList());
            return m;
        }).toList();
        return Map.of("entities", out, "max_rows", ImportValidator.MAX_ROWS);
    }

    /** The CSV template: the expected header row and one example row. */
    @GetMapping("/templates/{entity}")
    public ResponseEntity<byte[]> template(@PathVariable String entity) {
        ImportEntity e = ImportEntity.of(entity);
        String csv = Csv.BOM + String.join(",", e.columns.stream().map(Column::name).map(Csv::raw).toList()) + "\r\n"
                + String.join(",", e.columns.stream().map(Column::example).map(Csv::raw).toList()) + "\r\n";
        return csv(e.key + "_template.csv", csv.getBytes(StandardCharsets.UTF_8));
    }

    /** Step 1, the dry run: classifies every row and keeps the upload as a validated batch. */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(@RequestParam(required = false) String entity,
                                                        @RequestParam(required = false) MultipartFile file,
                                                        @RequestParam(name = "create_missing_categories", defaultValue = "false") boolean createCategories) {
        ImportEntity e = ImportEntity.of(entity);
        if (file == null || file.isEmpty()) throw ApiException.validation("file", "Choose a CSV or ZIP file to import.");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw ApiException.validation("file", "The file could not be read.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.validate(e, file.getOriginalFilename(), bytes, createCategories));
    }

    /** Step 2: imports the rows that are (still) ok, all or nothing. */
    @PostMapping("/{id}/commit")
    public Map<String, Object> commit(@PathVariable long id) {
        return service.commit(id);
    }

    @GetMapping("/batches")
    public PageResponse<Map<String, Object>> batches(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(required = false) String entity,
                                                     @RequestParam(required = false) String status) {
        return service.list(page, entity, status);
    }

    @GetMapping("/batches/{id}")
    public Map<String, Object> batch(@PathVariable long id) {
        return service.detail(id);
    }

    @GetMapping("/batches/{id}/exceptions")
    public ResponseEntity<byte[]> exceptions(@PathVariable long id) {
        return csv("import_" + id + "_exceptions.csv", service.exceptionsCsv(id));
    }

    private static ResponseEntity<byte[]> csv(String name, byte[] body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
