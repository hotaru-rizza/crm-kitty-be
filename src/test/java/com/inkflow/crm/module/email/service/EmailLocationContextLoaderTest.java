package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.repository.LocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailLocationContextLoaderTest {

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private EmailLocationContextLoader emailLocationContextLoader;

    @Test
    void shouldResolveAddressFromFirstDefaultLocationWhenMultipleDefaultsExistInData() {
        Location defaultLocation = Location.builder()
                .id(UUID.randomUUID())
                .address("Khreshchatyk 1")
                .isDefault(true)
                .build();

        when(locationRepository.findFirstByIsDefaultTrueAndDeletedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(Optional.of(defaultLocation));

        assertThat(emailLocationContextLoader.resolveAddress(null)).isEqualTo("Khreshchatyk 1");
    }
}
