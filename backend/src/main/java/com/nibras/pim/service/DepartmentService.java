package com.nibras.pim.service;

import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.repo.DepartmentRepository;
import com.nibras.pim.web.dto.DepartmentDto;
import com.nibras.pim.web.dto.LookupDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Department as a first-class module. */
@Service
public class DepartmentService extends AbstractCrudService<Department, DepartmentDto> {

    public DepartmentService(DepartmentRepository repo) {
        super(repo);
    }

    @Override
    public String name() {
        return "departments";
    }

    @Override
    protected List<String> searchFields() {
        return List.of("code", "summary");
    }

    @Override
    protected DepartmentDto toDto(Department d) {
        return new DepartmentDto(d.getId(), d.getVersion(), d.feedTitle(),
                d.getName(), d.getCode(), d.getSummary(), d.getNotes(),
                d.getPriority(), d.getBookmarked(), coverExists(d.getId()),
                d.getDateCreated(), d.getLastUpdated());
    }

    @Override
    protected Department toNew(DepartmentDto dto) {
        Department d = new Department();
        apply(dto, d);
        return d;
    }

    @Override
    protected void apply(DepartmentDto dto, Department d) {
        d.setName(dto.name());
        d.setCode(dto.code());
        d.setSummary(dto.summary());
        d.setNotes(dto.notes());
        d.setPriority(dto.priority());
        d.setBookmarked(dto.bookmarked());
    }

    @Override
    public Map<String, List<LookupDto>> lookups() {
        return Map.of();
    }
}
