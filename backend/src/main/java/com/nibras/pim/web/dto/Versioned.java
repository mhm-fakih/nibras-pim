package com.nibras.pim.web.dto;

/**
 * A DTO that carries the row version it was read at, so an update can tell whether the client is
 * writing on top of what it actually saw.
 *
 * <p>Every entity has had an {@code @Version} column since the legacy schema and every DTO has
 * carried the value out to the client — but nothing ever carried it back IN. {@code update()}
 * loaded the row and applied the DTO over it, so Hibernate compared the row against itself and no
 * conflict was possible. Two tabs editing one record, or two controls on one screen writing before
 * the first refetch landed, silently overwrote each other.
 *
 * <p>{@code AbstractCrudService} is bounded on this interface rather than reading {@code version()}
 * reflectively, so a new DTO cannot quietly skip the check: it won't compile.
 */
public interface Versioned {

    /** The version the client last read, or null when it didn't read one (a create). */
    Long version();
}
