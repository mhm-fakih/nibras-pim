-- The official calendar of Iran, day by day: each day's Shamsi date and its Hijri date as officially
-- set (by sighting, which the app's arithmetic Hijri can miss by a day), whether it is a holiday,
-- and its occasions in the original Persian with an Arabic translation. Loaded from the JSON files
-- in resources/calendar (built from the University of Tehran's yearly PDF by tools/iran-calendar).
CREATE TABLE IF NOT EXISTS calendar_day (
    day          DATE        NOT NULL,
    shamsi_year  INT         NOT NULL,
    shamsi_month INT         NOT NULL,
    shamsi_day   INT         NOT NULL,
    hijri_year   INT         NULL,
    hijri_month  INT         NULL,
    hijri_day    INT         NULL,
    holiday      TINYINT(1)  NOT NULL DEFAULT 0,
    source       VARCHAR(255) NULL,
    PRIMARY KEY (day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS calendar_occasion (
    id       BIGINT     NOT NULL AUTO_INCREMENT,
    day      DATE       NOT NULL,
    ord      INT        NOT NULL,
    text_fa  TEXT       NOT NULL,
    text_ar  TEXT       NULL,
    holiday  TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_calendar_occasion (day, ord),
    KEY ix_calendar_occasion_day (day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
