package com.nibras.pim.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nibras.pim.domain.BaseEntity;
import com.nibras.pim.domain.Report;
import com.nibras.pim.repo.ReportRepository;
import com.nibras.pim.web.dto.FilterParams;
import com.nibras.pim.web.dto.PageResponse;
import com.nibras.pim.web.dto.ReportDto;
import org.hibernate.Hibernate;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saved report definitions: which records, which columns, in which order.
 *
 * <h2>A report is always a query, never a table</h2>
 * Both sources produce a WHERE, an ORDER BY and a LIMIT that reach the database — nothing here ever
 * loads a table and narrows it afterwards. What differs is who writes the query:
 *
 * <ul>
 *   <li><b>Filters</b> — ticked in the builder, handed to the module's own list service, which
 *       turns them into a {@code Specification} plus a {@code Pageable}. Expresses what the list
 *       screen can express.</li>
 *   <li><b>HQL</b> — {@code from Book where type.code = 'art' order by id desc}. Expresses anything
 *       HQL can, and defines its own ordering, so the stored sort is ignored while it is set.</li>
 * </ul>
 *
 * <h2>Rows are always the module's own DTOs</h2>
 * Whichever source ran, each row is rendered by the service that owns it. Those DTOs already nest
 * every lookup as {@code {id, name, code}}, so a resource's type column reads "Article" rather than
 * "art" without this service knowing anything about lookups. Projecting the columns here instead
 * would mean re-implementing that resolution, and getting it subtly different.
 *
 * <h2>Columns and filters are stored as JSON</h2>
 * A report's columns are an ordered list of names and its filters are the same shape the list
 * endpoints already accept. Modelling either as rows would buy nothing — nothing joins to them, and
 * nothing queries them.
 */
@Service
@Transactional
public class ReportService {

    /** Pseudo-column: not a DTO property, it renders the record's cover image. */
    public static final String COVER_COLUMN = "cover";

    private static final int DEFAULT_PAGE_SIZE = 50;

    private final ReportRepository reports;
    private final EntityCatalog catalog;
    private final ReportQueryRunner queries;
    private final ObjectMapper json;

    public ReportService(ReportRepository reports, EntityCatalog catalog,
                         ReportQueryRunner queries, ObjectMapper json) {
        this.reports = reports;
        this.catalog = catalog;
        this.queries = queries;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<ReportDto> list(String moduleKey) {
        List<Report> rows = moduleKey == null || moduleKey.isBlank()
                ? reports.findAllByOrderByModuleKeyAscNameAsc()
                : reports.findByModuleKeyOrderByNameAsc(moduleKey);
        return rows.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ReportDto get(Long id) {
        return toDto(load(id));
    }

    public ReportDto save(Long id, ReportDto dto) {
        Report r = id == null ? new Report() : load(id);
        if (dto.moduleKey() == null || catalog.find(dto.moduleKey()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown module: " + dto.moduleKey());
        }
        if (dto.name() == null || dto.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A report needs a name.");
        }
        r.setName(dto.name().trim());
        r.setModuleKey(dto.moduleKey());
        r.setColumnsJson(write(dto.columns() == null ? List.of() : dto.columns()));
        r.setFiltersJson(write(dto.filters() == null ? Map.of() : dto.filters()));
        r.setQueryText(dto.query() == null || dto.query().isBlank() ? null : dto.query().trim());
        r.setSortField(dto.sortField());
        r.setSortDesc(dto.sortDesc() == null || dto.sortDesc());
        r.setPageSize(dto.pageSize() == null || dto.pageSize() < 1 ? DEFAULT_PAGE_SIZE : dto.pageSize());
        r.setNotes(dto.notes());
        return toDto(reports.saveAndFlush(r));
    }

    public void delete(Long id) {
        Report r = load(id);
        r.setDeletedOn(LocalDateTime.now());
        reports.save(r);
    }

    /**
     * The rows a report shows, one page at a time.
     *
     * <p>Two sources, and which one is in play is decided by whether a query was written:
     *
     * <ul>
     *   <li><b>A query</b> — {@code from Book where type.code = 'art' order by id desc} — is run as
     *       written. It names the rows AND their order, so the stored sort is not consulted;
     *       applying one on top of an {@code order by} could only fight it.</li>
     *   <li><b>Filters</b> otherwise: handed to the module's own list service with the stored sort.
     *       Note that "no filters" is a query too — {@code from Book} with nothing narrowing it —
     *       so this path is never a table scan the report then whittles down; the WHERE, the ORDER
     *       BY and the LIMIT all reach the database.</li>
     * </ul>
     *
     * <p>Either way the rows end up as the module's ordinary DTOs, which is what lets a type column
     * read "Article" rather than "art".
     */
    @Transactional(readOnly = true)
    public PageResponse<Object> run(ReportDto report, int page, Integer size) {
        AbstractCrudService<?, ?> module = catalog.find(report.moduleKey()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This report is built on \"" + report.moduleKey() + "\", which no longer exists."));
        int pageSize = size != null && size > 0 ? size
                : report.pageSize() != null && report.pageSize() > 0 ? report.pageSize() : DEFAULT_PAGE_SIZE;
        int pageIndex = Math.max(0, page);

        return report.query() == null || report.query().isBlank()
                ? runFilters(module, report, pageIndex, pageSize)
                : runQuery(module, report, pageIndex, pageSize);
    }

    private PageResponse<Object> runFilters(AbstractCrudService<?, ?> module, ReportDto report,
                                            int page, int pageSize) {
        FilterParams filters = toFilters(report.filters());
        Pageable pageable = PageRequest.of(page, pageSize, sortOf(report));
        try {
            return erase(module.list(filters, pageable));
        } catch (PropertyReferenceException badSort) {
            // The report was saved sorting on a field that has since been renamed or removed.
            // Showing the rows in the default order beats refusing to open the report at all.
            return erase(module.list(filters,
                    PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "dateCreated"))));
        }
    }

