package com.nibras.pim.service;

import com.nibras.pim.domain.CustomDashboard;
import com.nibras.pim.domain.CustomDashboardRegion;
import com.nibras.pim.repo.CustomDashboardRepository;
import com.nibras.pim.web.dto.CustomDashboardDto;
import com.nibras.pim.web.dto.CustomDashboardRegionDto;
import com.nibras.pim.web.dto.DashboardFileContentDto;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Plain CRUD for user-defined dashboards + their regions (component types: {@code quote},
 * {@code calendar}, {@code goalsHeatmap}, {@code kanban}, {@code file} — see the frontend's
 * DashboardComponents.tsx for what each renders). Deliberately not built on
 * {@link AbstractCrudService}: dashboards are admin/config objects (no tags, search index, audit
 * feed, or repo folder), not content records. A save replaces the whole region list at once —
 * simplest semantics for a builder UI that submits the full dashboard in one go. Named "Custom" to
 * stay clear of the pre-existing {@code DashboardService} (Kanban board / Indicators KPI data).
 */
@Service
@Transactional
public class CustomDashboardService {

    /** Cap so a huge accidental file target doesn't blow up a dashboard render. */
    private static final long MAX_FILE_BYTES = 200_000;

    private final CustomDashboardRepository repo;
    private final RepositoryService repositories;

    public CustomDashboardService(CustomDashboardRepository repo, RepositoryService repositories) {
        this.repo = repo;
        this.repositories = repositories;
    }

    @Transactional(readOnly = true)
    public List<CustomDashboardDto> list() {
        return repo.findAll(Sort.by("name")).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public CustomDashboardDto get(Long id) {
        return toDto(load(id));
    }

    public CustomDashboardDto create(CustomDashboardDto dto) {
        CustomDashboard d = new CustomDashboard();
        apply(dto, d);
        return toDto(repo.saveAndFlush(d));
    }

    public CustomDashboardDto update(Long id, CustomDashboardDto dto) {
        CustomDashboard d = load(id);
        apply(dto, d);
        return toDto(repo.saveAndFlush(d));
    }

    public void delete(Long id) {
        CustomDashboard d = load(id);
        d.setDeletedOn(LocalDateTime.now());
        repo.save(d);
    }

    /**
     * Plain-text content of a file under one of the three repo roots, for the "file" component —
     * e.g. {@code repo=1, path=dashboard-top.md} reads {@code <repo1>/dashboard-top.md}. Bounded to
     * the repo root the same way {@code AttachmentService.resolveInRepo} bounds record-folder reads,
     * so a region config can't be used to read arbitrary filesystem paths.
     */
    @Transactional(readOnly = true)
    public DashboardFileContentDto readFile(int repoIndex, String path) {
        if (path == null || path.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing file path.");
        }
        Path root = repositories.repoRoot(repoIndex);
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root)) {
            throw new ResponseStatusException(BAD_REQUEST, "Illegal path.");
        }
        if (!Files.isRegularFile(target)) {
            throw new ResponseStatusException(NOT_FOUND, "File not found: " + path);
        }
        try {
            byte[] bytes = Files.readAllBytes(target);
            boolean truncated = bytes.length > MAX_FILE_BYTES;
            String content = new String(bytes, 0, (int) Math.min(bytes.length, MAX_FILE_BYTES),
                    java.nio.charset.StandardCharsets.UTF_8);
            return new DashboardFileContentDto(path, content, truncated);
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not read file: " + e.getMessage());
        }
    }

    private CustomDashboard load(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Dashboard " + id + " not found"));
    }

    private void apply(CustomDashboardDto dto, CustomDashboard d) {
        d.setName(dto.name());
        d.setColumnsCount(dto.columnsCount() == null ? 2 : dto.columnsCount());
        d.setRefreshMinutes(dto.refreshMinutes());
        // Whole-list replace: orphanRemoval on the mapped collection deletes anything dropped.
        d.getRegions().clear();
        List<CustomDashboardRegionDto> incoming = dto.regions();
        for (int i = 0; incoming != null && i < incoming.size(); i++) {
            CustomDashboardRegionDto r = incoming.get(i);
            CustomDashboardRegion region = new CustomDashboardRegion();
            region.setDashboard(d);
            region.setOrderIndex(r.orderIndex() != null ? r.orderIndex() : i);
            region.setComponentType(r.componentType());
            region.setConfig(r.config());
            region.setColSpan(r.colSpan() != null && r.colSpan() > 0 ? r.colSpan() : 1);
            d.getRegions().add(region);
        }
    }

    private CustomDashboardDto toDto(CustomDashboard d) {
        List<CustomDashboardRegionDto> regions = d.getRegions().stream()
                .map(r -> new CustomDashboardRegionDto(
                        r.getId(), r.getOrderIndex(), r.getComponentType(), r.getConfig(), r.getColSpan()))
                .toList();
        return new CustomDashboardDto(d.getId(), d.getName(), d.getColumnsCount(), d.getRefreshMinutes(), regions);
    }
}
