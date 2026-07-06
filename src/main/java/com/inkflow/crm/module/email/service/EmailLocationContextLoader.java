package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.security.LocationScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailLocationContextLoader {

    private final LocationRepository locationRepository;

    public Optional<Location> resolveLocationForMacros() {
        Optional<UUID> filter = LocationScope.resolveFilter(null);
        if (filter.isPresent()) {
            return locationRepository.findByIdAndDeletedAtIsNull(filter.get());
        }
        return locationRepository.findByIsDefaultTrueAndDeletedAtIsNull()
                .or(() -> locationRepository.findFirstByIsActiveTrueAndDeletedAtIsNull());
    }

    public String resolveAddress() {
        return resolveLocationForMacros()
                .map(Location::getAddress)
                .filter(address -> address != null && !address.isBlank())
                .orElse("");
    }
}
