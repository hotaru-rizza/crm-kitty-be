package com.inkflow.crm.module.service.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.domain.entity.ArtistServicePricing;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.enums.PricingType;
import com.inkflow.crm.domain.repository.ArtistServicePricingRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.service.dto.*;
import com.inkflow.crm.module.service.mapper.ServiceMapper;
import com.inkflow.crm.module.service.support.ServiceDurationPolicy;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
public class ServiceService {

    private final ServiceRepository serviceRepository;
    private final ArtistServicePricingRepository artistServicePricingRepository;
    private final ServiceMapper serviceMapper;
    private final ServiceLookup serviceLookup;
    private final AuditRecorder auditRecorder;
    private final AuditLabelFormatter auditLabelFormatter;

    @Transactional(readOnly = true)
    public PageResult<ServiceDto> getAllServices(PageRequest pageRequest, Boolean active) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        if (active != null) {
            List<Service> services = serviceRepository.findByIsActiveAndDeletedAtIsNull( active);
            return new PageResult<>(serviceMapper.toDtoList(services), null);
        }

        Page<Service> page = serviceRepository.findByDeletedAtIsNull( pageRequest.toPageable());
        return new PageResult<>(serviceMapper.toDtoList(page.getContent()), PaginationDto.from(page));
    }

    @Transactional(readOnly = true)
    public ServiceDetailDto getServiceById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return serviceMapper.toDetailDto(serviceLookup.require(tenantId, id));
    }

    @Transactional
    public ServiceDto createService(CreateServiceRequest request) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Service service = serviceMapper.toEntity(request);
        service.setTenantId(tenantId);
        service.setDuration(ServiceDurationPolicy.resolveForCreate(service.getPricingType(), request.getDuration()));
        service = serviceRepository.save(service);

        log.info("Service created: tenantId={} serviceId={}", tenantId, service.getId());
        auditRecorder.record(
                AuditAction.CREATE,
                AuditEntityType.SERVICE,
                service.getId().toString(),
                auditLabelFormatter.catalogService(service.getTitle())
        );
        return serviceMapper.toDto(service);
    }

    @Transactional
    public ServiceDto updateService(UUID id, UpdateServiceRequest request) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Service service = serviceLookup.require(tenantId, id);
        serviceMapper.updateEntity(request, service);
        PricingType pricingType = request.getPricingType() != null
                ? PricingType.fromValue(request.getPricingType())
                : service.getPricingType();
        service.setDuration(ServiceDurationPolicy.resolveForUpdate(
                pricingType,
                request.getDuration(),
                service.getDuration()));
        service = serviceRepository.save(service);

        log.info("Service updated: tenantId={} serviceId={}", tenantId, id);
        auditRecorder.record(
                AuditAction.UPDATE,
                AuditEntityType.SERVICE,
                service.getId().toString(),
                auditLabelFormatter.catalogService(service.getTitle())
        );
        return serviceMapper.toDto(service);
    }

    @Transactional
    public void deleteService(UUID id) {
        SecurityUtils.requireOwner();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Service service = serviceLookup.require(tenantId, id);
        service.softDelete();
        serviceRepository.save(service);

        log.info("Service deleted: tenantId={} serviceId={}", tenantId, id);
        auditRecorder.record(
                AuditAction.DELETE,
                AuditEntityType.SERVICE,
                service.getId().toString(),
                auditLabelFormatter.catalogService(service.getTitle())
        );
    }

    @Transactional(readOnly = true)
    public ServicePriceDto getServicePrice(UUID serviceId, UUID artistId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Service service = serviceLookup.require(tenantId, serviceId);

        Optional<ArtistServicePricing> override = artistServicePricingRepository
                .findByStaffIdAndServiceId(artistId, serviceId);

        if (override.isPresent()) {
            return toPriceDto(serviceId, artistId, override.get(), service);
        }

        return ServicePriceDto.builder()
                .serviceId(serviceId)
                .artistId(artistId)
                .price(service.getPrice())
                .duration(service.getDuration())
                .isOverride(false)
                .build();
    }

    private ServicePriceDto toPriceDto(UUID serviceId, UUID artistId, ArtistServicePricing pricing, Service service) {
        return ServicePriceDto.builder()
                .serviceId(serviceId)
                .artistId(artistId)
                .price(pricing.getPrice())
                .duration(pricing.getDuration() != null ? pricing.getDuration() : service.getDuration())
                .isOverride(true)
                .build();
    }
}
