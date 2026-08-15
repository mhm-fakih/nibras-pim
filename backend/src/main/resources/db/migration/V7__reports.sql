-- Reports: a saved, named table over any main record type — chosen columns, chosen filters.
--
-- One table, not the dashboard's parent/child pair. A dashboard region carries its own component
-- type and per-region config, so it earns a row each; a report's "regions" are just an ordered list
-- of field names, and a JSON array in one column says that without a join.
--
-- module_key rather than a resources-only table: the picker is generic from the start. Which fields
-- exist for a module is already described in the frontend's entities.ts, and every module's list
-- endpoint already takes the same filter shape — so "resources for now, any record later" costs a
-- column here rather than a rewrite later.

CREATE TABLE IF NOT EXISTS report (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    version       BIGINT       NOT NULL DEFAULT 0,
    name          VARCHAR(255) NOT NULL,
    -- Which record type this reports on: "resources", "tasks", … (AppModule.moduleKey).
    module_key    VARCHAR(64)  NOT NULL,
    -- Ordered JSON array of field names, e.g. ["cover","title","author","type"]. "cover" is a
    -- pseudo-field: not a DTO property, it renders the record's cover image.
    columns_json  LONGTEXT     NULL,
    -- The list filters, as the same JSON the list endpoints already accept (q, statusId, …).
    filters_json  LONGTEXT     NULL,
    sort_field    VARCHAR(64)  NULL,
    sort_desc     BIT(1)       NOT NULL DEFAULT b'1',
    page_size     INT          NOT NULL DEFAULT 50,
    notes         LONGTEXT     NULL,
    date_created  DATETIME     NOT NULL,
    last_updated  DATETIME     NOT NULL,
    deleted_on    DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_report_module (module_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
