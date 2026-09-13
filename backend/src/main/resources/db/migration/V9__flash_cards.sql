-- Flash cards: passages lifted out of long notes, to be read back and decided on once.
--
-- A ONE-TIME gathering, deliberately. The rows are a frozen extract taken from index_card at a
-- single moment; notes written or deleted afterwards do not change them. That is what makes this a
-- finite job with an end rather than a feed that refills itself — the point is to get through the
-- material once, not to keep up with it.
--
-- The passage text is COPIED rather than referenced. A card must keep saying what it said when it
-- was gathered, even if the note behind it is later edited or deleted; note_id is provenance (shown
-- as N13459), not a foreign key, and is deliberately not constrained as one.
--
-- card_key is the passage's content hash from the gathering job. It exists so the load can be run
-- again without duplicating anything, and so a decision made on the phone survives the extract
-- being regenerated from the same note.

CREATE TABLE IF NOT EXISTS flash_card (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    version       BIGINT       NOT NULL DEFAULT 0,
    -- Content hash of the passage, from the gathering job. Makes the load idempotent.
    card_key      VARCHAR(32)  NOT NULL,
    -- The index_card this passage came out of. Provenance only — no FK, see above.
    note_id       BIGINT       NOT NULL,
    -- The source note's own date, shown beside the record reference.
    written_on    DATE         NULL,
    passage       LONGTEXT     NOT NULL,
    -- Laid out right-to-left. Decided at gather time from the passage's first strong letter.
    rtl           BIT(1)       NOT NULL DEFAULT b'0',
    -- 'r' religious, 'p' psychological, 'o' neither.
    topic         VARCHAR(1)   NOT NULL DEFAULT 'o',
    -- The bank's own order: favoured topics first, recent first within them.
    position      INT          NOT NULL,
    -- NEW / REVIEWED / REPEAT / KEPT.
    state         VARCHAR(16)  NOT NULL DEFAULT 'NEW',
    decided_on    DATETIME     NULL,
    date_created  DATETIME     NOT NULL,
    last_updated  DATETIME     NOT NULL,
    deleted_on    DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flash_card_key (card_key),
    -- The queue's own query: the next N undecided cards in bank order.
    KEY idx_flash_card_state_pos (state, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
