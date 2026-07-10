package com.nibras.pim.audit;

import com.nibras.pim.domain.BaseEntity;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.Type;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Records every insert / field-level update / (soft) delete of a {@link BaseEntity} into
 * the {@code audit_log} table. Hooked into Hibernate's post-commit-ish flush events and
 * written on the SAME JDBC connection so the audit row commits/rolls back atomically with
 * the change. Lookups and non-{@code BaseEntity} entities are ignored.
 */
@Component
public class AuditListener
        implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    /** Bookkeeping columns we don't want as audit noise (change on every write). */
    private static final Set<String> IGNORED = Set.of("version", "lastUpdated");

    private static final String INSERT_SQL = """
            insert into change_log
              (txn_id, entity, entity_id, op, field, old_value, new_value, changed_at, changed_by)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final EntityManagerFactory emf;

    public AuditListener(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @PostConstruct
    void register() {
        SessionFactoryImplementor sf = emf.unwrap(SessionFactoryImplementor.class);
        EventListenerRegistry registry =
                sf.getServiceRegistry().requireService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_INSERT, this);
        registry.appendListeners(EventType.POST_UPDATE, this);
        registry.appendListeners(EventType.POST_DELETE, this);
    }

    // ---- events ----------------------------------------------------------------------

    @Override
    public void onPostInsert(PostInsertEvent event) {
        if (!(event.getEntity() instanceof BaseEntity)) {
            return;
        }
        write(event.getSession(), simpleName(event.getPersister()), asLong(event.getId()),
                "INSERT", List.of());
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        if (!(event.getEntity() instanceof BaseEntity)) {
            return;
        }
        write(event.getSession(), simpleName(event.getPersister()), asLong(event.getId()),
                "DELETE", List.of());
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (!(event.getEntity() instanceof BaseEntity)) {
            return;
        }
        EntityPersister persister = event.getPersister();
        SharedSessionContractImplementor session = event.getSession();
        String entity = simpleName(persister);
        Long id = asLong(event.getId());

        String[] names = persister.getPropertyNames();
        Type[] types = persister.getPropertyTypes();
        Object[] now = event.getState();
        Object[] was = event.getOldState();

        // Soft delete = an update that stamps deleted_on (null -> non-null). Record as DELETE.
        int deletedIdx = indexOf(names, "deletedOn");
        if (was != null && deletedIdx >= 0 && was[deletedIdx] == null && now[deletedIdx] != null) {
            write(session, entity, id, "DELETE", List.of());
            return;
        }

        List<Change> changes = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            if (IGNORED.contains(names[i]) || types[i].isCollectionType()) {
                continue;   // skip noise + collections (tags) — out of scope for field diffs
            }
            String oldRepr = was == null ? null : repr(types[i], was[i], session);
            String newRepr = repr(types[i], now[i], session);
            if (!Objects.equals(oldRepr, newRepr)) {
                changes.add(new Change(names[i], oldRepr, newRepr));
            }
        }
        if (!changes.isEmpty()) {
            write(session, entity, id, "UPDATE", changes);
        }
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    // ---- writing ---------------------------------------------------------------------

    private void write(SharedSessionContractImplementor session, String entity, Long id,
                       String op, List<Change> changes) {
        if (id == null) {
            return;
        }
        String txn = UUID.randomUUID().toString();
        Timestamp at = Timestamp.valueOf(LocalDateTime.now());
        String by = AuditContext.actor();

        session.doWork(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                if (changes.isEmpty()) {
                    bind(ps, txn, entity, id, op, null, null, null, at, by);
                    ps.executeUpdate();
                } else {
                    for (Change c : changes) {
                        bind(ps, txn, entity, id, op, c.field(), c.oldValue(), c.newValue(), at, by);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        });
    }

    private static void bind(PreparedStatement ps, String txn, String entity, Long id, String op,
                             String field, String oldValue, String newValue, Timestamp at, String by)
            throws java.sql.SQLException {
        ps.setString(1, txn);
        ps.setString(2, entity);
        ps.setLong(3, id);
        ps.setString(4, op);
        ps.setString(5, field);
        ps.setString(6, oldValue);
        ps.setString(7, newValue);
        ps.setTimestamp(8, at);
        ps.setString(9, by);
    }

    // ---- helpers ---------------------------------------------------------------------

    /** Readable string for a property value; for @ManyToOne records the FK id. */
    private static String repr(Type type, Object value, SharedSessionContractImplementor session) {
        if (value == null) {
            return null;
        }
        if (type.isEntityType()) {
            try {
                Object fk = session.getContextEntityIdentifier(value);
                return fk != null ? String.valueOf(fk) : String.valueOf(value);
            } catch (Exception ignored) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    private static String simpleName(EntityPersister persister) {
        String name = persister.getEntityName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    private static int indexOf(String[] names, String target) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(target)) {
                return i;
            }
        }
        return -1;
    }

    private static Long asLong(Object id) {
        return id instanceof Number n ? n.longValue() : null;
    }

    private record Change(String field, String oldValue, String newValue) {
    }
}
