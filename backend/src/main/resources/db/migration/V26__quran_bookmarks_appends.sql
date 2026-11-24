-- Surahs can be bookmarked too (the phone and the web), and notes sent from the phone are appended
-- to an aya's notes exactly once: each send carries a ref, recorded here when applied, so a send
-- repeated after a lost reply is acknowledged without being appended a second time.
ALTER TABLE quran_surah ADD COLUMN bookmarked TINYINT(1) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS quran_applied (
    ref        VARCHAR(64) NOT NULL,
    applied_on DATETIME    NOT NULL,
    PRIMARY KEY (ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
