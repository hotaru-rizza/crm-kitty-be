package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.RolePermission;
import com.inkflow.crm.domain.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByRole(UserRole role);

    Optional<RolePermission> findByRoleAndPermission(UserRole role, String permission);

    void deleteByRole(UserRole role);
}
