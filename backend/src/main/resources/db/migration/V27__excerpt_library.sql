-- The excerpt library: 2–3 pages taken at random from the body of an ebk resource's file (past the
-- front matter, before the indexes), to be summarized later — a one-line summary and details —
-- and, when worth keeping, promoted to a real Excerpt (E). Kept apart from the Excerpts module so
-- thousands of unread samples do not crowd the excerpts written by hand.
--
-- A book tried and found to have no usable file gets a row too (status 'failed'), so it is not
-- tried again on every batch.
CREATE TABLE IF NOT EXISTS book_sample (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    book_id       BIGINT       NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'new',   -- new | summarized | promoted | skipped | failed
    source_repo   INT          NULL,
    source_file   VARCHAR(1024) NULL,                    -- path inside the record's folder
    format        VARCHAR(8)   NULL,                     -- pdf | djvu | epub | mobi | chm | azw3
    page_from     INT          NULL,
    page_to       INT          NULL,
    total_pages   INT          NULL,                     -- for an ebook: the position, in ‰ of its text
    pdf_file      VARCHAR(512) NULL,                     -- the sample pages as a PDF, in the library folder
    text          LONGTEXT     NULL,
    has_text      TINYINT(1)   NOT NULL DEFAULT 0,
    summary       VARCHAR(255) NULL,
    description   LONGTEXT     NULL,
    excerpt_id    BIGINT       NULL,
    error         VARCHAR(512) NULL,
    created_on    DATETIME     NOT NULL,
    summarized_on DATETIME     NULL,
    PRIMARY KEY (id),
    KEY ix_book_sample_book (book_id),
    KEY ix_book_sample_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
