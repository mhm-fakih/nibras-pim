package com.nibras.pim.web;

import com.nibras.pim.domain.AuditLog;
import com.nibras.pim.repo.AuditLogRepository;
import com.nibras.pim.web.dto.AuditEntryDto;
import com.nibras.pim.web.dto.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Read-only views of the audit trail: global (filtered) and per-record history. */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLogRepository repo;

    public AuditController(AuditLogRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public PageResponse<AuditEntryDto> list(
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String op,
            @RequestParam(required = false) String field,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 50, sort = "changedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (entity != null && !entity.isBlank()) {
                ps.add(cb.equal(root.get("entity"), entity));
            }
            if (entityId != null) {
                ps.add(cb.equal(root.get("entityId"), entityId));
            }
            if (op != null && !op.isBlank()) {
                ps.add(cb.equal(root.get("op"), op.toUpperCase()));
            }
            if (field != null && !field.isBlank()) {
                ps.add(cb.like(cb.lower(root.get("field")), "%" + field.toLowerCase() + "%"));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("changedAt"), from.atStartOfDay()));
            }
            if (to != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("changedAt"), to.atTime(LocalTime.MAX)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };

        return PageResponse.of(repo.findAll(spec, pageable).map(AuditEntryDto::of));
    }

    @GetMapping("/{entity}/{id}")
    public List<AuditEntryDto> history(@PathVariable String entity, @PathVariable Long id) {
        return repo.findByEntityAndEntityIdOrderByChangedAtDescIdDesc(entity, id)
                .stream().map(AuditEntryDto::of).toList();
    }
}
