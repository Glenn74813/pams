package com.avtech.pams.supplier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Supplier names for pickers (e.g. recording who delivered a stock lot). The full supplier module
 * still lives in the Laravel app until it is ported.
 */
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final JdbcTemplate jdbc;

    public SupplierController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/options")
    public Map<String, Object> options() {
        return Map.of("data", jdbc.queryForList(
                "select id, name from suppliers where is_active = true order by name"));
    }
}
