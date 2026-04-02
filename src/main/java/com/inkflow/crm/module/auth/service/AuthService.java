package com.inkflow.crm.module.auth.service;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.auth.dto.CurrentUserResponse;
import com.inkflow.crm.module.settings.service.SettingsService;
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
    private final SettingsService settingsService;

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser() {
        UserPrincipal principal = SecurityUtils.getCurrentUserOrThrow();

        Staff staff = staffRepository.findByAuthUserIdAndDeletedAtIsNull(principal.getId().toString())
                .or(() -> staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(principal.getId(), principal.getTenantId()))
                .orElseThrow(() -> ResourceNotFoundException.staff(principal.getId().toString()));

        Tenant tenant = tenantRepository.findById(principal.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        com.inkflow.crm.common.exception.ErrorCode.NOT_FOUND, "Tenant not found"));

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
                .permissions(settingsService.getGrantedPermissions(
                        principal.getTenantId(), principal.getRole()))
                .build();
    }
}
