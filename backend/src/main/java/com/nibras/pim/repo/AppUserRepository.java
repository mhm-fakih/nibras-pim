package com.nibras.pim.repo;

import com.nibras.pim.domain.AppUser;
import com.nibras.pim.security.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countByRoleAndEnabledTrue(Role role);

    List<AppUser> findAllByOrderByUsernameAsc();
}
