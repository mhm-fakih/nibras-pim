package com.nibras.pim.service;

import com.nibras.pim.domain.BaseEntity;
import com.nibras.pim.domain.Tag;
import com.nibras.pim.repo.BaseRepository;
import com.nibras.pim.web.dto.FilterParams;
import com.nibras.pim.web.dto.LookupDto;
import com.nibras.pim.web.dto.Versioned;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generic CRUD for the seven Nibras entities. Subclasses supply only the mapping
 * to/from their DTO, the searchable column names, and the available lookups.
 */
@Transactional
public abstract class AbstractCrudService<E extends BaseEntity, D extends Versioned> {

    private static final Logger log = LoggerFactory.getLogger(AbstractCrudService.class);

    protected final BaseRepository<E> repo;

    @PersistenceContext
    protected EntityManager em;

    /** Field-injected to avoid touching every subclass constructor (and a bean cycle). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SearchIndexService searchIndex;

    /** Field-injected (optional) so create() can fill admin-configured parameter defaults. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LookupDefaultsService lookupDefaults;

    // @Lazy injects a proxy so the AttachmentService -> EntityCatalog -> services cycle is broken.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private AttachmentService attachments;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TagService tagService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RepositoryService repositories;

    // @Lazy for the same reason as AttachmentService: FulltextService depends on it (via
    // EntityCatalog) transitively, so an eager injection here would be a bean cycle.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private FulltextService fulltext;

    /** Reads the two bookmark side-effect switches — see {@link #syncCheckoutLink}. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SettingService settings;

    protected AbstractCrudService(BaseRepository<E> repo) {
        this.repo = repo;
    }

    // ---- to be implemented per entity -------------------------------------------------

    /** Stable URL slug, e.g. "tasks", "resources". */
    public abstract String name();

    protected abstract D toDto(E entity);

    protected abstract E toNew(D dto);

    protected abstract void apply(D dto, E entity);

    /** Columns the free-text {@code q} filter searches (LIKE, OR-combined). */
    protected abstract List<String> searchFields();

    /** Dropdown data for forms: e.g. {"statuses": [...], "types": [...], "tags": [...]}. */
    public abstract Map<String, List<LookupDto>> lookups();

