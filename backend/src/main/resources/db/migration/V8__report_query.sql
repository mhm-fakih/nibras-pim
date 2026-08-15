-- A report's rows can come from a query instead of the point-and-click filters.
--
-- The filters express "this table, narrowed by the things the list screen can narrow by". A query
-- expresses anything HQL can say — `from Book where type.code = 'art' order by id desc` — including
-- its own ordering, which is why sort_field/sort_desc are ignored whenever this column is set
-- rather than being merged with it: two sources of truth for the order is a bug waiting to happen.
--
-- Nullable and additive: every report saved before this keeps working unchanged on the filters path.

ALTER TABLE report ADD COLUMN query_text LONGTEXT NULL;
