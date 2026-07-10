package com.nibras.pim.repo;

import com.nibras.pim.domain.RolePermission;
import com.nibras.pim.security.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findByRole(Role role);

    void deleteByRole(Role role);
}
