package com.inkflow.crm.module.settings.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.settings.dto.UpdateUserSettingsRequest;
import com.inkflow.crm.module.settings.dto.UserSettingsDto;
import com.inkflow.crm.module.staff.service.StaffLookup;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    @Mock
    private StaffLookup staffLookup;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private InkflowProperties inkflowProperties;

    @InjectMocks
    private UserSettingsService userSettingsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserSettings_usesStaffPreferences() {
        UUID staffId = UUID.randomUUID();
        authenticate(staffId);

        Staff staff = Staff.builder()
                .id(staffId)
                .uiLanguage("en")
                .startPage("calendar")
                .build();

        when(staffLookup.requireStaff(staffId)).thenReturn(staff);

        UserSettingsDto dto = userSettingsService.getCurrentUserSettings();

        assertEquals("en", dto.language());
        assertEquals("calendar", dto.startPage());
    }

    @Test
    void updateCurrentUserSettings_persistsLanguage() {
        UUID staffId = UUID.randomUUID();
        authenticate(staffId);

        Staff staff = Staff.builder().id(staffId).build();
        UpdateUserSettingsRequest request = new UpdateUserSettingsRequest();
        request.setLanguage("ua");
        request.setStartPage("clients");

        when(staffLookup.requireStaff(staffId)).thenReturn(staff);
        when(staffRepository.save(staff)).thenReturn(staff);

        UserSettingsDto dto = userSettingsService.updateCurrentUserSettings(request);

        assertEquals("ua", staff.getUiLanguage());
        assertEquals("clients", staff.getStartPage());
        assertEquals("ua", dto.language());
        verify(staffRepository).save(staff);
    }

    private void authenticate(UUID staffId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(staffId)
                .tenantId(UUID.randomUUID())
                .role(com.inkflow.crm.domain.enums.UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
