package com.nibras.pim.domain;

/**
 * Shared accessors the mobile-sync write actions ({@code mobilePush}, {@code mobilePushHighlight},
 * {@code mobilePushComplete}) rely on across every syncable entity: appending to {@code notes} and
 * clearing the {@code bookmarked} flag once a record has been actioned on the phone. Every entity in
 * the sync set (Task, Goal, Planner, Journal, IndexCard, Writing, Book) has both columns, so they all
 * implement this — letting the service handle them uniformly instead of branching per type.
 */
public interface SyncMutable {
    String getNotes();

    void setNotes(String notes);

    Boolean getBookmarked();

    void setBookmarked(Boolean bookmarked);
}
