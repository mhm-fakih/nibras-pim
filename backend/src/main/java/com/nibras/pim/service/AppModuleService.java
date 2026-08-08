package com.nibras.pim.service;

import com.nibras.pim.domain.AppModule;
import com.nibras.pim.repo.AppModuleRepository;
import com.nibras.pim.web.dto.AppModuleDto;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
@Transactional
public class AppModuleService {

    private final AppModuleRepository repo;

    public AppModuleService(AppModuleRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<AppModuleDto> list() {
        return repo.findAllByOrderBySortOrderAsc().stream().map(AppModuleDto::of).toList();
    }

    public AppModuleDto update(String key, AppModuleDto dto) {
        AppModule m = repo.findByModuleKey(key)
                .orElseThrow(() -> new EntityNotFoundException("Module " + key + " not found"));

        // Partial update: a null field means "leave unchanged". For code specifically, an
        // empty string means "clear it" (so toggling `enabled` alone never wipes the code).
        if (dto.code() != null) {
            String code = dto.code().isBlank() ? null : dto.code().trim().toUpperCase();
            if (code != null) {
                // Codes must stay unique so they can address a module unambiguously in commands.
                boolean taken = repo.findAll().stream()
                        .anyMatch(o -> !o.getModuleKey().equals(key) && code.equalsIgnoreCase(o.getCode()));
                if (taken) {
                    throw new ResponseStatusException(CONFLICT,
                            "Code '" + code + "' is already used by another module.");
                }
            }
            m.setCode(code);
        }
        if (dto.color() != null) {
            m.setColor(dto.color().isBlank() ? null : dto.color().trim());
        }
        if (dto.label() != null && !dto.label().isBlank()) {
            m.setLabel(dto.label().trim());
        }
        if (dto.enabled() != null) {
            m.setEnabled(dto.enabled());
        }
        if (dto.syncOrder() != null) {
            m.setSyncOrder(dto.syncOrder());
        }
        if (dto.sortOrder() != null) {
            m.setSortOrder(dto.sortOrder());
        }
        if (dto.bucketBy100() != null) {
            m.setBucketBy100(dto.bucketBy100());
        }
        if (dto.bucketByType() != null) {
            m.setBucketByType(dto.bucketByType());
        }
        return AppModuleDto.of(repo.save(m));
    }
}
