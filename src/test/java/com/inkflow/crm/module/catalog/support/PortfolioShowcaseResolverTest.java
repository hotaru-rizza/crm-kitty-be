package com.inkflow.crm.module.catalog.support;

import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.entity.TattooStatus;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioShowcaseResolverTest {

    @Mock
    private TattooRepository tattooRepository;

    @InjectMocks
    private PortfolioShowcaseResolver resolver;

    @Test
    void prefersShowcasePhotosOverPortfolioFallback() {
        UUID staffId = UUID.randomUUID();
        Tattoo showcase = tattoo("https://showcase.jpg", staffId, true);

        when(tattooRepository.findByStaffIdInAndShowcaseTrueOrderBySortOrderAsc(List.of(staffId)))
                .thenReturn(List.of(showcase));

        Map<UUID, List<String>> urls = resolver.resolveUrlsBatch(List.of(staffId));

        assertEquals(List.of("https://showcase.jpg"), urls.get(staffId));
    }

    @Test
    void fallsBackToReadyPortfolioWhenShowcaseMissing() {
        UUID staffId = UUID.randomUUID();
        Tattoo ready = tattoo("https://portfolio.jpg", staffId, false);
        ready.setStatus(TattooStatus.READY);

        when(tattooRepository.findByStaffIdInAndShowcaseTrueOrderBySortOrderAsc(List.of(staffId)))
                .thenReturn(List.of());
        when(tattooRepository.findByStaffIdInAndStatusOrderBySortOrderAscCreatedAtDesc(
                List.of(staffId), TattooStatus.READY))
                .thenReturn(List.of(ready));

        Map<UUID, List<String>> urls = resolver.resolveUrlsBatch(List.of(staffId));

        assertEquals(List.of("https://portfolio.jpg"), urls.get(staffId));
    }

    private Tattoo tattoo(String url, UUID staffId, boolean showcase) {
        Tattoo tattoo = new Tattoo();
        tattoo.setImageUrl(url);
        tattoo.setStaffId(staffId);
        tattoo.setShowcase(showcase);
        tattoo.setStatus(TattooStatus.READY);
        return tattoo;
    }
}
