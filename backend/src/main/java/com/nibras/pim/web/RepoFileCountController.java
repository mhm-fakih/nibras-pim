package com.nibras.pim.web;

import com.nibras.pim.service.RepositoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** How many files tasks have in repository 1 — the dashboard's open-tasks list shows it. */
@RestController
public class RepoFileCountController {

    private final RepositoryService repos;
    public RepoFileCountController(RepositoryService repos) {
        this.repos = repos;
    }

    @GetMapping("/api/tasks/repo1-file-counts")
    public Map<Long, Integer> counts(@RequestParam List<Long> ids) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        for (Long id : ids.stream().limit(500).toList()) {
            out.put(id, repos.countFiles(1, "tasks", id, null));
        }
        return out;
    }
}