    private PageResponse<Object> runQuery(AbstractCrudService<?, ?> module, ReportDto report,
                                          int page, int pageSize) {
        // One row beyond the page: with an uncountable query that surplus row is the only way to
        // know whether a "next page" exists, and it costs one row rather than a second query.
        // Each row becomes its DTO inside the runner's transaction — see ReportQueryRunner#run.
        ReportQueryRunner.Window window = queries.run(report.query(), page * pageSize, pageSize + 1,
                entity -> toModuleDto(module, report.moduleKey(), entity));
        boolean more = window.rows().size() > pageSize;
        List<Object> rows = more ? List.copyOf(window.rows().subList(0, pageSize)) : window.rows();

        long total = window.total() >= 0
                ? window.total()
                // Uncountable: report what is known to exist so far. "Next" then stays available
                // exactly while a further row was actually seen.
                : (long) page * pageSize + rows.size() + (more ? 1 : 0);
        int totalPages = pageSize == 0 ? 1 : (int) Math.ceil((double) total / pageSize);
        return new PageResponse<>(rows, page, pageSize, total, Math.max(totalPages, page + 1));
    }

    /**
     * One query row as the report module's DTO, refusing rows of the wrong type.
     *
     * <p>The columns come from the report's module, so records of another type would be drawn as a
     * table of blanks — better to name the type the query actually returns.
     */
    private Object toModuleDto(AbstractCrudService<?, ?> module, String moduleKey, BaseEntity entity) {
        String owner = SavedSearchQueryRunner.moduleOf(Hibernate.getClass(entity));
        if (!moduleKey.equals(owner)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This query returns " + (owner == null ? "something that isn't a record" : owner)
                            + ", but the report is set up for " + moduleKey
                            + ". Change the record type above, or the query.");
        }
        return module.dtoOf(entity);
    }

    /** A {@code Page<D>} as a {@code PageResponse<Object>} — the caller can't name D. */
    private PageResponse<Object> erase(Page<?> page) {
        return new PageResponse<>(List.copyOf(page.getContent()), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    private Sort sortOf(ReportDto report) {
        String field = report.sortField() == null || report.sortField().isBlank()
                ? "dateCreated" : report.sortField();
        return Sort.by(Boolean.FALSE.equals(report.sortDesc()) ? Sort.Direction.ASC : Sort.Direction.DESC,
                field);
    }

    /**
     * The stored filter map as the record the list services expect.
     *
     * <p>Converted rather than hand-mapped: {@link FilterParams} has two dozen components, half of
     * them same-typed neighbours, and a hand-written mapping is exactly where a transposed pair
     * compiles cleanly and quietly reports the wrong rows. Unknown keys are ignored so a definition
     * saved against an older filter set still opens.
     */
    private FilterParams toFilters(Map<String, Object> stored) {
        if (stored == null || stored.isEmpty()) {
            return FilterParams.forQuery(null);
        }
        try {
            return json.convertValue(stored, FilterParams.class);
        } catch (IllegalArgumentException unusable) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This report's filters could not be read: " + unusable.getMessage(), unusable);
        }
    }

    @Transactional(readOnly = true)
    public Report load(Long id) {
        return reports.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No such report: " + id));
    }

    // ---------------------------------------------------------------- json

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not store that: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception unreadable) {
            // A definition saved by an older version, or hand-edited. Losing the filters is far
            // better than failing to open the report at all.
            return Map.of();
        }
    }

    private List<String> readList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(raw, new TypeReference<List<String>>() { });
        } catch (Exception unreadable) {
            return List.of();
        }
    }

    private ReportDto toDto(Report r) {
        return new ReportDto(r.getId(), r.getName(), r.getModuleKey(),
                readList(r.getColumnsJson()), read(r.getFiltersJson()), r.getQueryText(),
                r.getSortField(), r.getSortDesc(), r.getPageSize(), r.getNotes(),
                r.getDateCreated(), r.getLastUpdated());
    }
}
