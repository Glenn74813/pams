package com.avtech.pams.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;

/** An INSERT that returns the new row's id on both databases (generated keys, not RETURNING). */
public final class Inserts {

    private Inserts() {
    }

    public static Long insert(JdbcTemplate jdbc, String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, key);
        Map<String, Object> keys = key.getKeys();
        if (keys != null && keys.get("id") instanceof Number n) return n.longValue();
        Number n = key.getKey();
        return n == null ? null : n.longValue();
    }
}
