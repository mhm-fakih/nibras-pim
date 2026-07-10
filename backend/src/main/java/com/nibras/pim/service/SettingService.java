package com.nibras.pim.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thin key/value access to the additive {@code app_setting} table (skey -> svalue). Backs app-level
 * config the user edits at runtime — e.g. the three repository root paths and the file-explorer command.
 * Defaults live in the callers, so the table can start empty.
 */
@Service
public class SettingService {

    private final JdbcTemplate jdbc;

    public SettingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Value for {@code key}, or {@code def} if unset/blank. */
    @Transactional(readOnly = true)
    public String get(String key, String def) {
        String v = jdbc.query("select svalue from app_setting where skey = ? limit 1",
                rs -> rs.next() ? rs.getString(1) : null, key);
        return v == null || v.isBlank() ? def : v;
    }

    /** Upsert a setting value (unique skey makes this a clean ON DUPLICATE KEY upsert). */
    @Transactional
    public void set(String key, String value) {
        jdbc.update("insert into app_setting (skey, svalue, updated_at) values (?, ?, now()) "
                + "on duplicate key update svalue = values(svalue), updated_at = now()", key, value);
    }
}
