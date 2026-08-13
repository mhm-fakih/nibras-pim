package com.nibras.pim.service;

import com.nibras.pim.domain.AuditLog;
import com.nibras.pim.domain.lookup.WorkStatus;
import com.nibras.pim.repo.AuditLogRepository;
import com.nibras.pim.repo.WorkStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which work status a record takes when it is marked complete.
 *
 * <p>Completing a task used to mean different things in different places. The mobile push set the
 * status by a hardcoded {@code findFirstByCode("done")}; the web checkmark and the sprint review
 * stamped {@code completedOn} and left the status untouched entirely — so the same task looked
 * finished on one screen and still open on another, depending on where you had ticked it.
 *
 * <p>One resolver, three callers, and the choice moved out of the code: Settings → Tasks picks the
 * status from the list the task form itself offers. The old {@code "done"} code survives as the
 * fallback, so an instance that never visits that setting keeps behaving exactly as it did.
 */
@Service
public class DoneStatusService {

    /** Setting key: the id of the work status applied on completion. */
    public static final String SETTING_KEY = "task.done-status-id";

    /** Code looked up when the setting is unset — what the mobile push path always hardcoded. */
    public static final String LEGACY_CODE = "done";

    private final SettingService settings;
    private final WorkStatusRepository statuses;
    private final AuditLogRepository audit;

    public DoneStatusService(SettingService settings, WorkStatusRepository statuses,
                             AuditLogRepository audit) {
        this.settings = settings;
        this.statuses = statuses;
        this.audit = audit;
    }

    /**
     * The configured completion status, or null when there is none to apply.
     *
     * <p>Null is a legitimate answer, not a failure: an instance whose statuses don't include a
     * "done" of any kind should stamp {@code completedOn} and leave the status alone rather than
     * invent one. Callers treat null as "no change".
     */
    @Transactional(readOnly = true)
    public WorkStatus resolve() {
        String id = settings.get(SETTING_KEY, "");
        if (id != null && !id.isBlank()) {
            try {
                WorkStatus configured = statuses.findById(Long.parseLong(id.trim())).orElse(null);
                if (configured != null) {
                    return configured;
                }
                // Configured but deleted since. Fall through to the legacy code rather than fail a
                // save over a parameter someone tidied up.
            } catch (NumberFormatException notAnId) {
                // Same: a malformed setting must not break completing a task.
            }
        }
        return statuses.findFirstByCode(LEGACY_CODE).orElse(null);
    }

    /**
     * The status a record had immediately BEFORE it was completed, read back out of the audit trail.
     *
     * <p>Un-completing used to leave the status alone, because nothing in the record remembers what
     * it was before — but the audit log does. Every field-level change is written with its old and
     * new value, and every change from one save shares a {@code txnId}. So the completion is
     * findable ({@code completedOn} going null → non-null), and the {@code status} row from that
     * same transaction carries exactly the value to put back. Associations are recorded as their FK
     * id, so it resolves straight to a row.
     *
     * <h3>What it deliberately won't do</h3>
     * If the status has been changed since the completion, nothing is restored. The recorded
     * "before" value is only trustworthy while the change it describes is still in place; if
     * someone has since moved the task to another status by hand, that is a newer decision than
     * anything this can reconstruct, and overwriting it would be worse than doing nothing.
     *
     * @param entity   audit entity name, e.g. {@code "Task"}
     * @param current  the status the record has right now
     * @return an {@link Outcome} saying whether to change anything, and to what
     */
    @Transactional(readOnly = true)
    public Outcome statusBeforeCompletion(String entity, Long id, WorkStatus current) {
        if (id == null) {
            return Outcome.leaveAlone();
        }
        // The save that completed it: completedOn went from nothing to something.
        AuditLog completion = audit
                .findByEntityAndEntityIdAndFieldOrderByChangedAtDescIdDesc(entity, id, "completedOn")
                .stream()
                .filter(a -> a.getOldValue() == null && a.getNewValue() != null)
                .findFirst()
                .orElse(null);
        if (completion == null || completion.getTxnId() == null) {
            return Outcome.leaveAlone();
        }

        AuditLog statusChange = audit.findByTxnId(completion.getTxnId()).stream()
                .filter(a -> "status".equals(a.getField()))
                .findFirst()
                .orElse(null);
        if (statusChange == null) {
            return Outcome.leaveAlone();   // completing it didn't move the status; nothing to undo
        }

        // Only undo a change that is still in place.
        Long currentId = current == null ? null : current.getId();
        Long appliedId = asId(statusChange.getNewValue());
        if (!java.util.Objects.equals(currentId, appliedId)) {
            return Outcome.leaveAlone();
        }

        Long previousId = asId(statusChange.getOldValue());
        if (previousId == null) {
            return Outcome.restore(null);   // it genuinely had no status before
        }
        return statuses.findById(previousId).map(Outcome::restore).orElseGet(Outcome::leaveAlone);
    }

    private static Long asId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException notAnId) {
            return null;
        }
    }

    /**
     * Whether to restore, and to what. A plain {@code WorkStatus} return couldn't say the difference
     * between "put it back to no status" and "don't touch it" — both would be null.
     */
    public record Outcome(boolean restore, WorkStatus status) {

        static Outcome leaveAlone() {
            return new Outcome(false, null);
        }

        static Outcome restore(WorkStatus status) {
            return new Outcome(true, status);
        }
    }
}
