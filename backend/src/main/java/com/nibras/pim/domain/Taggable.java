package com.nibras.pim.domain;

import java.util.Set;

/**
 * Implemented by entities that carry relational {@link Tag}s (Task, Goal, Planner, Journal, IndexCard,
 * Writing, Book). Lets the generic CRUD service attach/detach tags from the detail pane without
 * branching per type.
 */
public interface Taggable {
    Set<Tag> getTags();
}