    // ---- generic operations -----------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<D> list(FilterParams f, Pageable pageable) {
        return repo.findAll(spec(f), pageable).map(this::toDto);
    }

    /**
     * This module's DTO for one of its entities.
     *
     * <p>Exists for the report runner: a report query hands back entities, and they have to be
     * rendered exactly as the list endpoint would render them — same nested lookups, same computed
     * fields — or a report and a list of the same records would disagree.
     */
    @SuppressWarnings("unchecked")
    public D dtoOf(BaseEntity entity) {
        return toDto((E) entity);
    }

    @Transactional(readOnly = true)
    public D get(Long id) {
        return toDto(load(id));
    }

    public D create(D dto) {
        E entity = toNew(dto);
        if (lookupDefaults != null) {
            lookupDefaults.applyDefaults(entity);   // fill blank parameters with their admin defaults
        }
        entity = repo.saveAndFlush(entity);
        reindex(entity);
        syncCheckoutLink(entity, false);   // a brand-new record was never bookmarked before now
        return toDto(entity);
    }

    public D update(Long id, D dto) {
        E entity = load(id);
        requireCurrentVersion(entity, dto);
        boolean wasBookmarked = isBookmarked(entity);
        apply(dto, entity);
        entity = repo.saveAndFlush(entity);
        reindex(entity);
        syncCheckoutLink(entity, wasBookmarked);
        return toDto(entity);
    }

    /**
     * Refuse a write built on a stale copy of the record.
     *
     * <p>Hibernate's own {@code @Version} check only covers the window INSIDE this transaction: it
     * issues {@code update … where id = ? and version = ?} with the version it just loaded, so it
     * catches a concurrent commit while we work. It cannot catch the case that actually bites — a
     * client that read the record minutes ago, or seconds ago and before a refetch landed, and is
     * now sending every field back including the ones someone else changed. The row and our copy of
     * it agree perfectly; it is the CLIENT's copy that is out of date.
     *
     * <p>So the version the client read is compared against the row's current one, and a mismatch
     * is a 409 rather than a silent overwrite. A DTO with no version is left alone — that is a
     * caller with no baseline to be stale against, not a caller skipping the check.
     */
    private void requireCurrentVersion(E entity, D dto) {
        Long seen = dto == null ? null : dto.version();
        Long actual = entity.getVersion();
        if (seen == null || actual == null || seen.equals(actual)) {
            return;
        }
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                "This record was changed by someone else (or in another tab) since you opened it. "
                        + "Reload it and reapply your change.");
    }

    /**
     * Set or clear a ManyToOne association by id, generically (e.g. link a journal to a goal).
     * The association's entity type is taken from the property, so it works for any FK field.
     */
    @org.springframework.transaction.annotation.Transactional
    public D setReference(Long id, String field, Long targetId) {
        E entity = load(id);
        org.springframework.beans.BeanWrapper bw = new org.springframework.beans.BeanWrapperImpl(entity);
        if (!bw.isWritableProperty(field)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "No such field: " + field);
        }
        Class<?> type = bw.getPropertyType(field);
        Object ref = targetId == null ? null : em.getReference(type, targetId);
        bw.setPropertyValue(field, ref);
        E saved = repo.saveAndFlush(entity);
        reindex(saved);
        return toDto(saved);
    }

    /** Soft delete: stamp {@code deleted_on}; the @SQLRestriction hides it thereafter. */
    public void delete(Long id) {
        E entity = load(id);
        boolean wasBookmarked = isBookmarked(entity);
        entity.setDeletedOn(LocalDateTime.now());
        repo.save(entity);
        if (searchIndex != null) {
            searchIndex.delete(name(), id);   // drop soft-deleted rows from search
        }
        if (wasBookmarked && repositories != null) {
            repositories.unlinkCheckout(name(), id, typeCodeOf(entity));
        }
    }

    /** Refresh this record's search-index entry after it was changed by other means (e.g. a merge). */
    @Transactional
    public void reindexRecord(Long id) {
        reindex(load(id));
    }

    /** Keep the full-text index in sync after a create/update. */
    private void reindex(E entity) {
        if (searchIndex != null) {
            searchIndex.upsert(name(), entity.getId(), entity.feedTitle(), entity.feedDescription());
        }
    }

    /** All non-deleted entities, used to rebuild the search index. */
    @Transactional(readOnly = true)
    public List<E> allForIndex() {
        return repo.findAll();
    }

    /**
     * Every record reduced to what the search index stores.
     *
     * <p>Flattened here, inside the read transaction, rather than by the caller: the rebuild writes
     * in its own short transactions, so by the time it iterates, the entities are detached and any
     * lazy field {@code feedTitle()} reached for would throw.
     */
    @Transactional(readOnly = true)
    public List<SearchIndexService.Doc> indexDocs() {
        return repo.findAll().stream()
                .map(e -> new SearchIndexService.Doc(e.getId(), e.feedTitle(), e.feedDescription()))
                .toList();
    }

    /** Just enough of a record to locate its repository folder and name it in a list. */
    public record RecordRef(String module, long id, String typeCode, String title) {
    }

    /**
     * Every non-deleted record as an id + type code + title.
     *
     * <p>The type code is the part a caller can't work out for itself: it decides whether the
     * record's folder nests under a type bucket, and only the owning service knows where the type
     * lives on its entity. Read in one transaction so {@code feedTitle()} can touch lazy fields.
     */
    @Transactional(readOnly = true)
    public List<RecordRef> recordRefs() {
        return repo.findAll().stream()
                .map(e -> new RecordRef(name(), e.getId(), typeCodeOf(e), e.feedTitle()))
                .toList();
    }

    /** Where a search term was found in a record: the field, and the text around it. */
    public record FieldMatch(String field, String snippet) {
    }

    /**
     * Long-text fields worth quoting back even when the module doesn't search them.
     *
     * <p>A list row shows a title, so "this record matched" without saying where is unhelpful
     * exactly when the match is somewhere long — a scanned book's full text, a note left months
     * ago. These are checked in addition to {@link #searchFields()} and skipped where the entity
     * has no such property.
     */
    private static final List<String> CONTEXT_FIELDS =
            List.of("description", "fullText", "notes", "highlights", "shortDescription");

    /**
     * For each of [ids], where in the record the query appears.
     *
     * <p>Looks for the query **as typed**, because that is what listed the record: the filter is a
     * single {@code LIKE %q%}, not a per-word match, so pointing at where one common word happened
     * to land would explain a match that didn't happen that way.
     *
     * <p>Records are fetched in one query and fields read reflectively, so this stays generic
     * across modules; a record whose text no longer contains the term (it changed between the two
     * requests) simply contributes nothing.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<FieldMatch>> matchContext(String q, List<Long> ids) {
        if (q == null || q.isBlank() || ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<String> fields = new ArrayList<>(searchFields());
        CONTEXT_FIELDS.forEach(f -> {
            if (!fields.contains(f)) {
                fields.add(f);
            }
        });

        Map<Long, List<FieldMatch>> out = new LinkedHashMap<>();
        for (E entity : repo.findAllById(ids)) {
            org.springframework.beans.BeanWrapper bw = new org.springframework.beans.BeanWrapperImpl(entity);
            List<FieldMatch> found = new ArrayList<>();
            for (String field : fields) {
                if (!bw.isReadableProperty(field)) {
                    continue;
                }
                Object value = bw.getPropertyValue(field);
                if (!(value instanceof String text) || text.isBlank()) {
                    continue;
                }
                String snippet = Snippets.around(text, q);
                if (snippet != null) {
                    found.add(new FieldMatch(field, snippet));
                }
            }
            if (!found.isEmpty()) {
                out.put(entity.getId(), found);
            }
        }
        return out;
    }

    /** All bookmarked (non-deleted) entities, newest first — for the unified bookmarks list. */
    @Transactional(readOnly = true)
    public List<E> bookmarkedEntities() {
        Specification<E> bm = (root, q, cb) ->
                has(root, "bookmarked") ? cb.isTrue(root.get("bookmarked")) : cb.disjunction();
        return repo.findAll(bm, Sort.by(Sort.Direction.DESC, "dateCreated"));
    }

    /** Entities matching a filter (newest first) — used by the static-site generator. */
    @Transactional(readOnly = true)
    public List<E> entitiesForFilter(FilterParams f) {
        return repo.findAll(spec(f), Sort.by(Sort.Direction.DESC, "dateCreated"));
    }

    /** Most-recent BOOKMARKED items for the RSS feed. */
    @Transactional(readOnly = true)
    public List<E> latest(int max) {
        Pageable pageable = PageRequest.of(0, max, Sort.by(Sort.Direction.DESC, "dateCreated"));
        Specification<E> bookmarked = (root, q, cb) ->
                has(root, "bookmarked") ? cb.isTrue(root.get("bookmarked")) : cb.conjunction();
        return repo.findAll(bookmarked, pageable).getContent();
    }

    @Transactional(readOnly = true)
    public List<Object[]> creationCounts(LocalDateTime from, LocalDateTime to) {
        return repo.countByCreationDay(from, to);
    }

    /** Per-day last-updated counts — the Insights page's "updated" histogram. */
    @Transactional(readOnly = true)
    public List<Object[]> updateCounts(LocalDateTime from, LocalDateTime to) {
        return repo.countByUpdateDay(from, to);
    }

    /** Records created in [from, to) — for dashboard KPIs. */
    @Transactional(readOnly = true)
    public long countCreatedBetween(LocalDateTime from, LocalDateTime to) {
        return repo.count((root, q, cb) -> cb.between(root.get("dateCreated"), from, to));
    }

    /** Records last-updated in [from, to) (0 for entities without a lastUpdated column). */
    @Transactional(readOnly = true)
    public long countUpdatedBetween(LocalDateTime from, LocalDateTime to) {
        return repo.count((root, q, cb) ->
                has(root, "lastUpdated") ? cb.between(root.get("lastUpdated"), from, to) : cb.disjunction());
    }

    /** Bookmarked records (0 for entities without a bookmarked column). */
    @Transactional(readOnly = true)
    public long countBookmarked() {
        return repo.count((root, q, cb) ->
                has(root, "bookmarked") ? cb.isTrue(root.get("bookmarked")) : cb.disjunction());
    }

    protected E load(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(name() + " " + id + " not found"));
    }

    // ---- tagging (detail-pane, view mode) ---------------------------------------------

    /** Attaches an existing tag (by id) to a record. No-op if the entity isn't taggable. */
    @Transactional
    public D addTag(Long id, Long tagId) {
        E e = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("No " + name() + " " + id));
        if (e instanceof com.nibras.pim.domain.Taggable t) {
            t.getTags().add(em.getReference(Tag.class, tagId));
            repo.save(e);
        }
        return toDto(e);
    }

    /** Attaches a tag by name to a record, creating the tag if it doesn't exist yet. */
    @Transactional
    public D addTagByName(Long id, String tagName) {
        Tag tag = tagService.findOrCreate(tagName);
        return addTag(id, tag.getId());
    }

    /** Detaches a tag from a record. */
    @Transactional
    public D removeTag(Long id, Long tagId) {
        E e = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("No " + name() + " " + id));
        if (e instanceof com.nibras.pim.domain.Taggable t) {
            t.getTags().removeIf(tg -> tg.getId().equals(tagId));
            repo.save(e);
        }
        return toDto(e);
    }

    /**
     * The date property the "on day" filter uses. Defaults to {@code dateCreated} ("created that
     * day"); journals/planners override it with their own day field so the day's record matches.
     */
    protected String dayField() {
        return "dateCreated";
    }

    // ---- record folder (repositories 1/2/3) -------------------------------------------

    /** The record's type code, for type-bucketed folders. Overridden by services whose entity has a type. */
    protected String typeCodeOf(E entity) {
        return null;
    }

    /** Open this record's folder in repository {@code repoNum} (1/2/3) in the desktop file manager. */
    public void openFolder(Long id, int repoNum) {
        E e = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("No " + name() + " " + id));
        repositories.openFolder(repoNum, name(), id, typeCodeOf(e));
    }

    /** This record's type code (for type-bucketed repo folders); null if it has no type. */
    @Transactional(readOnly = true)
    public String typeCodeFor(Long id) {
        E e = repo.findById(id).orElse(null);
        return e == null ? null : typeCodeOf(e);
    }

    /** Whether {@code entity} has a (readable) {@code bookmarked} property currently set true. */
    private boolean isBookmarked(E entity) {
        org.springframework.beans.BeanWrapper bw = new org.springframework.beans.BeanWrapperImpl(entity);
        return bw.isReadableProperty("bookmarked")
                && Boolean.TRUE.equals(bw.getPropertyValue("bookmarked"));
    }

    /** Create the checkout-folder symlink when a record is bookmarked. Off unless switched on. */
    public static final String SETTING_LINK = "bookmark.symlink";
    /** Open the record's checkout text when it is bookmarked. Off unless switched on. */
    public static final String SETTING_OPEN = "bookmark.open-checkout";

    /** A bookmark side-effect switch. Both default OFF — bookmarking is a note to self, and on its
     *  own it should not touch the filesystem or launch anything. */
    private boolean bookmarkSideEffect(String key) {
        return settings != null && Boolean.parseBoolean(settings.get(key, "false"));
    }

    /**
     * The two things that can happen when a record's bookmark is turned on: a symlink into the
     * checkout folder ({@link #SETTING_LINK}), and the record's checkout text being opened in the
     * desktop editor ({@link #SETTING_OPEN}). Both are off by default and switched independently in
     * Settings. No-op for entities without a {@code bookmarked} column.
     *
     * <p>Removal is deliberately NOT gated. A symlink made while the setting was on must not
     * outlive the bookmark that justified it — otherwise switching the feature off would strand
     * every existing link. Deleting one that isn't there is a no-op anyway.
     *
     * <p>The export is passed an explicit {@code open} rather than {@code null}: the two-argument
     * form falls back to the {@code export.open} setting, which defaults to TRUE and is meant for
     * the manual Export button. Inheriting it is exactly how bookmarking came to pop an editor
     * open without anyone asking it to.
     */
    private void syncCheckoutLink(E entity, boolean wasBookmarked) {
        if (repositories == null) {
            return;
        }
        boolean nowBookmarked = isBookmarked(entity);
        if (nowBookmarked == wasBookmarked) {
            return;
        }
        if (!nowBookmarked) {
            repositories.unlinkCheckout(name(), entity.getId(), typeCodeOf(entity));
            return;
        }

        boolean link = bookmarkSideEffect(SETTING_LINK);
        boolean open = bookmarkSideEffect(SETTING_OPEN);
        if (!link && !open) {
            return;
        }
        if (link) {
            repositories.linkCheckout(name(), entity.getId(), typeCodeOf(entity));
        }
        // Written whichever switch is on: the symlink would otherwise point at a folder with no
        // checkout file in it, and there is nothing to open if the text was never written.
        if (fulltext != null && fulltext.supports(name())) {
            try {
                fulltext.export(name(), entity.getId(), open);
            } catch (RuntimeException e) {
                log.warn("Could not write checkout export for {} {}: {}", name(), entity.getId(), e.toString());
            }
        }
    }

    // ---- mapping helpers --------------------------------------------------------------

    /** Lazily attaches a managed reference for a ManyToOne lookup by id (null clears it). */
    protected <T> T ref(Class<T> type, Long id) {
        return id == null ? null : em.getReference(type, id);
    }

    protected Set<Tag> resolveTags(List<Long> tagIds) {
        Set<Tag> tags = new LinkedHashSet<>();
        if (tagIds != null) {
            for (Long id : tagIds) {
                tags.add(em.getReference(Tag.class, id));
            }
        }
        return tags;
    }

    protected static LookupDto lookup(com.nibras.pim.domain.lookup.LookupEntity e) {
        return new LookupDto(e.getId(), e.getName(), e.getCode(), e.getStyle());
    }

    /** Department isn't a LookupEntity (no style column), so it gets its own projection. */
    protected static LookupDto deptLookup(com.nibras.pim.domain.lookup.Department d) {
        return new LookupDto(d.getId(), d.getName(), d.getCode(), null);
    }

    /** Course projection (display title is its summary). */
    protected static LookupDto courseLookup(com.nibras.pim.domain.lookup.Course c) {
        return new LookupDto(c.getId(), c.feedTitle(), c.getCode(), null);
    }

    /** Whether the record has a cover image on disk (so lists can skip a 404 probe). */
    protected boolean coverExists(Long id) {
        return attachments != null && id != null && attachments.cover(name(), id).isPresent();
    }

    /** All (non-deleted) rows of a lookup table as dropdown options, ordered by name. */
    protected <T extends com.nibras.pim.domain.lookup.LookupEntity> List<LookupDto> options(Class<T> type) {
        return em.createQuery("select e from " + type.getName() + " e order by e.name", type)
                .getResultList().stream().map(AbstractCrudService::lookup).toList();
    }

    /** All (non-deleted) tags as dropdown options. */
    protected List<LookupDto> tagOptions() {
        return em.createQuery("select t from " + Tag.class.getName() + " t order by t.name", Tag.class)
                .getResultList().stream()
                .map(t -> new LookupDto(t.getId(), t.getName(), null, null))
                .toList();
    }

    /** All (non-deleted) departments as dropdown options, ordered by code. */
    protected List<LookupDto> departmentOptions() {
        return em.createQuery(
                        "select d from " + com.nibras.pim.domain.lookup.Department.class.getName()
                                + " d order by d.code",
                        com.nibras.pim.domain.lookup.Department.class)
                .getResultList().stream()
                .map(d -> new LookupDto(d.getId(), d.getName(), d.getCode(), null))
                .toList();
    }

    /** Goal projection for a "link to goal" field (goals aren't LookupEntities). */
    protected static LookupDto goalLookup(com.nibras.pim.domain.Goal g) {
        return new LookupDto(g.getId(), g.feedTitle(), g.getCode(), null);
    }

    /** Bookmarked goals as dropdown options (alphabetical) — a "link to goal" field only offers active goals. */
    protected List<LookupDto> bookmarkedGoalOptions() {
        return em.createQuery(
                        "select g from " + com.nibras.pim.domain.Goal.class.getName()
                                + " g where g.bookmarked = true",
                        com.nibras.pim.domain.Goal.class)
                .getResultList().stream()
                .map(AbstractCrudService::goalLookup)
                // Sort on the display title (feedTitle: title falling back to summary), case-folded.
                .sorted(java.util.Comparator.comparing(
                        o -> o.name() == null ? "" : o.name().toLowerCase()))
                .toList();
    }

    // ---- generic specification --------------------------------------------------------

    protected Specification<E> spec(FilterParams f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();

            if (f.q() != null && !f.q().isBlank()) {
                String q = f.q().trim();
                // A purely numeric query means "find the record with this id", not "find this
                // number inside the text fields" — e.g. typing "482" jumps straight to record #482
                // instead of a LIKE scan that would rarely match anything.
                Long asId = q.matches("\\d+") ? tryParseId(q) : null;
                if (asId != null) {
                    ps.add(cb.equal(root.get("id"), asId));
                } else {
                    String like = "%" + q.toLowerCase() + "%";
                    List<Predicate> or = new ArrayList<>();
                    for (String field : searchFields()) {
                        if (has(root, field)) {
                            or.add(cb.like(cb.lower(root.get(field).as(String.class)), like));
                        }
                    }
                    if (!or.isEmpty()) {
                        ps.add(cb.or(or.toArray(Predicate[]::new)));
                    }
                }
            }
            if (f.createdOn() != null) {
                ps.add(cb.between(root.get("dateCreated"),
                        f.createdOn().atStartOfDay(), f.createdOn().atTime(LocalTime.MAX)));
            }
            if (f.onDay() != null && has(root, dayField())) {
                ps.add(cb.between(root.get(dayField()),
                        f.onDay().atStartOfDay(), f.onDay().atTime(LocalTime.MAX)));
            }
            if (f.from() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("dateCreated"), f.from().atStartOfDay()));
            }
            if (f.to() != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("dateCreated"), f.to().atTime(LocalTime.MAX)));
            }
            if (f.bookmarked() != null && has(root, "bookmarked")) {
                ps.add(cb.equal(root.get("bookmarked"), f.bookmarked()));
            }
            if (f.urgent() != null && has(root, "urgent")) {
                ps.add(cb.equal(root.get("urgent"), f.urgent()));
            }
            if (f.dashboard() != null && has(root, "dashboard")) {
                ps.add(cb.equal(root.get("dashboard"), f.dashboard()));
            }
            if (f.statusId() != null && has(root, "status")) {
                ps.add(cb.equal(root.get("status").get("id"), f.statusId()));
            }
            if (f.typeId() != null && has(root, "type")) {
                ps.add(cb.equal(root.get("type").get("id"), f.typeId()));
            }
            if (f.tagId() != null && has(root, "tags")) {
                query.distinct(true);
                ps.add(cb.equal(root.join("tags").get("id"), f.tagId()));
            }
            if (f.goalId() != null && has(root, "goal")) {
                ps.add(cb.equal(root.get("goal").get("id"), f.goalId()));
            }
            if (f.writingId() != null && has(root, "writing")) {
                ps.add(cb.equal(root.get("writing").get("id"), f.writingId()));
            }
            // An exact priority, unlike the highPriority preset's ">= 3": the list filter asks for
            // "the p3s", and a module without a priority column simply ignores it.
            if (f.priority() != null && has(root, "priority")) {
                ps.add(cb.equal(root.get("priority"), f.priority()));
            }

            // ---- report presets ----
            LocalDateTime now = LocalDateTime.now();
            if (isTrue(f.noStatus()) && has(root, "status")) {
                ps.add(cb.isNull(root.get("status")));
            }
            if (isTrue(f.noType()) && has(root, "type")) {
                ps.add(cb.isNull(root.get("type")));
            }
            if (isTrue(f.noDepartment()) && has(root, "department")) {
                ps.add(cb.isNull(root.get("department")));
            }
            if (isTrue(f.noPriority()) && has(root, "priority")) {
                ps.add(cb.isNull(root.get("priority")));
            }
            if (isTrue(f.hasTags()) && has(root, "tags")) {
                ps.add(cb.isNotEmpty(root.<Set<Tag>>get("tags")));
            }
            if (isTrue(f.overdue()) && has(root, "endDate")) {
                ps.add(cb.lessThan(root.<LocalDateTime>get("endDate"), now));
            }
            if (isTrue(f.dueSoon()) && has(root, "endDate")) {
                ps.add(cb.between(root.<LocalDateTime>get("endDate"), now, now.plusDays(7)));
            }
            if (isTrue(f.highPriority()) && has(root, "priority")) {
                ps.add(cb.greaterThanOrEqualTo(root.<Integer>get("priority"), 3));
            }
            if (isTrue(f.recentCreated())) {
                ps.add(cb.greaterThanOrEqualTo(root.get("dateCreated"), now.minusDays(7)));
            }
            if (isTrue(f.recentUpdated()) && has(root, "lastUpdated")) {
                ps.add(cb.greaterThanOrEqualTo(root.get("lastUpdated"), now.minusDays(7)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    private static boolean isTrue(Boolean b) {
        return Boolean.TRUE.equals(b);
    }

    /** Null (not zero) on overflow, so an absurdly long digit string just falls back to text search. */
    private static Long tryParseId(String digits) {
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException overflow) {
            return null;
        }
    }

    private static boolean has(jakarta.persistence.criteria.Root<?> root, String attr) {
        try {
            root.get(attr);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
