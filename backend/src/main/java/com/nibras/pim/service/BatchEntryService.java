package com.nibras.pim.service;

import com.nibras.pim.domain.BaseEntity;
import com.nibras.pim.domain.Book;
import com.nibras.pim.domain.Goal;
import com.nibras.pim.domain.IndexCard;
import com.nibras.pim.domain.Journal;
import com.nibras.pim.domain.Planner;
import com.nibras.pim.domain.Task;
import com.nibras.pim.domain.Writing;
import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.repo.AppModuleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Adds many records at once: one record per non-blank line, each line parsed with the shared
 * {@link CommandParser} ({@code #type ?status @context pN …} resolved against that line's module,
 * the rest of the line becoming the title). A line may start with a module code (e.g. {@code t})
 * to override the page's default module. Each line runs in its own transaction so one bad line
 * doesn't roll back the others.
 */
@Service
public class BatchEntryService {

    private static final Map<String, Class<? extends BaseEntity>> CLASSES = Map.of(
            "tasks", Task.class, "journals", Journal.class, "planners", Planner.class,
            "resources", Book.class, "notes", IndexCard.class, "goals", Goal.class,
            "writings", Writing.class, "courses", Course.class);

    private final CommandParser parser;
    private final EntityCatalog catalog;
    private final AppModuleRepository modules;
    private final ObjectProvider<SearchIndexService> searchIndex;
    private final TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    public BatchEntryService(CommandParser parser, EntityCatalog catalog, AppModuleRepository modules,
                             ObjectProvider<SearchIndexService> searchIndex,
                             PlatformTransactionManager txManager) {
        this.parser = parser;
        this.catalog = catalog;
        this.modules = modules;
        this.searchIndex = searchIndex;
        this.tx = new TransactionTemplate(txManager);
    }

    public record EntryRequest(String module, String text) {
    }

    public record LineResult(int line, String input, String module, Long id, String error) {
    }

    public record EntryResult(int created, List<LineResult> results) {
    }

    /** A record created from one command line. */
    public record Created(String module, Long id, String title) {
    }

    /** Read-only parse of a command line for preview (no record created). */
    public record Plan(String module, String title, String error) {
    }

    public EntryResult add(EntryRequest req) {
        if (req == null || req.text() == null || req.text().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Nothing to add.");
        }
        String defaultModule = defaultModule(req.module());
        Map<String, String> codeToKey = creatableCodeMap();

        List<LineResult> results = new ArrayList<>();
        int created = 0;
        int lineNo = 0;
        for (String raw : req.text().split("\\r?\\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            lineNo++;
            int n = lineNo;
            try {
                Created c = tx.execute(status -> create(line, defaultModule, codeToKey));
                results.add(new LineResult(n, line, c.module(), c.id(), null));
                created++;
            } catch (ResponseStatusException e) {
                results.add(new LineResult(n, line, null, null, e.getReason()));
            } catch (RuntimeException e) {
                results.add(new LineResult(n, line, null, null, e.getMessage()));
            }
        }
        return new EntryResult(created, results);
    }

    public boolean isCreatable(String module) {
        return CLASSES.containsKey(module);
    }

    private String defaultModule(String module) {
        return module != null && CLASSES.containsKey(module) ? module : "tasks";
    }

    /** Module code (e.g. "T") -> module key, for the creatable modules only. */
    public Map<String, String> creatableCodeMap() {
        Map<String, String> codeToKey = new LinkedHashMap<>();
        modules.findAll().forEach(m -> {
            if (m.getCode() != null && !m.getCode().isBlank() && CLASSES.containsKey(m.getModuleKey())) {
                codeToKey.put(m.getCode().trim().toUpperCase(), m.getModuleKey());
            }
        });
        return codeToKey;
    }

    /** Peel an optional leading module code; returns {@code [moduleKey, body]}. */
    private String[] splitModule(String line, String defaultModule, Map<String, String> codeToKey) {
        String[] head = line.strip().split("\\s+", 2);
        String key = codeToKey.get(head[0].toUpperCase());
        if (key != null) {
            return new String[]{key, head.length > 1 ? head[1] : ""};
        }
        return new String[]{defaultModule, line.strip()};
    }

    /** Create one record from a single line within the <em>current</em> transaction. */
    public Created create(String line, String defaultModule, Map<String, String> codeToKey) {
        String[] mb = splitModule(line, defaultModule, codeToKey);
        String module = mb[0];
        String body = mb[1];

        Class<? extends BaseEntity> cls = CLASSES.get(module);
        if (cls == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot add records to module: " + module);
        }
        AbstractCrudService<?, ?> svc = catalog.find(module).orElseThrow();
        CommandParser.Parsed parsed = parser.parse(body, svc.lookups(), cls, true);
        if (parsed.text().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "No title text.");
        }

        BaseEntity entity = newInstance(cls);
        BeanWrapper bw = new BeanWrapperImpl(entity);
        if (bw.isWritableProperty("summary")) {
            bw.setPropertyValue("summary", parsed.text());
        }
        if (bw.isWritableProperty("title")) {
            bw.setPropertyValue("title", parsed.text());
        }
        parsed.assignments().forEach(op -> op.applyTo(bw, em));

        em.persist(entity);
        em.flush();   // assigns the id + fires the audit listener
        Long id = entity.getId();
        SearchIndexService si = searchIndex.getIfAvailable();
        if (si != null) {
            si.upsert(module, id, entity.feedTitle(), entity.feedDescription());
        }
        return new Created(module, id, parsed.text());
    }

    /** Create one record from a line in its own transaction (used by the file importer). */
    public Created createOneInTx(String line, String defaultModule) {
        return tx.execute(status -> create(line, defaultModule(defaultModule), creatableCodeMap()));
    }

    /**
     * Like {@link #createOneInTx} but also fills the record's long-text body from {@code body} — the
     * {@code fullText} field for resources, otherwise {@code description}. Used by the file importer so
     * a plain-text (.md/.txt) file's contents land in the record, not just its name in the title.
     */
    public Created createWithBodyInTx(String line, String defaultModule, String body) {
        return tx.execute(status -> {
            Created created = create(line, defaultModule(defaultModule), creatableCodeMap());
            if (body != null && !body.isBlank()) {
                BaseEntity entity = em.find(CLASSES.get(created.module()), created.id());
                if (entity != null) {
                    BeanWrapper bw = new BeanWrapperImpl(entity);
                    String field = "resources".equals(created.module()) ? "fullText" : "description";
                    if (!bw.isWritableProperty(field) && bw.isWritableProperty("description")) {
                        field = "description";
                    }
                    if (bw.isWritableProperty(field)) {
                        bw.setPropertyValue(field, body);
                        em.flush();  // managed entity → persists the body
                        SearchIndexService si = searchIndex.getIfAvailable();
                        if (si != null) {
                            si.upsert(created.module(), created.id(),
                                    entity.feedTitle(), entity.feedDescription());
                        }
                    }
                }
            }
            return created;
        });
    }

    /** Read-only parse of a line for preview: resolves/validates without creating anything. */
    @Transactional(readOnly = true)
    public Plan planLine(String line, String defaultModule) {
        String[] mb = splitModule(line, defaultModule(defaultModule), creatableCodeMap());
        String module = mb[0];
        String body = mb[1];
        Class<? extends BaseEntity> cls = CLASSES.get(module);
        if (cls == null) {
            return new Plan(module, null, "Cannot add records to module: " + module);
        }
        try {
            AbstractCrudService<?, ?> svc = catalog.find(module).orElseThrow();
            CommandParser.Parsed parsed = parser.parse(body, svc.lookups(), cls, true);
            if (parsed.text().isBlank()) {
                return new Plan(module, null, "No title text.");
            }
            return new Plan(module, parsed.text(), null);
        } catch (ResponseStatusException e) {
            return new Plan(module, null, e.getReason());
        }
    }

    private static BaseEntity newInstance(Class<? extends BaseEntity> cls) {
        try {
            var ctor = cls.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot instantiate " + cls.getSimpleName());
        }
    }
}
