-- Search scope (Search page): "title / summary" and "description / notes" are searched on their own.
-- MySQL's MATCH() must name exactly the columns of one FULLTEXT index, so the existing
-- ft_search (title, body) can only serve "both"; each column gets an index of its own. Guarded so a
-- database that already has them (or a re-run) is left alone.
SET @add_ft_title := IF((SELECT COUNT(*) FROM information_schema.statistics
                         WHERE table_schema = DATABASE() AND table_name = 'search_doc'
                           AND index_name = 'ft_title') = 0,
                        'ALTER TABLE search_doc ADD FULLTEXT KEY ft_title (title)',
                        'SELECT 1');
PREPARE stmt FROM @add_ft_title;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_ft_body := IF((SELECT COUNT(*) FROM information_schema.statistics
                        WHERE table_schema = DATABASE() AND table_name = 'search_doc'
                          AND index_name = 'ft_body') = 0,
                       'ALTER TABLE search_doc ADD FULLTEXT KEY ft_body (body)',
                       'SELECT 1');
PREPARE stmt FROM @add_ft_body;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
