package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.staff.dto.CreateStaffRequest;
import com.inkflow.crm.module.staff.dto.StaffDto;
import com.inkflow.crm.module.staff.mapper.StaffMapper;
import com.inkflow.crm.security.UserPrincipal;
import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.module.staff.dto.UpdateStaffRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private StaffMapper staffMapper;

    @Mock
    private AuditRecorder auditRecorder;

    @InjectMocks
    private StaffService staffService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createStaff_persistsNewMember() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        CreateStaffRequest request = CreateStaffRequest.builder()
                .firstName("Alex")
                .lastName("Ink")
                .email("alex@test.com")
                .role("artist")
                .calendarColor("#6366f1")
                .locationIds(java.util.List.of(UUID.randomUUID()))
                .build();

        Staff entity = Staff.builder().firstName("Alex").build();
        Staff saved = Staff.builder().id(UUID.randomUUID()).tenantId(tenantId).email("alex@test.com").build();

        when(staffRepository.existsByEmailAndDeletedAtIsNull("alex@test.com")).thenReturn(false);
        when(staffMapper.toEntity(request)).thenReturn(entity);
        when(locationRepository.findAllById(any())).thenReturn(java.util.List.of());
        when(staffRepository.save(entity)).thenReturn(saved);
        when(staffMapper.toDto(saved)).thenReturn(StaffDto.builder().id(saved.getId()).email("alex@test.com").build());

        StaffDto result = staffService.createStaff(request);

        assertEquals("alex@test.com", result.getEmail());
        verify(staffRepository).save(entity);
    }

    @Test
    void createStaff_rejectsDuplicateEmail() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        CreateStaffRequest request = CreateStaffRequest.builder()
                .firstName("Alex")
                .lastName("Ink")
                .email("exists@test.com")
                .role("artist")
                .locationIds(java.util.List.of(UUID.randomUUID()))
                .build();

        when(staffRepository.existsByEmailAndDeletedAtIsNull("exists@test.com")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> staffService.createStaff(request));
    }

    @Test
    void updateStaff_rejectsDuplicateEmail() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId);

        Staff existing = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .email("old@test.com")
                .build();

        when(staffRepository.findByIdAndDeletedAtIsNull(staffId))
                .thenReturn(Optional.of(existing));
        when(staffRepository.existsByEmailAndDeletedAtIsNull("taken@test.com"))
                .thenReturn(true);

        UpdateStaffRequest request = UpdateStaffRequest.builder()
                .email("taken@test.com")
                .build();

        assertThrows(BusinessRuleException.class, () -> staffService.updateStaff(staffId, request));
    }

    @Test
    void deleteStaff_softDeletesWhenOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId);

        Staff staff = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .email("delete@test.com")
                .build();

        when(staffRepository.findByIdAndDeletedAtIsNull(staffId))
                .thenReturn(Optional.of(staff));
        when(staffRepository.save(staff)).thenReturn(staff);

        staffService.deleteStaff(staffId);

        ArgumentCaptor<Staff> captor = ArgumentCaptor.forClass(Staff.class);
        verify(staffRepository).save(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    void deleteStaff_rejectsNonOwner() {
        UUID tenantId = UUID.randomUUID();
        authenticateAsArtist(tenantId);

        assertThrows(AccessDeniedException.class, () -> staffService.deleteStaff(UUID.randomUUID()));
    }

    @Test
    void updateStaff_rejectsMissingStaff() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId);

        when(staffRepository.findByIdAndDeletedAtIsNull(staffId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> staffService.updateStaff(staffId, new com.inkflow.crm.module.staff.dto.UpdateStaffRequest()));
    }

    private void authenticateAsArtist(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.ARTIST)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private void authenticate(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
