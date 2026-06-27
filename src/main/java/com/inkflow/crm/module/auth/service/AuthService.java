package com.inkflow.crm.module.auth.service;

import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.auth.dto.CurrentUserResponse;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.security.SecurityUtils;
import com.inkflow.crm.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final StaffRepository staffRepository;
    private final TenantRepository tenantRepository;
    private final RolePermissionService rolePermissionService;
    private final AuthLoginAuditService authLoginAuditService;

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser() {
        UserPrincipal principal = SecurityUtils.getCurrentUserOrThrow();

        Staff staff = staffRepository.findByAuthUserIdAndDeletedAtIsNull(principal.getAuthUserId())
                .or(() -> staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(principal.getId(), principal.getTenantId()))
                .orElseThrow(() -> ResourceNotFoundException.staff(principal.getAuthUserId()));

        Tenant tenant = tenantRepository.findById(principal.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Tenant not found"));

        authLoginAuditService.recordLoginIfNew(principal, staff);

        return CurrentUserResponse.builder()
                .id(staff.getId())
                .email(staff.getEmail())
                .firstName(staff.getFirstName())
                .lastName(staff.getLastName())
                .avatar(staff.getAvatar())
                .role(staff.getRole().getValue())
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .locationIds(principal.getLocationIds())
                .permissions(rolePermissionService.getGrantedPermissions(
                        principal.getTenantId(), principal.getRole()))
                .build();
    }
}
