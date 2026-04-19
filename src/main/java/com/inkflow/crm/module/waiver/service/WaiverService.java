package com.inkflow.crm.module.waiver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.*;
import com.inkflow.crm.domain.repository.*;
import com.inkflow.crm.module.waiver.dto.*;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WaiverService {

    private final WaiverTemplateRepository waiverTemplateRepository;
    private final SignedWaiverRepository signedWaiverRepository;
    private final AppointmentRepository appointmentRepository;
    private final ObjectMapper objectMapper;

    // ──────────────── Template CRUD ────────────────

    @Transactional(readOnly = true)
    public List<WaiverTemplateDto> listTemplates() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return waiverTemplateRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(this::mapToTemplateDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public WaiverTemplateDto createTemplate(CreateTemplateRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        List<ConsentField> fields = request.getFields().stream()
                .map(ConsentFieldDto::toEntity)
                .collect(Collectors.toList());

        WaiverTemplate template = WaiverTemplate.builder()
                .tenantId(tenantId)
                .title(request.getTitle())
                .fields(fields)
                .version(1)
                .isActive(false)
                .createdBy(SecurityUtils.getCurrentUserId())
                .build();

        template = waiverTemplateRepository.save(template);
        return mapToTemplateDto(template);
    }

    @Transactional
    public WaiverTemplateDto updateTemplate(UUID templateId, UpdateTemplateRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        WaiverTemplate template = waiverTemplateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.waiverTemplate(templateId.toString()));

        List<ConsentField> fields = request.getFields().stream()
                .map(ConsentFieldDto::toEntity)
                .collect(Collectors.toList());

        template.setTitle(request.getTitle());
        template.setFields(fields);
        template.setVersion(template.getVersion() + 1);

        template = waiverTemplateRepository.save(template);
        return mapToTemplateDto(template);
    }

    @Transactional
    public void deleteTemplate(UUID templateId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        WaiverTemplate template = waiverTemplateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.waiverTemplate(templateId.toString()));

        template.setDeletedAt(Instant.now());
        template.setIsActive(false);
        waiverTemplateRepository.save(template);
    }

    @Transactional
    public WaiverTemplateDto activateTemplate(UUID templateId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        WaiverTemplate template = waiverTemplateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.waiverTemplate(templateId.toString()));

        waiverTemplateRepository.deactivateAllForTenant(tenantId);

        template.setIsActive(true);
        template = waiverTemplateRepository.save(template);
        return mapToTemplateDto(template);
    }

    @Transactional
    public WaiverTemplateDto deactivateTemplate(UUID templateId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        WaiverTemplate template = waiverTemplateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.waiverTemplate(templateId.toString()));

        template.setIsActive(false);
        template = waiverTemplateRepository.save(template);
        return mapToTemplateDto(template);
    }

    // ──────────────── Get active template ────────────────

    @Transactional
    public WaiverTemplateDto getActiveTemplate() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        WaiverTemplate template = waiverTemplateRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseGet(() -> createDefaultTemplate(tenantId));

        return mapToTemplateDto(template);
    }

    @Transactional(readOnly = true)
    public WaiverTemplateDto getTemplateById(UUID templateId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        WaiverTemplate template = waiverTemplateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.waiverTemplate(templateId.toString()));

        return mapToTemplateDto(template);
    }

    // ──────────────── Signing ────────────────

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

        Appointment appointment = appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(
                        request.getAppointmentId(), tenantId)
                .orElseThrow(() -> ResourceNotFoundException.appointment(request.getAppointmentId().toString()));

        if (appointment.getWaiverSigned()) {
            throw new BusinessRuleException("Waiver already signed for this appointment");
        }

        WaiverTemplate template = waiverTemplateRepository.findByIdAndTenantId(request.getTemplateId(), tenantId)
                .orElseThrow(() -> ResourceNotFoundException.waiverTemplate(request.getTemplateId().toString()));

        if (template.isStructured()) {
            validateStructuredFields(template, request.getFieldValues());
        } else {
            validateCheckboxes(template, request.getCheckboxValues());
        }

        SignedWaiver signedWaiver = SignedWaiver.builder()
                .tenantId(tenantId)
                .appointment(appointment)
                .client(appointment.getClient())
                .template(template)
                .signatureData(request.getSignatureData())
                .checkboxValues(serializeCheckboxValues(request.getCheckboxValues()))
                .fieldValues(request.getFieldValues())
                .clientIp(clientIp)
                .build();

        signedWaiver = signedWaiverRepository.save(signedWaiver);

        appointment.setWaiverSigned(true);
        appointment.setSignedWaiver(signedWaiver);
        appointmentRepository.save(appointment);

        return mapToSignedWaiverDto(signedWaiver);
    }

    // ──────────────── Consent token ────────────────

    @Transactional
    public String generateConsentToken(UUID appointmentId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Appointment appointment = appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.appointment(appointmentId.toString()));

        if (appointment.getConsentToken() != null) {
            return appointment.getConsentToken();
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        appointment.setConsentToken(token);
        appointmentRepository.save(appointment);
        return token;
    }

    // ──────────────── Signed documents list ────────────────

    @Transactional(readOnly = true)
    public Page<SignedWaiverDto> listSignedWaivers(int page, int size) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Pageable pageable = PageRequest.of(page, size);

        return signedWaiverRepository.findByTenantIdOrderBySignedAtDesc(tenantId, pageable)
                .map(this::mapToSignedWaiverDto);
    }

    // ──────────────── Validation ────────────────

    private void validateStructuredFields(WaiverTemplate template, Map<String, Object> fieldValues) {
        if (template.getFields() == null) return;

        for (ConsentField field : template.getFields()) {
            if (Boolean.TRUE.equals(field.getRequired())) {
                Object value = fieldValues != null ? fieldValues.get(field.getId()) : null;
                switch (field.getType()) {
                    case CHECKBOX:
                        if (!(value instanceof Boolean) || !(Boolean) value) {
                            throw new BusinessRuleException("Required checkbox not checked: " + field.getLabel());
                        }
                        break;
                    case SIGNATURE:
                    case TEXT_INPUT:
                    case INITIALS:
                    case DATE:
                        if (value == null || value.toString().isBlank()) {
                            throw new BusinessRuleException("Required field is empty: " + field.getLabel());
                        }
                        break;
                    default:
                        break;
                }
            }
        }
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

    // ──────────────── Default template ────────────────

    private WaiverTemplate createDefaultTemplate(UUID tenantId) {
        List<ConsentField> fields = List.of(
                ConsentField.builder().id("heading_1").type(ConsentField.FieldType.HEADING)
                        .content("ІНФОРМОВАНА ЗГОДА НА ПРОВЕДЕННЯ ТАТУЮВАННЯ").build(),
                ConsentField.builder().id("para_1").type(ConsentField.FieldType.PARAGRAPH)
                        .content("Я, нижчепідписаний(а), добровільно звертаюсь до тату-студії для нанесення татуювання та підтверджую наступне:").build(),
                ConsentField.builder().id("cb_age").type(ConsentField.FieldType.CHECKBOX)
                        .label("Підтверджую, що мені виповнилось 18 років").required(true).build(),
                ConsentField.builder().id("cb_health").type(ConsentField.FieldType.CHECKBOX)
                        .label("Підтверджую відсутність протипоказань (вагітність, годування, захворювання крові, алергії, шкірні захворювання)").required(true).build(),
                ConsentField.builder().id("cb_sober").type(ConsentField.FieldType.CHECKBOX)
                        .label("Підтверджую, що не перебуваю під впливом алкоголю або наркотичних речовин").required(true).build(),
                ConsentField.builder().id("cb_aftercare").type(ConsentField.FieldType.CHECKBOX)
                        .label("Ознайомлений(а) з правилами догляду за татуюванням та зобов'язуюсь їх дотримуватись").required(true).build(),
                ConsentField.builder().id("txt_allergies").type(ConsentField.FieldType.TEXT_INPUT)
                        .label("Алергії (якщо є)").required(false).build(),
                ConsentField.builder().id("cb_consent").type(ConsentField.FieldType.CHECKBOX)
                        .label("Даю згоду на проведення процедури татуювання").required(true).build(),
                ConsentField.builder().id("sig_client").type(ConsentField.FieldType.SIGNATURE)
                        .label("Підпис клієнта").required(true).build()
        );

        WaiverTemplate template = WaiverTemplate.builder()
                .tenantId(tenantId)
                .title("Інформована згода на татуювання")
                .fields(fields)
                .version(1)
                .isActive(true)
                .checkboxes(new ArrayList<>())
                .createdBy(SecurityUtils.getCurrentUserId())
                .build();

        return waiverTemplateRepository.save(template);
    }

    // ──────────────── Mappers ────────────────

    private WaiverTemplateDto mapToTemplateDto(WaiverTemplate template) {
        WaiverTemplateDto.WaiverTemplateDtoBuilder builder = WaiverTemplateDto.builder()
                .id(template.getId())
                .title(template.getTitle())
                .version(template.getVersion())
                .isActive(template.getIsActive())
                .createdAt(template.getCreatedAt());

        if (template.isStructured()) {
            builder.fields(template.getFields().stream()
                    .map(ConsentFieldDto::fromEntity)
                    .collect(Collectors.toList()));
        } else {
            builder.content(template.getContent());

            List<ConsentFieldDto> legacyFields = new java.util.ArrayList<>();
            if (template.getContent() != null && !template.getContent().isBlank()) {
                legacyFields.add(ConsentFieldDto.builder()
                        .id("legacy_content")
                        .type(ConsentField.FieldType.PARAGRAPH)
                        .content(template.getContent())
                        .build());
            }
            if (template.getCheckboxes() != null) {
                builder.checkboxes(template.getCheckboxes().stream()
                        .map(cb -> WaiverTemplateDto.CheckboxDto.builder()
                                .id(cb.getCheckboxId())
                                .label(cb.getLabel())
                                .isRequired(cb.getIsRequired())
                                .build())
                        .collect(Collectors.toList()));

                template.getCheckboxes().forEach(cb -> legacyFields.add(ConsentFieldDto.builder()
                        .id(cb.getCheckboxId())
                        .type(ConsentField.FieldType.CHECKBOX)
                        .label(cb.getLabel())
                        .required(cb.getIsRequired())
                        .build()));
            }
            legacyFields.add(ConsentFieldDto.builder()
                    .id("legacy_signature")
                    .type(ConsentField.FieldType.SIGNATURE)
                    .label("Підпис клієнта")
                    .required(true)
                    .build());

            builder.fields(legacyFields);
        }

        return builder.build();
    }

    private SignedWaiverDto mapToSignedWaiverDto(SignedWaiver signedWaiver) {
        WaiverTemplate template = signedWaiver.getTemplate();

        List<ConsentFieldDto> templateFields = null;
        if (template.isStructured() && template.getFields() != null) {
            templateFields = template.getFields().stream()
                    .map(ConsentFieldDto::fromEntity)
                    .collect(java.util.stream.Collectors.toList());
        } else if (template.getCheckboxes() != null && !template.getCheckboxes().isEmpty()) {
            templateFields = template.getCheckboxes().stream()
                    .map(cb -> ConsentFieldDto.builder()
                            .id(cb.getCheckboxId())
                            .type(ConsentField.FieldType.CHECKBOX)
                            .label(cb.getLabel())
                            .required(cb.getIsRequired())
                            .build())
                    .collect(java.util.stream.Collectors.toList());
        }

        return SignedWaiverDto.builder()
                .id(signedWaiver.getId())
                .appointmentId(signedWaiver.getAppointment().getId())
                .clientId(signedWaiver.getClient().getId())
                .clientName(signedWaiver.getClient().getFirstName() + " " + signedWaiver.getClient().getLastName())
                .templateId(template.getId())
                .templateTitle(template.getTitle())
                .signatureData(signedWaiver.getSignatureData())
                .checkboxValues(deserializeCheckboxValues(signedWaiver.getCheckboxValues()))
                .fieldValues(signedWaiver.getFieldValues())
                .templateFields(templateFields)
                .signedAt(signedWaiver.getSignedAt())
                .build();
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
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }
}
