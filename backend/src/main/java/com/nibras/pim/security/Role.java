package com.nibras.pim.security;

/**
 * The four roles for a small-team install. Mapped to Spring authorities as {@code ROLE_<name>}.
 *
 * <ul>
 *   <li>{@code ADMIN} — everything, including user management.</li>
 *   <li>{@code DATA_ENTRY} — create/update/delete content records (planners, journals, tasks, …).</li>
 *   <li>{@code PARAMETERS_DATA_ENTRY} — create/update/delete lookup/parameter data
 *       (types, statuses, departments, courses, contexts, tags, settings).</li>
 *   <li>{@code READ_ONLY} — read-only across the app.</li>
 * </ul>
 */
public enum Role {
    ADMIN,
    DATA_ENTRY,
    PARAMETERS_DATA_ENTRY,
    READ_ONLY;

    /** Spring Security authority name for this role. */
    public String authority() {
        return "ROLE_" + name();
    }
}
