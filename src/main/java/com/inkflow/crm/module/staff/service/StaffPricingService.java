package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.domain.entity.ArtistServicePricing;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.ArtistServicePricingRepository;
import com.inkflow.crm.module.service.service.ServiceLookup;
import com.inkflow.crm.module.staff.dto.StaffServiceDto;
import com.inkflow.crm.module.staff.dto.UpdateStaffServicesRequest;
import com.inkflow.crm.module.staff.mapper.StaffPricingMapper;
import com.inkflow.crm.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
public class StaffPricingService {

    private final StaffLookup staffLookup;
    private final ServiceLookup serviceLookup;
    private final ArtistServicePricingRepository artistServicePricingRepository;
    private final StaffPricingMapper staffPricingMapper;
    private final EntityManager entityManager;
    private final AuditRecorder auditRecorder;
    private final AuditLabelFormatter auditLabelFormatter;

    private void auditStaffMutation(Staff staff, String details) {
        auditRecorder.record(
                AuditAction.UPDATE,
                AuditEntityType.STAFF,
                staff.getId().toString(),
                auditLabelFormatter.staff(staff),
                null,
                details
        );
    }

    @Transactional(readOnly = true)
    public List<StaffServiceDto> getStaffServices(UUID staffId) {
        staffLookup.requireStaff(staffId);

        return artistServicePricingRepository.findByStaffId(staffId).stream()
                .filter(pricing -> pricing.getService().getDeletedAt() == null)
                .map(staffPricingMapper::toDto)
                .toList();
    }

    @Transactional
    public List<StaffServiceDto> updateStaffServices(UUID staffId, UpdateStaffServicesRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Staff staff = staffLookup.requireStaff(staffId);

        artistServicePricingRepository.deleteByStaffId(staffId);
        entityManager.flush();

        List<ArtistServicePricing> saved = new ArrayList<>();
        for (UpdateStaffServicesRequest.ServiceAssignment assignment : request.getServices()) {
            Service service = serviceLookup.require(tenantId, assignment.getServiceId());
            ArtistServicePricing pricing = staffPricingMapper.toEntity(
                    staff, service, assignment.getCustomPrice(), assignment.getCustomDuration());

            saved.add(artistServicePricingRepository.save(pricing));
        }

        log.info("Staff services updated: tenantId={} staffId={} count={}", tenantId, staffId, saved.size());
        auditStaffMutation(staff, "Послуги оновлено");
        return saved.stream().map(staffPricingMapper::toDto).toList();
    }

    @Transactional
    public StaffServiceDto addServiceToStaff(
            UUID staffId,
            UUID serviceId,
            BigDecimal customPrice,
            Integer customDuration) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Staff staff = staffLookup.requireStaff(staffId);
        Service service = serviceLookup.require(tenantId, serviceId);

        if (artistServicePricingRepository.findByStaffIdAndServiceId(staffId, serviceId).isPresent()) {
            throw new BusinessRuleException("Service is already assigned to this staff member");
        }

        ArtistServicePricing pricing = staffPricingMapper.toEntity(staff, service, customPrice, customDuration);
        pricing = artistServicePricingRepository.save(pricing);

        log.info("Staff service added: tenantId={} staffId={} serviceId={}", tenantId, staffId, serviceId);
        auditStaffMutation(staff, "Додано послугу: " + service.getTitle());
        return staffPricingMapper.toDto(pricing);
    }

    @Transactional
    public void removeServiceFromStaff(UUID staffId, UUID serviceId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Staff staff = staffLookup.requireStaff(staffId);

        ArtistServicePricing pricing = requireAssignment(staffId, serviceId);
        String serviceTitle = pricing.getService().getTitle();
        artistServicePricingRepository.delete(pricing);

        log.info("Staff service removed: tenantId={} staffId={} serviceId={}", tenantId, staffId, serviceId);
        auditStaffMutation(staff, "Прибрано послугу: " + serviceTitle);
    }

    @Transactional
    public StaffServiceDto updateStaffServicePricing(
            UUID staffId,
            UUID serviceId,
            BigDecimal customPrice,
            Integer customDuration) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Staff staff = staffLookup.requireStaff(staffId);

        ArtistServicePricing pricing = requireAssignment(staffId, serviceId);
        applyPricingUpdate(pricing, customPrice, customDuration);

        pricing = artistServicePricingRepository.save(pricing);
        log.info("Staff service pricing updated: tenantId={} staffId={} serviceId={}", tenantId, staffId, serviceId);
        auditStaffMutation(staff, "Оновлено ціни: " + pricing.getService().getTitle());
        return staffPricingMapper.toDto(pricing);
    }

    private ArtistServicePricing requireAssignment(UUID staffId, UUID serviceId) {
        return artistServicePricingRepository.findByStaffIdAndServiceId(staffId, serviceId)
                .orElseThrow(() -> new BusinessRuleException("Service is not assigned to this staff member"));
    }

    private void applyPricingUpdate(ArtistServicePricing pricing, BigDecimal customPrice, Integer customDuration) {
        if (customPrice != null) {
            pricing.setPrice(customPrice);
        }
        if (customDuration != null) {
            pricing.setDuration(customDuration);
        }
    }
}
