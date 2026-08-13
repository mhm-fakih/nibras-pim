-- Highlight collector for every content module, so the reading page's click-to-highlight can
-- store the highlighted sentence on any record, not just notes (index_card.main_highlights) and
-- resources (book.highlights). Resources keep `highlights` as their target and use full_text as
-- the source; every other module highlights its description into main_highlights.
--
-- Each add is guarded by an information_schema check (MySQL has no ADD COLUMN IF NOT EXISTS), so
-- this migration is safe to re-run and safe on a database where a column already exists.

SET @add_mh_0 := IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'task'
                   AND column_name = 'main_highlights') = 0,
                'ALTER TABLE task ADD COLUMN main_highlights LONGTEXT NULL',
                'DO 0');
PREPARE s FROM @add_mh_0; EXECUTE s; DEALLOCATE PREPARE s;

SET @add_mh_1 := IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'journal'
                   AND column_name = 'main_highlights') = 0,
                'ALTER TABLE journal ADD COLUMN main_highlights LONGTEXT NULL',
                'DO 0');
PREPARE s FROM @add_mh_1; EXECUTE s; DEALLOCATE PREPARE s;

SET @add_mh_2 := IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'planner'
                   AND column_name = 'main_highlights') = 0,
                'ALTER TABLE planner ADD COLUMN main_highlights LONGTEXT NULL',
                'DO 0');
PREPARE s FROM @add_mh_2; EXECUTE s; DEALLOCATE PREPARE s;

SET @add_mh_3 := IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'book'
                   AND column_name = 'main_highlights') = 0,
                'ALTER TABLE book ADD COLUMN main_highlights LONGTEXT NULL',
                'DO 0');
PREPARE s FROM @add_mh_3; EXECUTE s; DEALLOCATE PREPARE s;

SET @add_mh_4 := IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'index_card'
                   AND column_name = 'main_highlights') = 0,
                'ALTER TABLE index_card ADD COLUMN main_highlights LONGTEXT NULL',
                'DO 0');
PREPARE s FROM @add_mh_4; EXECUTE s; DEALLOCATE PREPARE s;

SET @add_mh_5 := IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'goal'
                   AND column_name = 'main_highlights') = 0,
                'ALTER TABLE goal ADD COLUMN main_highlights LONGTEXT NULL',
                'DO 0');
PREPARE s FROM @add_mh_5; EXECUTE s; DEALLOCATE PREPARE s;

SET @add_mh_6 := IF((SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'writing'
                   AND column_name = 'main_highlights') = 0,
                'ALTER TABLE writing ADD COLUMN main_highlights LONGTEXT NULL',
                'DO 0');
PREPARE s FROM @add_mh_6; EXECUTE s; DEALLOCATE PREPARE s;
