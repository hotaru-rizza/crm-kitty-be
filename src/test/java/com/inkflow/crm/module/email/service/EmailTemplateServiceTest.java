package com.inkflow.crm.module.email.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.EmailTemplateOverride;
import com.inkflow.crm.domain.repository.EmailTemplateOverrideRepository;
import com.inkflow.crm.module.email.dto.TemplateListItemDto;
import com.inkflow.crm.module.email.dto.UpdateTemplateRequest;
import com.inkflow.crm.module.email.enums.TemplateKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailTemplateServiceTest {

    @Mock 
    private EmailTemplateOverrideRepository overrideRepository;

    @Mock
    private EmailContentRenderer contentRenderer;

    @Mock
    private InkflowProperties inkflowProperties;

    @InjectMocks
    private EmailTemplateService service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER   = UUID.randomUUID();

    @Test
    void listConfigurable_returnsOnlyConfigurableKeys() {
        when(overrideRepository.findAllByTenantId(TENANT)).thenReturn(List.of());

        List<TemplateListItemDto> result = service.listConfigurable(TENANT, "uk");

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(item -> {
            TemplateKey key = TemplateKey.valueOf(item.key());
            assertThat(key.isConfigurable()).isTrue();
        });
    }

    @Test
    void listConfigurable_marksOverriddenCorrectly() {
        EmailTemplateOverride override = EmailTemplateOverride.builder()
                .tenantId(TENANT)
                .templateKey(TemplateKey.BOOKING_CONFIRMED.name())
                .locale("uk")
                .subject("Custom")
                .body("Custom body")
                .build();

        when(overrideRepository.findAllByTenantId(TENANT)).thenReturn(List.of(override));

        List<TemplateListItemDto> result = service.listConfigurable(TENANT, "uk");

        TemplateListItemDto overridden = result.stream()
                .filter(i -> i.key().equals(TemplateKey.BOOKING_CONFIRMED.name()))
                .findFirst().orElseThrow();

        assertThat(overridden.isOverridden()).isTrue();
        assertThat(overridden.subject()).isEqualTo("Custom");
    }

    @Test
    void upsertOverride_savesNewOverride() {
        String body = "Hi {client_name}! Your booking at {studio_name} is confirmed. " +
                "Master: {master_name}, service: {service}, {date} at {time}. Address: {address}. Powered by {app_name}.";
        UpdateTemplateRequest request = new UpdateTemplateRequest("Confirmed — {studio_name}", body);

        when(overrideRepository.findByTenantIdAndTemplateKeyAndLocale(TENANT, "BOOKING_CONFIRMED", "uk"))
                .thenReturn(Optional.empty());
        when(overrideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TemplateListItemDto result = service.upsertOverride(TENANT, "BOOKING_CONFIRMED", "uk", request, USER);

        assertThat(result.key()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(result.isOverridden()).isTrue();

        ArgumentCaptor<EmailTemplateOverride> captor = ArgumentCaptor.forClass(EmailTemplateOverride.class);
        verify(overrideRepository).save(captor.capture());
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(USER);
    }

    @Test
    void upsertOverride_throwsWhenKeyIsUnknown() {
        UpdateTemplateRequest request = new UpdateTemplateRequest("Subject", "Body");

        assertThatThrownBy(() -> service.upsertOverride(TENANT, "NONEXISTENT_KEY", "uk", request, USER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Unknown template key");
    }

    @Test
    void upsertOverride_throwsWhenKeyIsNotConfigurable() {
        UpdateTemplateRequest request = new UpdateTemplateRequest("Subject", "Body");

        assertThatThrownBy(() -> service.upsertOverride(TENANT, "TEAM_INVITE", "uk", request, USER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not configurable");
    }

    @Test
    void resetOverride_deletesRow() {
        service.resetOverride(TENANT, "BOOKING_CONFIRMED", "uk");

        verify(overrideRepository).deleteByTenantIdAndTemplateKeyAndLocale(TENANT, "BOOKING_CONFIRMED", "uk");
    }
}
