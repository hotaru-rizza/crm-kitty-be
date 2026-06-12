package com.inkflow.crm.module.email.service;

import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import com.inkflow.crm.module.email.dto.EmailSettingsDto;
import com.inkflow.crm.module.email.dto.SendEmailRequest;
import com.inkflow.crm.module.email.dto.SendEmailResultDto;
import com.inkflow.crm.module.email.mapper.EmailSettingsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailManagementService {

    private final EmailService emailService;
    private final ClientRepository clientRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final EmailSettingsMapper emailSettingsMapper;

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
