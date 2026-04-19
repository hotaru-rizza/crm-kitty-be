package com.inkflow.crm.module.waiver.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.domain.entity.*;
import com.inkflow.crm.domain.repository.*;
import com.inkflow.crm.module.waiver.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicConsentService {

    private final AppointmentRepository appointmentRepository;
    private final WaiverTemplateRepository waiverTemplateRepository;
    private final SignedWaiverRepository signedWaiverRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public PublicConsentDto getConsentForm(String token) {
        Appointment appointment = appointmentRepository.findByConsentTokenAndDeletedAtIsNull(token)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPOINTMENT_NOT_FOUND,
                        "Invalid consent link"));

        WaiverTemplate template = waiverTemplateRepository.findByTenantIdAndIsActiveTrue(appointment.getTenantId())
                .orElse(null);

        if (template == null) {
            throw new BusinessRuleException("No active consent template configured");
        }

        Client client = appointment.getClient();
        String clientName = client.getFirstName() + " " + client.getLastName();

        String studioName = tenantRepository.findById(appointment.getTenantId())
                .map(Tenant::getName)
                .orElse("Studio");

        boolean alreadySigned = appointment.getWaiverSigned();

        PublicConsentDto.PublicConsentDtoBuilder builder = PublicConsentDto.builder()
                .templateTitle(template.getTitle())
                .clientName(clientName)
                .appointmentTime(appointment.getStartTime())
                .studioName(studioName)
                .alreadySigned(alreadySigned);

        if (template.isStructured()) {
            builder.fields(template.getFields().stream()
                    .map(ConsentFieldDto::fromEntity)
                    .collect(Collectors.toList()));
        } else {
            // Convert legacy to structured for the public UI
            var fields = new ArrayList<ConsentFieldDto>();
            if (template.getContent() != null && !template.getContent().isBlank()) {
                fields.add(ConsentFieldDto.builder()
                        .id("legacy_content")
                        .type(ConsentField.FieldType.PARAGRAPH)
                        .content(template.getContent())
                        .build());
            }
            if (template.getCheckboxes() != null) {
                for (WaiverCheckbox cb : template.getCheckboxes()) {
                    fields.add(ConsentFieldDto.builder()
                            .id(cb.getCheckboxId())
                            .type(ConsentField.FieldType.CHECKBOX)
                            .label(cb.getLabel())
                            .required(cb.getIsRequired())
                            .build());
                }
            }
            fields.add(ConsentFieldDto.builder()
                    .id("legacy_signature")
                    .type(ConsentField.FieldType.SIGNATURE)
                    .label("Підпис клієнта")
                    .required(true)
                    .build());
            builder.fields(fields);
        }

        return builder.build();
    }

    @Transactional
    public void signConsent(String token, PublicSignRequest request, String clientIp) {
        Appointment appointment = appointmentRepository.findByConsentTokenAndDeletedAtIsNull(token)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPOINTMENT_NOT_FOUND,
                        "Invalid consent link"));

        if (appointment.getWaiverSigned()) {
            throw new BusinessRuleException("Consent already signed for this appointment");
        }

        WaiverTemplate template = waiverTemplateRepository.findByTenantIdAndIsActiveTrue(appointment.getTenantId())
                .orElseThrow(() -> new BusinessRuleException("No active consent template"));

        SignedWaiver signedWaiver = SignedWaiver.builder()
                .tenantId(appointment.getTenantId())
                .appointment(appointment)
                .client(appointment.getClient())
                .template(template)
                .signatureData(request.getSignatureData())
                .fieldValues(request.getFieldValues())
                .clientIp(clientIp)
                .build();

        signedWaiverRepository.save(signedWaiver);

        appointment.setWaiverSigned(true);
        appointment.setSignedWaiver(signedWaiver);
        appointmentRepository.save(appointment);
    }
}
