package com.nibras.pim.service;

import com.nibras.pim.domain.lookup.LookupEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;

import java.beans.PropertyDescriptor;

/**
 * Applies the admin-configured "default value" for each parameter. Each lookup table can have one
 * default (stored as the setting {@code lookup.default.<table>} = value id, set from the admin page
 * and marked there with a {@code *}). When a record is created with a parametric reference left
 * blank, the default is filled in — so a non-nullable column never ends up empty.
 */
@Service
public class LookupDefaultsService {

    /** Setting key prefix: {@code lookup.default.work_status}, {@code lookup.default.journal_type}, … */
    public static final String PREFIX = "lookup.default.";

    @PersistenceContext
    private EntityManager em;

    private final SettingService settings;

    public LookupDefaultsService(SettingService settings) {
        this.settings = settings;
    }

    /**
     * For every {@link LookupEntity}-typed association on {@code entity} that is currently null,
     * set the table's configured default (if one is configured and still exists).
     */
    public void applyDefaults(Object entity) {
        if (entity == null) {
            return;
        }
        BeanWrapper bw = new BeanWrapperImpl(entity);
        for (PropertyDescriptor pd : bw.getPropertyDescriptors()) {
            Class<?> type = pd.getPropertyType();
            String prop = pd.getName();
            if (type == null || !LookupEntity.class.isAssignableFrom(type)) {
                continue;
            }
            if (!bw.isReadableProperty(prop) || !bw.isWritableProperty(prop)
                    || bw.getPropertyValue(prop) != null) {
                continue;   // not a settable association, or already filled in
            }
            String table = tableName(type);
            if (table == null) {
                continue;
            }
            String idStr = settings.get(PREFIX + table, "");
            if (idStr == null || idStr.isBlank()) {
                continue;   // no default configured for this parameter
            }
            try {
                Object def = em.find(type, Long.valueOf(idStr.trim()));
                if (def != null) {
                    bw.setPropertyValue(prop, def);
                }
            } catch (NumberFormatException ignored) {
                // a malformed setting value just means "no default"
            }
        }
    }

    /** The lookup's table name from its {@code @Table} mapping (matches the admin/setting key). */
    private static String tableName(Class<?> type) {
        Table t = type.getAnnotation(Table.class);
        return t != null && !t.name().isBlank() ? t.name() : null;
    }
}
