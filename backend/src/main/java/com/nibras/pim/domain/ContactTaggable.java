package com.nibras.pim.domain;

import java.util.Set;

/**
 * A record that can be tagged with PEOPLE as well as with tags.
 *
 * <p>The same seam as {@link Taggable}, so the detail pane's quick-attach endpoints can be generic
 * rather than living on one controller. Only {@link Task} implements it today; the legacy schema
 * has `*_contact` join tables for books, goals, notes, journals and writings too, and each carries
 * rows, so this is the interface they would join when their turn comes.
 */
public interface ContactTaggable {

    Set<Contact> getContacts();
}
