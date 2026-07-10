package com.nibras.pim.security;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fixed vocabulary of capability keys and the default role→capability matrix. The vocabulary is
 * code (stable); which roles hold which keys lives in the DB ({@code role_permission}) and is edited
 * by admins. ADMIN is implicitly all-powerful and is not constrained by the matrix.
 */
public final class Permissions {

    public static final String CONTENT_WRITE = "content.write";
    public static final String PARAMS_WRITE = "params.write";
    public static final String USERS_MANAGE = "users.manage";
    public static final String TOOLS_ADMIN = "tools.admin";
    public static final String TOOLS_SETTINGS = "tools.settings";
    public static final String TOOLS_AUDIT = "tools.audit";

    /** All assignable keys, in display order (drives the admin matrix screen). */
    public static final List<String> ALL = List.of(
            CONTENT_WRITE, PARAMS_WRITE, USERS_MANAGE, TOOLS_ADMIN, TOOLS_SETTINGS, TOOLS_AUDIT);

    /** Seed matrix used when {@code role_permission} is empty. ADMIN gets everything implicitly. */
    public static final Map<Role, Set<String>> DEFAULTS = Map.of(
            Role.ADMIN, Set.copyOf(ALL),
            Role.DATA_ENTRY, Set.of(CONTENT_WRITE),
            Role.PARAMETERS_DATA_ENTRY, Set.of(PARAMS_WRITE),
            Role.READ_ONLY, Set.of());

    private Permissions() {
    }
}
