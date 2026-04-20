package com.inkflow.crm.module.service.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.ArtistServicePricing;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.repository.ArtistServicePricingRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.module.service.dto.*;
import com.inkflow.crm.module.service.mapper.ServiceMapper;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ServiceService {

    private final ServiceRepository serviceRepository;
    private final ArtistServicePricingRepository artistServicePricingRepository;
    private final ServiceMapper serviceMapper;

    @Transactional(readOnly = true)
    public PageResult<ServiceDto> getAllServices(PageRequest pageRequest, Boolean active) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        if (active != null) {
            List<Service> services = serviceRepository.findByTenantIdAndIsActiveAndDeletedAtIsNull(tenantId, active);
            return new PageResult<>(serviceMapper.toDtoList(services), null);
        } else {
            Page<Service> page = serviceRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageRequest.toPageable());
            List<ServiceDto> data = serviceMapper.toDtoList(page.getContent());
            return new PageResult<>(data, PaginationDto.from(page));
        }
    }

    @Transactional(readOnly = true)
    public ServiceDetailDto getServiceById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Service service = serviceRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.service(id.toString()));
        return serviceMapper.toDetailDto(service);
    }

    @Transactional
    public ServiceDto createService(CreateServiceRequest request) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Service service = serviceMapper.toEntity(request);
        service.setTenantId(tenantId);

        service = serviceRepository.save(service);
        return serviceMapper.toDto(service);
    }

    @Transactional
    public ServiceDto updateService(UUID id, UpdateServiceRequest request) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Service service = serviceRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.service(id.toString()));

        serviceMapper.updateEntity(request, service);
        service = serviceRepository.save(service);
        return serviceMapper.toDto(service);
    }

    @Transactional
    public void deleteService(UUID id) {
        SecurityUtils.requireOwner();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Service service = serviceRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.service(id.toString()));

        service.softDelete();
        serviceRepository.save(service);
    }

    @Transactional(readOnly = true)
    public ServicePriceDto getServicePrice(UUID serviceId, UUID artistId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Service service = serviceRepository.findByIdAndTenantIdAndDeletedAtIsNull(serviceId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.service(serviceId.toString()));

        Optional<ArtistServicePricing> override = artistServicePricingRepository
                .findByStaffIdAndServiceId(artistId, serviceId);

        if (override.isPresent()) {
            ArtistServicePricing pricing = override.get();
            return ServicePriceDto.builder()
                    .serviceId(serviceId)
                    .artistId(artistId)
                    .price(pricing.getPrice())
                    .duration(pricing.getDuration() != null ? pricing.getDuration() : service.getDuration())
                    .isOverride(true)
                    .build();
        }

        return ServicePriceDto.builder()
                .serviceId(serviceId)
                .artistId(artistId)
                .price(service.getPrice())
                .duration(service.getDuration())
                .isOverride(false)
                .build();
    }
}
