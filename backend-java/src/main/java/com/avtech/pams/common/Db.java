package com.avtech.pams.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * The few SQL pieces that differ between PostgreSQL and SQL Server. Everything else in the app is
 * written in SQL both accept: CONCAT instead of ||, CAST instead of ::, CASE WHEN inside SUM/COUNT
 * instead of FILTER, OFFSET .. ROWS FETCH NEXT .. ROWS ONLY instead of LIMIT, LOWER(..) LIKE
 * instead of ILIKE, generated keys instead of RETURNING, and booleans compared with {@link #t()}
 * / {@link #f()} or bound as parameters.
 */
@Component
public class Db {

    private final JdbcTemplate jdbc;
    private final boolean sqlServer;

    public Db(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        try (Connection c = dataSource.getConnection()) {
            this.sqlServer = c.getMetaData().getDatabaseProductName().toLowerCase().contains("sql server");
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read the database type", e);
        }
    }

    public boolean sqlServer() {
        return sqlServer;
    }

    /** Boolean literals: {@code where s.is_active = " + db.t()}. */
    public String t() {
        return sqlServer ? "1" : "true";
    }

    public String f() {
        return sqlServer ? "0" : "false";
    }

    /** Calendar year / month of a date or timestamp expression, as an integer. */
    public String year(String expr) {
        return sqlServer ? "year(" + expr + ")" : "cast(extract(year from " + expr + ") as int)";
    }

    public String month(String expr) {
        return sqlServer ? "month(" + expr + ")" : "cast(extract(month from " + expr + ") as int)";
    }

    /** Whole days from {@code from} to {@code to} (dates). */
    public String daysBetween(String from, String to) {
        return sqlServer ? "datediff(day, " + from + ", " + to + ")" : "(cast(" + to + " as date) - cast(" + from + " as date))";
    }

    /** The date part of a timestamp expression. */
    public String date(String expr) {
        return "cast(" + expr + " as date)";
    }

    /** A JSON value parameter for an insert/update ({@code cast(? as json)} on PostgreSQL). */
    public String jsonParam() {
        return sqlServer ? "?" : "cast(? as json)";
    }

    /** Text type for CAST, e.g. untyped null parameters: {@code cast(? as " + db.text() + ")"}. */
    public String text() {
        return sqlServer ? "nvarchar(4000)" : "varchar(4000)";
    }

    /**
     * Locks matching rows of one table until the transaction ends and returns them:
     * {@code SELECT * ... FOR UPDATE} / {@code WITH (UPDLOCK, ROWLOCK)}.
     */
    public List<Map<String, Object>> lockRows(String table, String where, Object... args) {
        String sql = sqlServer
                ? "select * from " + table + " with (updlock, rowlock, holdlock) where " + where
                : "select * from " + table + " where " + where + " for update";
        return jdbc.queryForList(sql, args);
    }

    /** A lock on a name held until the transaction ends (serialises number generation and similar). */
    public void namedLock(String name) {
        if (sqlServer) {
            jdbc.queryForObject("""
                    declare @r int;
                    exec @r = sp_getapplock @Resource = ?, @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 30000;
                    select @r""", Integer.class, name);
        } else {
            jdbc.queryForList("select pg_advisory_xact_lock(hashtext(?))", name);
        }
    }
}
