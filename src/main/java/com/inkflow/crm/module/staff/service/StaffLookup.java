package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StaffLookup {

    private final StaffRepository staffRepository;

    public Staff requireStaff(UUID staffId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        return staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId)
                .or(() -> staffRepository.findByAuthUserIdAndDeletedAtIsNull(staffId.toString())
                        .filter(staff -> tenantId.equals(staff.getTenantId())))
                .orElseThrow(() -> ResourceNotFoundException.staff(staffId.toString()));
    }
}
