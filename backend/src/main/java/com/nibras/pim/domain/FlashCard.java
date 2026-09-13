package com.nibras.pim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One passage lifted out of a note's description, to be read back once and decided on.
 *
 * <p>The set is a frozen extract — see {@code V9__flash_cards.sql}. Notes written or deleted after
 * the gathering do not add or remove cards, which is what makes this a job with an end rather than
 * a feed. The passage text is a copy, so a card keeps saying what it said when it was gathered even
 * if the note behind it changes; {@link #noteId} is provenance shown as {@code N13459}, not a
 * foreign key.
 */
@Entity
@Table(name = "flash_card")
@SQLRestriction("deleted_on is null")
@Getter
@Setter
public class FlashCard extends BaseEntity {

    /** Content hash of the passage, from the gathering job. Unique; makes the load idempotent. */
    @Column(name = "card_key", nullable = false, length = 32)
    private String cardKey;

    /** The {@code index_card} this came from — shown to the reader, never joined on. */
    @Column(name = "note_id", nullable = false)
    private Long noteId;

    @Column(name = "written_on")
    private LocalDate writtenOn;

    @Column(name = "passage", nullable = false, columnDefinition = "longtext")
    private String passage;

    /** Laid out right-to-left; decided at gather time from the first strong letter. */
    @Column(name = "rtl", nullable = false)
    private Boolean rtl = false;

    /** 'r' religious, 'p' psychological, 'o' neither. */
    @Column(name = "topic", nullable = false, length = 1)
    private String topic = "o";

    /** The bank's own order: favoured topics first, recent first within them. */
    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "state", nullable = false, length = 16)
    private String state = FlashCardState.NEW.name();

    @Column(name = "decided_on")
    private LocalDateTime decidedOn;

    /** Named by its source record, the way the card itself is headed. */
    @Override
    public String feedTitle() {
        return "N" + noteId;
    }

    @Override
    public String feedDescription() {
        return passage;
    }
}
