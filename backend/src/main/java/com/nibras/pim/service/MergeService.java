package com.nibras.pim.service;

import com.nibras.pim.domain.Book;
import com.nibras.pim.domain.Goal;
import com.nibras.pim.domain.IndexCard;
import com.nibras.pim.domain.Journal;
import com.nibras.pim.domain.Planner;
import com.nibras.pim.domain.Taggable;
import com.nibras.pim.domain.Task;
import com.nibras.pim.domain.Writing;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Merges two records of the same type into one: {@code longtext}-columned fields (description,
 * notes, …) are concatenated (primary, then secondary); every other field is filled from the
 * secondary only where the primary is null (the primary's own value always wins). Files under the
 * secondary's repo-1 folder are moved into a {@code <secondaryId>-files} subfolder of the primary's
 * repo-1 folder, then the secondary is soft-deleted with {@code mergedIntoId} set to the primary.
 */
@Service
public class MergeService {

    private static final Map<String, Class<?>> CLASSES = Map.of(
            "tasks", Task.class, "journals", Journal.class, "planners", Planner.class,
            "resources", Book.class, "notes", IndexCard.class, "goals", Goal.class,
            "writings", Writing.class);

    private static final Set<String> EXCLUDED = Set.of(
            "id", "version", "dateCreated", "lastUpdated", "deletedOn", "mergedIntoId");

    private final EntityCatalog catalog;
    private final RepositoryService repositories;
    private final RelationService relations;

    @PersistenceContext
    private EntityManager em;

    public MergeService(EntityCatalog catalog, RepositoryService repositories, RelationService relations) {
        this.catalog = catalog;
        this.repositories = repositories;
        this.relations = relations;
    }

    @Transactional
    public Object merge(String module, Long primaryId, Long secondaryId) {
        if (primaryId == null || secondaryId == null || primaryId.equals(secondaryId)) {
            throw new ResponseStatusException(BAD_REQUEST, "Choose two different records to merge.");
        }
        Class<?> cls = CLASSES.get(module);
        if (cls == null) {
            throw new ResponseStatusException(NOT_FOUND, "Unknown module: " + module);
        }
        AbstractCrudService<?, ?> service = catalog.find(module).orElseThrow();

        Object primary = em.find(cls, primaryId);
        Object secondary = em.find(cls, secondaryId);
        if (primary == null || secondary == null) {
            throw new ResponseStatusException(NOT_FOUND, "Record not found.");
        }

        BeanWrapper pbw = new BeanWrapperImpl(primary);
        BeanWrapper sbw = new BeanWrapperImpl(secondary);
        for (Field f : cls.getDeclaredFields()) {
            String name = f.getName();
            if (EXCLUDED.contains(name) || !pbw.isReadableProperty(name) || !pbw.isWritableProperty(name)) {
                continue;
            }
            Object sv = sbw.getPropertyValue(name);
            if (sv == null) {
                continue;
            }
            boolean longText = f.getType() == String.class && f.isAnnotationPresent(Column.class)
                    && "longtext".equalsIgnoreCase(f.getAnnotation(Column.class).columnDefinition());
            if (longText) {
                String sText = ((String) sv).trim();
                if (!sText.isEmpty()) {
                    Object pv = pbw.getPropertyValue(name);
                    String pText = pv == null ? "" : ((String) pv).trim();
                    String merged = pText.isEmpty() ? sText
                            : pText + "\n\n--- merged from #" + secondaryId + " ---\n\n" + sText;
                    pbw.setPropertyValue(name, merged);
                }
            } else if (pbw.getPropertyValue(name) == null) {
                pbw.setPropertyValue(name, sv);
            }
        }
        if (primary instanceof Taggable pt && secondary instanceof Taggable st) {
            pt.getTags().addAll(st.getTags());
        }
        em.flush();   // persist primary's field changes; fires @PreUpdate + the audit listener

        relations.repoint(module, cls, primaryId, secondaryId);   // e.g. a goal's tasks/journals/planners

        String primaryType = service.typeCodeFor(primaryId);
        String secondaryType = service.typeCodeFor(secondaryId);
        repositories.mergeFiles(module, primaryId, primaryType, secondaryId, secondaryType);

        service.reindexRecord(primaryId);

        sbw.setPropertyValue("mergedIntoId", primaryId);
        service.delete(secondaryId);   // soft-delete; also drops the checkout symlink if bookmarked

        return service.get(primaryId);
    }
}
