-- Give each course the knotes vocabulary that points at it.
--
-- Until now the mapping from a note's `#A.salat` to course 9208 lived in a markdown file inside the
-- notes folder. That made the subject view depend on a folder that may not be synced to the machine
-- running the backend — and when it was absent the view silently degraded to department letters.
-- The courses are already in the database; their tags belong there too.
--
-- Format, deliberately the same as the tag bank's own: the CANONICAL tag first, then aliases,
-- comma-separated. `A.salat, salat, صلاة, تعقيبات`. Keeping the file's convention means the file
-- stays a readable source for this column rather than a competing truth.
--
-- The column only. The 242 rows of vocabulary are a separate, re-runnable data script
-- (dist/sql/course-tags-<date>.sql) because it is content the user curates, not schema.
SET @add := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'course' AND column_name = 'tags') = 0,
    'ALTER TABLE course ADD COLUMN tags VARCHAR(255) NULL',
    'SELECT 1'));
PREPARE stmt FROM @add;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
