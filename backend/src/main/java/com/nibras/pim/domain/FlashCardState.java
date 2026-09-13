package com.nibras.pim.domain;

/** What has been decided about a flash card. */
public enum FlashCardState {

    /** Not looked at yet. Only these are queued, and only a few at a time. */
    NEW,

    /** Done with — retired from the job. */
    REVIEWED,

    /** Come back to it. Listed in full, never capped. */
    REPEAT,

    /** Held onto: the pile to go and edit in the note itself. Listed in full. */
    KEPT;

    public static FlashCardState of(String name) {
        for (FlashCardState s : values()) {
            if (s.name().equalsIgnoreCase(name)) return s;
        }
        return null;
    }
}
