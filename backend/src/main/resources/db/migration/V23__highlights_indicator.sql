-- "Nb. of highlights": an auto-collected daily indicator reading the All highlights report
-- (every ==marked== passage in resources' description and full text, local notes and AI notes).
--
-- indicators.report_source names the report whose count an indicator collects (kind REPORT);
-- "highlights" is the only one so far (HighlightsReportService).
ALTER TABLE indicators ADD COLUMN report_source VARCHAR(64) NULL;

INSERT INTO indicators (version, date_created, last_updated, code, name, description, frequency,
                        scale, category, report_source, bookmarked, auto_collect, priority)
SELECT 0, now(), now(), 'hl_count', 'Nb. of highlights',
       'Passages marked ==like this== in resources (description, full text), local notes and AI notes. See Reports → All highlights.',
       'daily', 'highlights', 'Highlights', 'highlights', b'1', b'1', 1
WHERE NOT EXISTS (SELECT 1 FROM indicators i WHERE i.code = 'hl_count' AND i.deleted_on IS NULL);

-- The PKM programs' usage indicators are monitored too, so they show on the dashboard's trend ticker.
UPDATE indicators SET bookmarked = b'1' WHERE category = 'Apps' AND usage_app IS NOT NULL AND deleted_on IS NULL;
