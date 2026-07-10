package com.nibras.pim.audit;

/**
 * Per-thread "who is making this change" used by the audit trail. Defaults to
 * {@code "system"}; once authentication exists a request filter can set the real actor.
 */
public final class AuditContext {

    private static final ThreadLocal<String> ACTOR = ThreadLocal.withInitial(() -> "system");

    private AuditContext() {
    }

    public static String actor() {
        return ACTOR.get();
    }

    public static void setActor(String actor) {
        ACTOR.set(actor == null || actor.isBlank() ? "system" : actor);
    }

    public static void clear() {
        ACTOR.remove();
    }
}
