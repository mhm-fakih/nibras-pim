package com.nibras.pim.web;

import com.nibras.pim.repo.ContextRepository;
import com.nibras.pim.web.dto.LookupDto;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The GTD-style contexts (work_location) — used by the quick-edit @ picker. */
@RestController
@RequestMapping("/api/contexts")
public class ContextController {

    private final ContextRepository repo;

    public ContextController(ContextRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<LookupDto> list() {
        return repo.findAll(Sort.by("name")).stream()
                .map(c -> new LookupDto(c.getId(), c.getName(), c.getCode(), c.getStyle()))
                .toList();
    }
}
