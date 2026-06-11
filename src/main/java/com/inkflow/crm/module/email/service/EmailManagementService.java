package com.inkflow.crm.module.email.service;

import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import com.inkflow.crm.module.email.dto.EmailSettingsDto;
import com.inkflow.crm.module.email.dto.EmailTemplateDto;
import com.inkflow.crm.module.email.dto.SendEmailRequest;
import com.inkflow.crm.module.email.dto.SendEmailResultDto;
import com.inkflow.crm.module.email.mapper.EmailSettingsMapper;
import com.inkflow.crm.module.email.mapper.EmailTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailManagementService {

    private final EmailService emailService;
    private final ClientRepository clientRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final EmailSettingsMapper emailSettingsMapper;
    private final EmailTemplateMapper emailTemplateMapper;

    @Transactional
    public SendEmailResultDto sendBulk(UUID tenantId, SendEmailRequest request) {
        List<Client> clients = clientRepository.findAllById(request.getClientIds());

        int sent = 0;
        int skipped = 0;

        for (Client client : clients) {
            if (!client.getTenantId().equals(tenantId)) {
                continue;
            }
            if (client.getEmail() == null || client.getEmail().isBlank()) {
                skipped++;
                continue;
            }

            emailService.sendManual(
                    tenantId,
                    client.getEmail(),
                    client.getFullName(),
                    request.getSubject(),
                    request.getBody()
            );
            sent++;
        }

        log.info("Bulk email: tenantId={} sent={} skipped={}", tenantId, sent, skipped);
        return new SendEmailResultDto(sent, skipped);
    }

    @Transactional(readOnly = true)
    public List<EmailTemplateDto> getTemplates(UUID tenantId) {
        CompanySettings settings = companySettingsRepository.findByTenantId(tenantId).orElse(null);
        Map<String, Map<String, String>> custom = settings != null ? settings.getEmailTemplates() : null;

        List<EmailTemplateDto> result = new ArrayList<>();
        for (String type : EmailTemplateMapper.MANAGED_TYPES) {
            result.add(emailTemplateMapper.toDto(type, custom));
        }
        return result;
    }

    @Transactional
    public EmailTemplateDto updateTemplate(UUID tenantId, String type, EmailTemplateDto dto) {
        CompanySettings settings = requireSettings(tenantId);
        String normalizedType = type.toUpperCase();

        Map<String, Map<String, String>> templates = emailTemplateMapper.templatesOrEmpty(settings);
        templates.put(normalizedType, emailTemplateMapper.toStorageEntry(dto));
        settings.setEmailTemplates(templates);
        companySettingsRepository.save(settings);

        log.info("Email template updated: tenantId={} type={}", tenantId, normalizedType);
        return EmailTemplateDto.builder()
                .type(normalizedType)
                .subject(dto.getSubject())
                .body(dto.getBody())
                .fields(dto.getFields())
                .build();
    }

    @Transactional
    public void resetTemplate(UUID tenantId, String type) {
        CompanySettings settings = requireSettings(tenantId);
        Map<String, Map<String, String>> templates = settings.getEmailTemplates();

        if (templates == null) {
            return;
        }

        String normalizedType = type.toUpperCase();
        templates.remove(normalizedType);
        settings.setEmailTemplates(templates);
        companySettingsRepository.save(settings);

        log.info("Email template reset: tenantId={} type={}", tenantId, normalizedType);
    }

    @Transactional(readOnly = true)
    public EmailSettingsDto getEmailSettings(UUID tenantId) {
        return companySettingsRepository.findByTenantId(tenantId)
                .map(emailSettingsMapper::toDto)
                .orElseGet(emailSettingsMapper::defaultDto);
    }

    @Transactional
    public EmailSettingsDto updateEmailSettings(UUID tenantId, EmailSettingsDto dto) {
        CompanySettings settings = requireSettings(tenantId);
        emailSettingsMapper.applyUpdate(settings, dto);
        settings = companySettingsRepository.save(settings);

        log.info("Email settings updated: tenantId={}", tenantId);
        return emailSettingsMapper.toDto(settings);
    }

    private CompanySettings requireSettings(UUID tenantId) {
        return companySettingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Settings not found"));
    }
}
