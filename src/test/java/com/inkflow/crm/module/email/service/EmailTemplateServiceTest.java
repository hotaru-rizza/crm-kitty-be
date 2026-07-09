package com.inkflow.crm.module.email.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.domain.entity.EmailTemplate;
import com.inkflow.crm.domain.repository.EmailTemplateRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.email.dto.CreateEmailTemplateRequest;
import com.inkflow.crm.module.email.dto.EmailTemplateResponseDto;
import com.inkflow.crm.module.email.dto.UpdateEmailTemplateRequest;
import com.inkflow.crm.module.email.enums.BuiltInTemplateKey;
import com.inkflow.crm.module.email.enums.TriggerType;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailTemplateServiceTest {

    @Mock
    private EmailTemplateRepository emailTemplateRepository;

    @Mock
    private BuiltInTemplateSeeder builtInTemplateSeeder;

    @Mock
    private TemplateEmailRenderer templateEmailRenderer;

    @Mock
    private EmailTenantContextLoader tenantContextLoader;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AuditLabelFormatter auditLabelFormatter;

    @InjectMocks
    private EmailTemplateService service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Test
    void list_returnsTenantTemplates() {
        EmailTemplate template = EmailTemplate.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .triggerType(TriggerType.BOOKING_CONFIRMED)
                .subject("Subject")
                .body("Body")
                .enabled(true)
                .deletable(false)
                .builtinKey(BuiltInTemplateKey.CONFIRMATION.name())
                .category(BuiltInTemplateKey.CONFIRMATION.getCategory())
                .build();

        when(emailTemplateRepository.findAllByTenantIdOrderByCategoryAscTriggerTypeAscBuiltinKeyAsc(TENANT))
                .thenReturn(List.of(template));

        List<EmailTemplateResponseDto> result = service.list(TENANT);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().builtinKey()).isEqualTo(BuiltInTemplateKey.CONFIRMATION.name());
    }

    @Test
    void create_savesCustomTemplate() {
        CreateEmailTemplateRequest request = new CreateEmailTemplateRequest(
                TriggerType.MANUAL, null, "Hello", "Body text", true);

        when(emailTemplateRepository.save(any())).thenAnswer(inv -> {
            EmailTemplate saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        EmailTemplateResponseDto result = service.create(TENANT, request, USER);

        assertThat(result.triggerType()).isEqualTo(TriggerType.MANUAL);
        assertThat(result.deletable()).isTrue();
    }

    @Test
    void delete_throwsWhenTemplateIsNotDeletable() {
        UUID templateId = UUID.randomUUID();
        EmailTemplate template = EmailTemplate.builder()
                .id(templateId)
                .tenantId(TENANT)
                .deletable(false)
                .triggerType(TriggerType.BOOKING_CONFIRMED)
                .subject("Subject")
                .body("Body")
                .enabled(true)
                .category(BuiltInTemplateKey.CONFIRMATION.getCategory())
                .build();

        when(emailTemplateRepository.findByIdAndTenantId(templateId, TENANT)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.delete(TENANT, templateId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be deleted");
    }

    @Test
    void update_appliesPartialChanges() {
        UUID templateId = UUID.randomUUID();
        EmailTemplate template = EmailTemplate.builder()
                .id(templateId)
                .tenantId(TENANT)
                .deletable(true)
                .triggerType(TriggerType.MANUAL)
                .subject("Old")
                .body("Old body")
                .enabled(false)
                .category(TriggerType.MANUAL.getCategory())
                .build();

        when(emailTemplateRepository.findByIdAndTenantId(templateId, TENANT)).thenReturn(Optional.of(template));
        when(emailTemplateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmailTemplateResponseDto result = service.update(
                TENANT, templateId, new UpdateEmailTemplateRequest(null, null, "New", null, true), USER);

        ArgumentCaptor<EmailTemplate> captor = ArgumentCaptor.forClass(EmailTemplate.class);
        verify(emailTemplateRepository).save(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("New");
        assertThat(captor.getValue().getEnabled()).isTrue();
        assertThat(result.subject()).isEqualTo("New");
    }
}
