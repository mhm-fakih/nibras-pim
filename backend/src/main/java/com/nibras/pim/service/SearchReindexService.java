package com.nibras.pim.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

/**
 * Rebuilds the whole {@code search_doc} index from every module's records. Kept separate
 * from {@link SearchIndexService} (which the CRUD services depend on) to avoid a bean cycle
 * via {@link EntityCatalog}.
 *
 * <h3>Why this is chunked rather than one transaction</h3>
 * A rebuild is ~50k records and takes about half a minute. As a single transaction, any one record
 * the database refused — an over-long title, a bad encoding, a row added by another tool — rolled
 * back the entire rebuild, and the Admin page could only say "Reindex failed": no count, no record
 * id, no reason. So the work is committed in chunks, a failing chunk is retried row by row to find
 * the record actually at fault, and the rest of the index still gets built. The result carries what
 * was skipped and why, because a rebuild that indexed 51,430 of 51,433 records is a success with a
 * footnote, not a failure.
 */
@Service
public class SearchReindexService {

    private static final Logger log = LoggerFactory.getLogger(SearchReindexService.class);

    /** Records per transaction. Big enough to be quick, small enough that a retry is cheap. */
    private static final int CHUNK = 500;

    /** Per-record failures reported back; beyond this they're only logged. */
    private static final int MAX_REPORTED = 20;

    private final EntityCatalog catalog;
    private final SearchIndexService index;

    public SearchReindexService(EntityCatalog catalog, SearchIndexService index) {
        this.catalog = catalog;
        this.index = index;
    }

    /** What a rebuild did: rows written, rows it couldn't write, and why (first few). */
    public record ReindexReport(int indexed, int skipped, List<String> failures) {
    }

    /**
     * Rebuild the index, skipping (and reporting) any record that can't be written.
     *
     * <p>Deliberately NOT {@code @Transactional} itself: the chunks below each commit on their own,
     * and an outer transaction would defeat that by holding everything open to the end.
     */
    public ReindexReport reindexAll() {
        requireIndexTable();

        int indexed = 0;
        int skipped = 0;
        List<String> failures = new ArrayList<>();

        for (AbstractCrudService<?, ?> service : catalog.all()) {
            String module = service.name();
            List<SearchIndexService.Doc> all;
            try {
                all = service.indexDocs();
            } catch (RuntimeException unreadable) {
                skipped++;
                record(failures, module + ": could not be read — " + rootMessage(unreadable));
                log.warn("Reindex skipped module {}: {}", module, unreadable.toString());
                continue;
            }
            for (int from = 0; from < all.size(); from += CHUNK) {
                List<SearchIndexService.Doc> chunk = all.subList(from, Math.min(from + CHUNK, all.size()));
                try {
                    indexed += index.upsertAll(module, chunk);
                } catch (DataAccessException chunkFailed) {
                    // One record in here is bad. Find out which, and keep the other 499.
                    for (SearchIndexService.Doc d : chunk) {
                        try {
                            indexed += index.upsertAll(module, List.of(d));
                        } catch (DataAccessException recordFailed) {
                            skipped++;
                            record(failures, module + " #" + d.id() + ": " + rootMessage(recordFailed));
                            log.warn("Reindex skipped {} {}: {}", module, d.id(), recordFailed.toString());
                        }
                    }
                }
            }
        }
        return new ReindexReport(indexed, skipped, failures);
    }

    /**
     * Fail early and in plain words when the index table isn't there.
     *
     * <p>This is the failure a database that predates the migrations actually produces, and as a
     * raw {@code SQLSyntaxErrorException} on the first of 51,433 inserts it reached the user as
     * "Reindex failed" with nothing to act on. Said once, up front, it names the fix.
     */
    private void requireIndexTable() {
        try {
            index.count();
        } catch (DataAccessException missing) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR,
                    "The search_doc table is not available in this database, so the index can't be "
                            + "built. It is created by migration V1; check that Flyway ran against "
                            + "the database this instance is pointed at. (" + rootMessage(missing) + ")");
        }
    }

    private static void record(List<String> failures, String message) {
        if (failures.size() < MAX_REPORTED) {
            failures.add(message);
        }
    }

    /** The innermost cause's message — the SQL error, not the six wrappers around it. */
    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String m = root.getMessage();
        return m == null || m.isBlank() ? root.getClass().getSimpleName() : m.strip();
    }

    /** Rows currently in {@code search_doc} — 0 on a fresh install until {@link #reindexAll()} runs. */
    @Transactional(readOnly = true)
    public long indexedCount() {
        return index.count();
    }
}
