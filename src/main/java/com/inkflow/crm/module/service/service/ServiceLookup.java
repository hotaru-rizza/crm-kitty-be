package com.inkflow.crm.module.service.service;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ServiceLookup {

    private final ServiceRepository serviceRepository;

    public Service require(UUID tenantId, UUID serviceId) {
        return serviceRepository.findByIdAndDeletedAtIsNull(serviceId)
                .orElseThrow(() -> ResourceNotFoundException.service(serviceId.toString()));
    }
}
