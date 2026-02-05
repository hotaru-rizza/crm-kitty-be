package com.inkflow.crm.module.waiver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.*;
import com.inkflow.crm.domain.repository.*;
import com.inkflow.crm.module.waiver.dto.*;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WaiverService {

    private final WaiverTemplateRepository waiverTemplateRepository;
    private final SignedWaiverRepository signedWaiverRepository;
    private final AppointmentRepository appointmentRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public WaiverTemplateDto getActiveTemplate() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        
        WaiverTemplate template = waiverTemplateRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseGet(() -> createDefaultTemplate(tenantId));
        
        return mapToTemplateDto(template);
    }

    private WaiverTemplate createDefaultTemplate(UUID tenantId) {
        List<WaiverCheckbox> checkboxes = List.of(
            WaiverCheckbox.builder()
                .checkboxId("age_confirmation")
                .label("Підтверджую, що мені виповнилось 18 років")
                .isRequired(true)
                .build(),
            WaiverCheckbox.builder()
                .checkboxId("health_confirmation")
                .label("Підтверджую відсутність протипоказань (вагітність, годування, захворювання крові, алергії, шкірні захворювання)")
                .isRequired(true)
                .build(),
            WaiverCheckbox.builder()
                .checkboxId("sober_confirmation")
                .label("Підтверджую, що не перебуваю під впливом алкоголю або наркотичних речовин")
                .isRequired(true)
                .build(),
            WaiverCheckbox.builder()
                .checkboxId("aftercare_confirmation")
                .label("Ознайомлений(а) з правилами догляду за татуюванням та зобов'язуюсь їх дотримуватись")
                .isRequired(true)
                .build(),
            WaiverCheckbox.builder()
                .checkboxId("consent_confirmation")
                .label("Даю згоду на проведення процедури татуювання")
                .isRequired(true)
                .build()
        );

        String content = """
            # ІНФОРМОВАНА ЗГОДА НА ПРОВЕДЕННЯ ТАТУЮВАННЯ
            
            ## 1. Загальні положення
            Я, нижчепідписаний(а), добровільно звертаюсь до тату-студії для нанесення татуювання та підтверджую наступне:
            
            ## 2. Стан здоров'я
            - Мені виповнилось 18 років
            - Я не маю протипоказань до проведення процедури
            - Я не перебуваю під впливом алкоголю чи наркотичних речовин
            - Я повідомив(ла) майстра про всі відомі мені алергічні реакції
            
            ## 3. Усвідомлення ризиків
            Я розумію, що:
            - Татуювання є постійним та його видалення складне і дороговартісне
            - Можливі побічні ефекти: набряк, почервоніння, свербіж
            - Результат залежить від індивідуальних особливостей шкіри
            - Необхідно дотримуватись правил догляду для належного загоєння
            
            ## 4. Правила догляду
            Я ознайомлений(а) з правилами догляду:
            - Не мочити татуювання перші 3 години
            - Наносити загоювальний крем 2-3 рази на день
            - Не здирати кірочки
            - Уникати прямих сонячних променів 2-3 тижні
            - Не відвідувати басейн, сауну, солярій 2-3 тижні
            
            ## 5. Згода
            Підписанням цього документу я підтверджую свою згоду на проведення процедури татуювання та приймаю на себе відповідальність за наслідки порушення правил догляду.
            """;

        WaiverTemplate template = WaiverTemplate.builder()
                .tenantId(tenantId)
                .title("Інформована згода на татуювання")
                .content(content)
                .version(1)
                .isActive(true)
                .checkboxes(new ArrayList<>(checkboxes))
                .createdBy(SecurityUtils.getCurrentUserId())
                .build();

        return waiverTemplateRepository.save(template);
    }

    @Transactional(readOnly = true)
    public WaiverTemplateDto getTemplateById(UUID templateId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        
        WaiverTemplate template = waiverTemplateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.waiverTemplate(templateId.toString()));
        
        return mapToTemplateDto(template);
    }

    @Transactional(readOnly = true)
    public SignedWaiverDto getSignedWaiver(UUID appointmentId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        
        SignedWaiver signedWaiver = signedWaiverRepository.findByAppointmentId(appointmentId)
                .orElse(null);
        
        if (signedWaiver == null || !signedWaiver.getTenantId().equals(tenantId)) {
            return null;
        }
        
        return mapToSignedWaiverDto(signedWaiver);
    }

    @Transactional
    public SignedWaiverDto signWaiver(SignWaiverRequest request, String clientIp) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        
        // Get appointment
        Appointment appointment = appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(
                request.getAppointmentId(), tenantId)
                .orElseThrow(() -> ResourceNotFoundException.appointment(request.getAppointmentId().toString()));
        
        // Check if already signed
        if (appointment.getWaiverSigned()) {
            throw new BusinessRuleException("Waiver already signed for this appointment");
        }
        
        // Get template
        WaiverTemplate template = waiverTemplateRepository.findByIdAndTenantId(request.getTemplateId(), tenantId)
                .orElseThrow(() -> ResourceNotFoundException.waiverTemplate(request.getTemplateId().toString()));
        
        // Validate required checkboxes
        validateCheckboxes(template, request.getCheckboxValues());
        
        // Create signed waiver
        SignedWaiver signedWaiver = SignedWaiver.builder()
                .tenantId(tenantId)
                .appointment(appointment)
                .client(appointment.getClient())
                .template(template)
                .signatureData(request.getSignatureData())
                .checkboxValues(serializeCheckboxValues(request.getCheckboxValues()))
                .clientIp(clientIp)
                .build();
        
        signedWaiver = signedWaiverRepository.save(signedWaiver);
        
        // Update appointment
        appointment.setWaiverSigned(true);
        appointment.setSignedWaiver(signedWaiver);
        appointmentRepository.save(appointment);
        
        return mapToSignedWaiverDto(signedWaiver);
    }

    private void validateCheckboxes(WaiverTemplate template, Map<String, Boolean> checkboxValues) {
        if (template.getCheckboxes() == null || template.getCheckboxes().isEmpty()) {
            return;
        }
        
        for (WaiverCheckbox checkbox : template.getCheckboxes()) {
            if (checkbox.getIsRequired()) {
                Boolean value = checkboxValues != null ? checkboxValues.get(checkbox.getCheckboxId()) : null;
                if (value == null || !value) {
                    throw new BusinessRuleException("Required checkbox not checked: " + checkbox.getLabel());
                }
            }
        }
    }

    private String serializeCheckboxValues(Map<String, Boolean> values) {
        if (values == null) return null;
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Map<String, Boolean> deserializeCheckboxValues(String json) {
        if (json == null || json.isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Boolean>>() {});
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    private WaiverTemplateDto mapToTemplateDto(WaiverTemplate template) {
        List<WaiverTemplateDto.CheckboxDto> checkboxes = template.getCheckboxes().stream()
                .map(cb -> WaiverTemplateDto.CheckboxDto.builder()
                        .id(cb.getCheckboxId())
                        .label(cb.getLabel())
                        .isRequired(cb.getIsRequired())
                        .build())
                .collect(Collectors.toList());
        
        return WaiverTemplateDto.builder()
                .id(template.getId())
                .title(template.getTitle())
                .content(template.getContent())
                .version(template.getVersion())
                .checkboxes(checkboxes)
                .createdAt(template.getCreatedAt())
                .build();
    }

    private SignedWaiverDto mapToSignedWaiverDto(SignedWaiver signedWaiver) {
        return SignedWaiverDto.builder()
                .id(signedWaiver.getId())
                .appointmentId(signedWaiver.getAppointment().getId())
                .clientId(signedWaiver.getClient().getId())
                .clientName(signedWaiver.getClient().getFirstName() + " " + signedWaiver.getClient().getLastName())
                .templateId(signedWaiver.getTemplate().getId())
                .templateTitle(signedWaiver.getTemplate().getTitle())
                .signatureData(signedWaiver.getSignatureData())
                .checkboxValues(deserializeCheckboxValues(signedWaiver.getCheckboxValues()))
                .signedAt(signedWaiver.getSignedAt())
                .build();
    }
}
