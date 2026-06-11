package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.security.SecurityUtils;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffLookupTest {

    @Mock
    private StaffRepository staffRepository;

    @InjectMocks
    private StaffLookup staffLookup;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireStaff_returnsTenantScopedStaff() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Staff staff = new Staff();
        staff.setId(staffId);
        staff.setTenantId(tenantId);

        authenticate(tenantId);
        when(staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId))
                .thenReturn(Optional.of(staff));

        Staff result = staffLookup.requireStaff(staffId);

        assertEquals(staffId, result.getId());
    }

    @Test
    void requireStaff_rejectsStaffFromAnotherTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        authenticate(tenantId);
        when(staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId))
                .thenReturn(Optional.empty());
        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(staffId.toString()))
                .thenReturn(Optional.of(staffWithTenant(staffId, otherTenantId)));

        assertThrows(ResourceNotFoundException.class, () -> staffLookup.requireStaff(staffId));
    }

    private Staff staffWithTenant(UUID staffId, UUID tenantId) {
        Staff staff = new Staff();
        staff.setId(staffId);
        staff.setTenantId(tenantId);
        return staff;
    }

    private void authenticate(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
