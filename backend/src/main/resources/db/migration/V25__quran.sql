-- The Quran exegesis and notes module: its own copy of the surahs (writings of type "سورة") and
-- ayat (index cards of type "آية"), made by QuranService.copy() — re-runnable, keyed on the source
-- record ids, so a second copy refreshes the text and exegesis without touching notes written here.
--
-- The module is off by default: enable it in Settings → Modules (its row below), then copy. While it
-- is on, the source surahs and ayat are left out of the Writings and Notes lists. Notes linked to an
-- aya the old way (index_card.entity_code 'N' + record_id = the aya's card id) are not copied: the
-- Quran page shows them live, nested under their aya.

CREATE TABLE IF NOT EXISTS quran_surah (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    number            INT          NOT NULL,
    name              VARCHAR(255) NOT NULL,
    revelation_order  INT          NULL,
    aya_count         INT          NOT NULL DEFAULT 0,
    text              LONGTEXT     NULL,
    notes             LONGTEXT     NULL,
    source_writing_id BIGINT       NULL,
    copied_on         DATETIME     NULL,
    last_updated      DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_quran_surah_number (number),
    UNIQUE KEY uk_quran_surah_source (source_writing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS quran_aya (
    id              BIGINT     NOT NULL AUTO_INCREMENT,
    surah_id        BIGINT     NOT NULL,
    number          INT        NOT NULL,
    -- the aya as written in the source's title (plain), and fully vocalized (its short description)
    text            LONGTEXT   NULL,
    text_vocalized  LONGTEXT   NULL,
    -- the short exegesis (the source's description) and the long notes (its notes)
    exegesis        LONGTEXT   NULL,
    notes           LONGTEXT   NULL,
    bookmarked      TINYINT(1) NOT NULL DEFAULT 0,
    source_card_id  BIGINT     NULL,
    copied_on       DATETIME   NULL,
    last_updated    DATETIME   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_quran_aya_number (surah_id, number),
    UNIQUE KEY uk_quran_aya_source (source_card_id),
    CONSTRAINT fk_quran_aya_surah FOREIGN KEY (surah_id) REFERENCES quran_surah (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- First in the module list (sort 0), off until switched on.
INSERT INTO app_module (module_key, label, code, color, enabled, sort_order, bucket_by_100, bucket_by_type, sync_order)
SELECT 'quran', 'Quran', 'A', '#1b5e20', 0, 0, 0, 0, 99
WHERE NOT EXISTS (SELECT 1 FROM (SELECT module_key FROM app_module) x WHERE x.module_key = 'quran');
