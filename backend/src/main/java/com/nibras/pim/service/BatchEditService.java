package com.nibras.pim.service;

import com.nibras.pim.domain.Book;
import com.nibras.pim.domain.Goal;
import com.nibras.pim.domain.IndexCard;
import com.nibras.pim.domain.Journal;
import com.nibras.pim.domain.Planner;
import com.nibras.pim.domain.Task;
import com.nibras.pim.domain.Writing;
import com.nibras.pim.service.CommandParser.Assignment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Applies a compact command (see {@link CommandParser}) to many selected records at once.
 * Updates run through the persistence context so the audit trail records each record's changes.
 */
@Service
public class BatchEditService {

    /**
     * Modules batch editing works on.
     *
     * <p>Hardcoded rather than derived from the catalogue, so that adding a CRUD module does not
     * silently enrol it in bulk edits — but that cuts both ways: `contacts` returned
     * "Unknown module: contacts" until it was added here, which is the wrong answer to a perfectly
     * reasonable "unbookmark these twelve people".
     *
     * <p>Which TOKENS apply is decided per entity, not here: {@link CommandParser#parse} is given
     * the class and only produces assignments for properties it actually has. So `*-` and `p3`
     * work on a contact while `?status` — a column a contact does not carry — is rejected rather
     * than failing at the write.
     */
    private static final Map<String, Class<?>> CLASSES = Map.of(
            "tasks", Task.class, "journals", Journal.class, "planners", Planner.class,
            "resources", Book.class, "notes", IndexCard.class, "goals", Goal.class,
            "writings", Writing.class, "contacts", com.nibras.pim.domain.Contact.class);

    private final EntityCatalog catalog;
    private final CommandParser parser;

    @PersistenceContext
    private EntityManager em;

    public BatchEditService(EntityCatalog catalog, CommandParser parser) {
        this.catalog = catalog;
        this.parser = parser;
    }

    public record BatchRequest(List<Long> ids, String command) {
    }

    public record BatchResult(int updated, List<String> fields) {
    }

    @Transactional
    public BatchResult apply(String module, BatchRequest req) {
        Class<?> cls = CLASSES.get(module);
        if (cls == null) {
            throw new ResponseStatusException(NOT_FOUND, "Unknown module: " + module);
        }
        if (req.ids() == null || req.ids().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "No records selected.");
        }
        AbstractCrudService<?, ?> service = catalog.find(module).orElseThrow();
        List<Assignment> ops = parser.parse(req.command(), service.lookups(), cls, false).assignments();
        if (ops.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "No changes in command.");
        }

        int updated = 0;
        for (Long id : req.ids()) {
            Object entity = em.find(cls, id);
            if (entity == null) {
                continue;
            }
            BeanWrapper bw = new BeanWrapperImpl(entity);
            for (Assignment op : ops) {
                op.applyTo(bw, em);
            }
            updated++;
        }
        em.flush();   // fires @PreUpdate + the audit listener per dirty record
        return new BatchResult(updated, ops.stream().map(Assignment::field).toList());
    }
}
