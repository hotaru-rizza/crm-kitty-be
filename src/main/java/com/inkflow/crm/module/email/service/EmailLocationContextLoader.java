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

    public Optional<Location> resolveLocationForMacros(UUID locationId) {
        if (locationId != null) {
            return locationRepository.findByIdAndDeletedAtIsNull(locationId);
        }

        return resolveFallbackLocation();
    }

    public String resolveAddress(UUID locationId) {
        return resolveLocationForMacros(locationId)
                .map(Location::getAddress)
                .filter(this::hasText)
                .orElse("");
    }

    private Optional<Location> resolveFallbackLocation() {
        Optional<UUID> filter = LocationScope.resolveFilter(null);
        if (filter.isPresent()) {
            return locationRepository.findByIdAndDeletedAtIsNull(filter.get());
        }

        return locationRepository.findFirstByIsDefaultTrueAndDeletedAtIsNullOrderByCreatedAtAsc()
                .or(() -> locationRepository.findFirstByIsActiveTrueAndDeletedAtIsNull());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
