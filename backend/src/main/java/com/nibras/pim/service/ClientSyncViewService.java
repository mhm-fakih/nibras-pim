package com.nibras.pim.service;

import com.nibras.pim.domain.AppModule;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.repo.AppModuleRepository;
import com.nibras.pim.repo.DepartmentRepository;
import com.nibras.pim.web.dto.LookupDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The parameter vocabulary the clients (mobile / terminal / desktop) are expected to be working
 * from, assembled exactly as they filter it — so the Admin page can be compared against what each
 * device actually shows and a drift (a department renamed here but stale on the phone, a type
 * nobody's device knows about) is visible instead of being discovered through a mis-filed note.
 *
 * The filters are deliberately the clients' own, not "everything in the database":
 * <ul>
 *   <li><b>Departments</b> — {@code priority >= 2}, ordered by {@code orderNumber}. The long tail of
 *       low-priority departments is never offered on a phone keypad, so listing it here would
 *       report a difference that isn't one.</li>
 *   <li><b>Types</b> — only those of an <i>enabled</i> module. A disabled module isn't synced, so
 *       its types aren't either.</li>
 * </ul>
 * Modules come back in {@link AppModule#getSortOrder()} order — the "Order" column on the frontend
 * Settings page, i.e. the same order the sidebar navigates in. One order the user maintains in one
 * place, rather than a second one they would have to remember to keep in step.
 */
@Service
@Transactional(readOnly = true)
public class ClientSyncViewService {

    /** Departments below this priority are not offered on the clients. */
    private static final int MIN_DEPARTMENT_PRIORITY = 2;

    private final AppModuleRepository modules;
    private final DepartmentRepository departments;
    private final EntityCatalog catalog;

    public ClientSyncViewService(AppModuleRepository modules, DepartmentRepository departments,
                                 EntityCatalog catalog) {
        this.modules = modules;
        this.departments = departments;
        this.catalog = catalog;
    }

    public record SyncedDepartment(Long id, String code, String name, Integer priority, Integer orderNumber) {
    }

    /**
     * {@code bucketByType} / {@code bucketBy100} are the module's repo folder layout (see
     * RepositoryService.folder): the phone reads a record's files from its own copy of repo 1, so it
     * has to build the same {@code <code>/[<type>/][<id/100>/]<id>} path the server writes.
     */
    public record SyncedModule(String moduleKey, String label, String code, int sortOrder,
                               List<LookupDto> types, boolean bucketByType, boolean bucketBy100) {
    }

    public record ClientSyncView(List<SyncedDepartment> departments, List<SyncedModule> modules) {
    }

    public ClientSyncView view() {
        List<SyncedDepartment> deps = departments.findAll().stream()
                .filter(d -> d.getPriority() != null && d.getPriority() >= MIN_DEPARTMENT_PRIORITY)
                .sorted(Comparator.comparing(
                        (Department d) -> d.getOrderNumber() == null ? Integer.MAX_VALUE : d.getOrderNumber())
                        .thenComparing(d -> nz(d.getName())))
                .map(d -> new SyncedDepartment(d.getId(), d.getCode(), d.getName(),
                        d.getPriority(), d.getOrderNumber()))
                .toList();

        List<SyncedModule> mods = modules.findAll().stream()
                .filter(m -> !Boolean.FALSE.equals(m.getEnabled()))
                .sorted(Comparator.comparingInt(
                        (AppModule m) -> m.getSortOrder() == null ? Integer.MAX_VALUE : m.getSortOrder())
                        .thenComparing(m -> nz(m.getLabel())))
                .map(m -> new SyncedModule(m.getModuleKey(), m.getLabel(), m.getCode(),
                        m.getSortOrder() == null ? 0 : m.getSortOrder(), typesOf(m.getModuleKey()),
                        Boolean.TRUE.equals(m.getBucketByType()), Boolean.TRUE.equals(m.getBucketBy100())))
                .toList();

        return new ClientSyncView(deps, mods);
    }

    /** A module's own type lookup, or empty when it has none (not every module is type-bucketed). */
    private List<LookupDto> typesOf(String moduleKey) {
        return catalog.find(moduleKey)
                .map(svc -> {
                    Map<String, List<LookupDto>> lookups = svc.lookups();
                    return lookups.getOrDefault("types", List.of());
                })
                .orElse(List.of());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
