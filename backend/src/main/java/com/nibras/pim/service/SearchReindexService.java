package com.nibras.pim.service;

import com.nibras.pim.domain.BaseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rebuilds the whole {@code search_doc} index from every module's records. Kept separate
 * from {@link SearchIndexService} (which the CRUD services depend on) to avoid a bean cycle
 * via {@link EntityCatalog}.
 */
@Service
public class SearchReindexService {

    private final EntityCatalog catalog;
    private final SearchIndexService index;

    public SearchReindexService(EntityCatalog catalog, SearchIndexService index) {
        this.catalog = catalog;
        this.index = index;
    }

    @Transactional   // read-write: it reads every entity and writes search_doc rows
    public int reindexAll() {
        int count = 0;
        for (AbstractCrudService<?, ?> service : catalog.all()) {
            String module = service.name();
            for (BaseEntity e : service.allForIndex()) {
                index.upsert(module, e.getId(), e.feedTitle(), e.feedDescription());
                count++;
            }
        }
        return count;
    }
}
