package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.StaffFaqRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.catalog.dto.PublicArtistDto;
import com.inkflow.crm.module.catalog.support.PortfolioShowcaseResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicArtistServiceTest {

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private StaffFaqRepository staffFaqRepository;

    @Mock
    private PortfolioShowcaseResolver showcaseResolver;

    @Mock
    private InkflowProperties inkflowProperties;

    @InjectMocks
    private PublicArtistService publicArtistService;

    @Test
    void findAll_filtersByCity() {
        UUID staffId = UUID.randomUUID();
        Staff kyivArtist = publicArtist(staffId, "Kyiv, Ukraine");
        Staff lvivArtist = publicArtist(UUID.randomUUID(), "Lviv, Ukraine");

        when(staffRepository.findAllPublicArtists()).thenReturn(List.of(kyivArtist, lvivArtist));
        when(showcaseResolver.resolveUrlsBatch(List.of(staffId))).thenReturn(Map.of(staffId, List.of("url1")));
        when(staffFaqRepository.findByStaffIdInOrderByStaffIdAscSortOrderAsc(List.of(staffId))).thenReturn(List.of());
        when(inkflowProperties.defaultZoneId()).thenReturn(java.time.ZoneId.of("Europe/Kyiv"));

        List<PublicArtistDto> result = publicArtistService.findAll("kyiv", null, null);

        assertEquals(1, result.size());
        assertEquals(staffId, result.getFirst().id());
    }

    @Test
    void findById_returnsArtistWhenPublic() {
        UUID staffId = UUID.randomUUID();
        Staff staff = publicArtist(staffId, "Kyiv");

        when(staffRepository.findPublicArtistById(staffId)).thenReturn(Optional.of(staff));
        when(showcaseResolver.resolveUrlsBatch(List.of(staffId))).thenReturn(Map.of(staffId, List.of()));
        when(staffFaqRepository.findByStaffIdInOrderByStaffIdAscSortOrderAsc(List.of(staffId))).thenReturn(List.of());
        when(inkflowProperties.defaultZoneId()).thenReturn(java.time.ZoneId.of("Europe/Kyiv"));

        Optional<PublicArtistDto> result = publicArtistService.findById(staffId);

        assertTrue(result.isPresent());
        assertEquals(staffId, result.get().id());
    }

    @Test
    void findById_returnsEmptyWhenNotPublic() {
        UUID staffId = UUID.randomUUID();
        when(staffRepository.findPublicArtistById(staffId)).thenReturn(Optional.empty());

        Optional<PublicArtistDto> result = publicArtistService.findById(staffId);

        assertTrue(result.isEmpty());
    }

    @Test
    void findAll_filtersByStyle() {
        UUID tradId = UUID.randomUUID();
        UUID blackworkId = UUID.randomUUID();
        Staff tradArtist = publicArtist(tradId, "Kyiv, Ukraine");
        Staff blackworkArtist = publicArtist(blackworkId, "Kyiv, Ukraine");
        blackworkArtist.setSpecialization(new HashSet<>(Set.of("blackwork")));

        when(staffRepository.findAllPublicArtists()).thenReturn(List.of(tradArtist, blackworkArtist));
        when(showcaseResolver.resolveUrlsBatch(List.of(tradId))).thenReturn(Map.of(tradId, List.of()));
        when(staffFaqRepository.findByStaffIdInOrderByStaffIdAscSortOrderAsc(List.of(tradId))).thenReturn(List.of());
        when(inkflowProperties.defaultZoneId()).thenReturn(java.time.ZoneId.of("Europe/Kyiv"));

        List<PublicArtistDto> result = publicArtistService.findAll(null, "traditional", null);

        assertEquals(1, result.size());
        assertEquals(tradId, result.getFirst().id());
        assertTrue(result.getFirst().styles().contains("traditional"));
    }

    @Test
    void findAll_filtersBySearchName() {
        UUID alexId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Staff alex = publicArtist(alexId, "Kyiv, Ukraine");
        Staff other = publicArtist(otherId, "Lviv, Ukraine");
        other.setFirstName("Maria");
        other.setLastName("Rose");

        when(staffRepository.findAllPublicArtists()).thenReturn(List.of(alex, other));
        when(showcaseResolver.resolveUrlsBatch(List.of(alexId))).thenReturn(Map.of(alexId, List.of()));
        when(staffFaqRepository.findByStaffIdInOrderByStaffIdAscSortOrderAsc(List.of(alexId))).thenReturn(List.of());
        when(inkflowProperties.defaultZoneId()).thenReturn(java.time.ZoneId.of("Europe/Kyiv"));

        List<PublicArtistDto> result = publicArtistService.findAll(null, null, "alex ink");

        assertEquals(1, result.size());
        assertEquals(alexId, result.getFirst().id());
        assertEquals("Alex Ink", result.getFirst().name());
    }

    @Test
    void findAll_returnsEmptyWhenNoFiltersMatch() {
        Staff artist = publicArtist(UUID.randomUUID(), "Kyiv, Ukraine");
        when(staffRepository.findAllPublicArtists()).thenReturn(List.of(artist));

        List<PublicArtistDto> result = publicArtistService.findAll("odesa", null, null);

        assertTrue(result.isEmpty());
    }

    private Staff publicArtist(UUID id, String address) {
        Location location = Location.builder()
                .name("Studio")
                .address(address)
                .build();

        Staff staff = Staff.builder()
                .id(id)
                .firstName("Alex")
                .lastName("Ink")
                .isPublic(true)
                .build();
        staff.setSpecialization(new HashSet<>(Set.of("traditional")));
        staff.setLocations(new HashSet<>(Set.of(location)));
        return staff;
    }
}
