-- Daily and weekly checklists in the database, instead of one Markdown file per week that only
-- reached the server through folder sync. The phone keeps its own copy and syncs it both ways
-- through /api/checklists/sync; the web ticks and annotates the same rows.
--
-- Dates follow the weeks the lists are kept in: a category's or item's start_date is the Monday of
-- the first week it is in, and end_date — set when it is made inactive — the Sunday of the last
-- one. Nothing here is ever deleted: an item taken off the list is ended, a check taken back is
-- cleared (no mark, no note) and stays as a row, so the sync can carry the clearing too.
--
-- uid is the row's identity on every side (the phone creates rows offline, so it cannot wait for
-- an id). A check's uid is its natural key, "<item uid>@<yyyy-MM-dd>", so the same day ticked on
-- the phone and on the web is one row however each side came to it.
--
-- rev comes from checklist_rev, one counter for all three tables: "everything after rev N" is the
-- whole of what a phone that last synced at N has not seen.

CREATE TABLE IF NOT EXISTS checklist_mark_type (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(16)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    symbol      VARCHAR(8)   NOT NULL,
    -- What one mark of this type counts towards a day's completion.
    weight      DECIMAL(4,2) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_checklist_mark_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO checklist_mark_type (code, name, symbol, weight, sort_order) VALUES
    ('done', 'Done', '✓', 1.00, 1),
    ('partial', 'Partly done', '◐', 0.50, 2);

CREATE TABLE IF NOT EXISTS checklist_rev (
    id     INT    NOT NULL,
    value  BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO checklist_rev (id, value) VALUES (1, 0);

CREATE TABLE IF NOT EXISTS checklist_category (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    uid           VARCHAR(64)  NOT NULL,
    -- The group's number: item 3 of group 1 is shown as 1.3.
    code          INT          NOT NULL,
    -- May start with a reminder time ("07:30 Start of day"), which the phone reads.
    name          VARCHAR(255) NOT NULL,
    weekly        BIT(1)       NOT NULL DEFAULT b'0',
    start_date    DATE         NOT NULL,
    end_date      DATE         NULL,
    rev           BIGINT       NOT NULL,
    -- 'web' or 'mobile': which side wrote the row last.
    changed_by    VARCHAR(8)   NOT NULL DEFAULT 'web',
    date_created  DATETIME     NOT NULL,
    last_updated  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_checklist_category_uid (uid),
    KEY idx_checklist_category_rev (rev)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS checklist_item (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    uid           VARCHAR(64)  NOT NULL,
    category_id   BIGINT       NOT NULL,
    code          INT          NOT NULL,
    name          VARCHAR(500) NOT NULL,
    start_date    DATE         NOT NULL,
    end_date      DATE         NULL,
    rev           BIGINT       NOT NULL,
    changed_by    VARCHAR(8)   NOT NULL DEFAULT 'web',
    date_created  DATETIME     NOT NULL,
    last_updated  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_checklist_item_uid (uid),
    KEY idx_checklist_item_rev (rev),
    CONSTRAINT fk_checklist_item_category FOREIGN KEY (category_id) REFERENCES checklist_category (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS checklist_check (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    uid           VARCHAR(100) NOT NULL,
    item_id       BIGINT       NOT NULL,
    -- The day ticked; for a weekly category, the Monday of the week.
    day           DATE         NOT NULL,
    -- NULL: no mark (cleared, or a note on its own).
    mark_type_id  BIGINT       NULL,
    note          TEXT         NULL,
    rev           BIGINT       NOT NULL,
    changed_by    VARCHAR(8)   NOT NULL DEFAULT 'web',
    date_created  DATETIME     NOT NULL,
    last_updated  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_checklist_check_uid (uid),
    UNIQUE KEY uk_checklist_check_item_day (item_id, day),
    KEY idx_checklist_check_rev (rev),
    KEY idx_checklist_check_day (day),
    CONSTRAINT fk_checklist_check_item FOREIGN KEY (item_id) REFERENCES checklist_item (id),
    CONSTRAINT fk_checklist_check_mark FOREIGN KEY (mark_type_id) REFERENCES checklist_mark_type (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
