package com.nibras.pim.repo;

import com.nibras.pim.domain.SavedSearch;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedSearchRepository extends BaseRepository<SavedSearch> {

    /** Saved searches tied to a module (by single-letter entity code), highest priority first. */
    List<SavedSearch> findByEntityOrderByPriorityDescIdAsc(String entity);
}
