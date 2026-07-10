package com.nibras.pim.service;

import com.nibras.pim.domain.Tag;
import com.nibras.pim.repo.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Tag lookup + create-if-missing, backing the detail-pane tag editor's autocomplete. */
@Service
public class TagService {

    private final TagRepository repo;

    public TagService(TagRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<Tag> all() {
        return repo.findAllByOrderByNameAsc();
    }

    /** Finds a tag by name (case-insensitive) or creates it. Used when linking a typed-in tag. */
    @Transactional
    public Tag findOrCreate(String name) {
        String n = name.trim();
        return repo.findFirstByNameIgnoreCase(n)
                .orElseGet(() -> {
                    Tag t = new Tag();
                    t.setName(n);
                    return repo.save(t);
                });
    }
}
