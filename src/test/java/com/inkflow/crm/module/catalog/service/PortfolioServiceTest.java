package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.catalog.dto.TattooDto;
import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.entity.TattooStatus;
import com.inkflow.crm.module.catalog.mapper.TattooMapper;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
import com.inkflow.crm.module.catalog.support.PortfolioShowcaseResolver;
import com.inkflow.crm.module.staff.service.StaffLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private TattooRepository tattooRepository;

    @Mock
    private StaffLookup staffLookup;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private PortfolioProcessor portfolioProcessor;

    @Mock
    private TattooMapper tattooMapper;

    @Mock
    private PortfolioShowcaseResolver showcaseResolver;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AuditLabelFormatter auditLabelFormatter;

    @InjectMocks
    private PortfolioService portfolioService;

    @BeforeEach
    void stubAuditLabels() {
        lenient().when(auditLabelFormatter.portfolio(any())).thenReturn("Портфоліо");
    }

    @Test
    void uploadBulk_createsProcessingTattoosAndTriggersProcessor() {
        UUID staffId = UUID.randomUUID();
        Staff staff = Staff.builder().id(staffId).firstName("Alex").lastName("Ink").build();

        when(staffLookup.requireStaff(staffId)).thenReturn(staff);
        java.util.concurrent.atomic.AtomicLong nextId = new java.util.concurrent.atomic.AtomicLong(1);
        when(tattooRepository.save(org.mockito.ArgumentMatchers.any(Tattoo.class))).thenAnswer(invocation -> {
            Tattoo tattoo = invocation.getArgument(0);
            tattoo.setId(nextId.getAndIncrement());
            return tattoo;
        });
        when(tattooMapper.toDtoList(anyList())).thenAnswer(invocation -> {
            List<Tattoo> tattoos = invocation.getArgument(0);
            return tattoos.stream()
                    .map(t -> new TattooDto(t.getId(), staffId, t.getStatus().name(), t.getImageUrl(),
                            t.getThumbnailUrl(), null, null, null, null, t.getAuthorName(), null,
                            t.getDescription(), t.getAltDescription(), List.of(), false))
                    .toList();
        });

        List<TattooDto> result = portfolioService.uploadBulk(staffId, List.of("https://cdn/a.jpg", "https://cdn/b.jpg"));

        assertEquals(2, result.size());
        verify(portfolioProcessor).processImages(List.of(1L, 2L));
    }

    @Test
    void setShowcase_rejectsMoreThanTenPhotos() {
        UUID staffId = UUID.randomUUID();
        when(staffLookup.requireStaff(staffId)).thenReturn(Staff.builder().id(staffId).build());

        List<Long> tooMany = java.util.stream.LongStream.rangeClosed(1, 11).boxed().toList();

        assertThrows(IllegalArgumentException.class, () -> portfolioService.setShowcase(staffId, tooMany));
    }

    @Test
    void delete_removesTattooForStaff() {
        UUID staffId = UUID.randomUUID();
        Tattoo tattoo = new Tattoo();
        tattoo.setId(5L);
        tattoo.setStaffId(staffId);

        when(staffLookup.requireStaff(staffId)).thenReturn(Staff.builder().id(staffId).build());
        when(tattooRepository.findById(5L)).thenReturn(Optional.of(tattoo));

        portfolioService.delete(staffId, 5L);

        verify(tattooRepository).delete(tattoo);
    }

    @Test
    void delete_rejectsTattooFromAnotherStaff() {
        UUID staffId = UUID.randomUUID();
        Tattoo tattoo = new Tattoo();
        tattoo.setId(5L);
        tattoo.setStaffId(UUID.randomUUID());

        when(staffLookup.requireStaff(staffId)).thenReturn(Staff.builder().id(staffId).build());
        when(tattooRepository.findById(5L)).thenReturn(Optional.of(tattoo));

        assertThrows(ResourceNotFoundException.class, () -> portfolioService.delete(staffId, 5L));
    }

    @Test
    void setShowcase_clearsOldAndMarksSelected() {
        UUID staffId = UUID.randomUUID();
        Tattoo previousShowcase = portfolioTattoo(1L, staffId, true);
        Tattoo selected = portfolioTattoo(2L, staffId, false);

        when(staffLookup.requireStaff(staffId)).thenReturn(Staff.builder().id(staffId).build());
        when(tattooRepository.findByStaffIdAndShowcaseTrueOrderBySortOrderAsc(staffId))
                .thenReturn(List.of(previousShowcase));
        when(tattooRepository.findAllByIdIn(List.of(2L))).thenReturn(List.of(selected));
        when(tattooRepository.findByStaffIdOrderBySortOrderAscCreatedAtDesc(staffId))
                .thenReturn(List.of(selected));
        when(tattooMapper.toDtoList(List.of(selected))).thenReturn(List.of());

        portfolioService.setShowcase(staffId, List.of(2L));

        assertFalse(previousShowcase.isShowcase());
        assertTrue(selected.isShowcase());
        verify(tattooRepository).saveAll(List.of(previousShowcase));
        verify(tattooRepository).saveAll(List.of(selected));
    }

    @Test
    void update_appliesDescriptionAndTriggersReEmbed() {
        UUID staffId = UUID.randomUUID();
        Tattoo tattoo = portfolioTattoo(5L, staffId, false);

        when(staffLookup.requireStaff(staffId)).thenReturn(Staff.builder().id(staffId).build());
        when(tattooRepository.findById(5L)).thenReturn(Optional.of(tattoo));
        when(embeddingService.embedPassage(anyString())).thenReturn(new float[]{0.1f, 0.2f});
        when(tattooRepository.save(tattoo)).thenReturn(tattoo);
        when(tattooMapper.toDto(tattoo)).thenReturn(new TattooDto(
                5L, staffId, TattooStatus.READY.name(), tattoo.getImageUrl(), null,
                null, null, null, null, null, null, "Updated", null, List.of(), false));

        portfolioService.update(staffId, 5L, "Updated", null);

        assertEquals("Updated", tattoo.getDescription());
        verify(embeddingService).embedPassage(anyString());
        verify(tattooRepository).save(tattoo);
    }

    @Test
    void update_appliesTagsWithoutReEmbedWhenDescriptionBlank() {
        UUID staffId = UUID.randomUUID();
        Tattoo tattoo = portfolioTattoo(6L, staffId, false);
        tattoo.setDescription("");

        when(staffLookup.requireStaff(staffId)).thenReturn(Staff.builder().id(staffId).build());
        when(tattooRepository.findById(6L)).thenReturn(Optional.of(tattoo));
        when(tattooRepository.save(tattoo)).thenReturn(tattoo);
        when(tattooMapper.toDto(tattoo)).thenReturn(new TattooDto(
                6L, staffId, TattooStatus.READY.name(), tattoo.getImageUrl(), null,
                null, null, null, null, null, null, "", null, List.of("blackwork"), false));

        portfolioService.update(staffId, 6L, null, List.of("blackwork"));

        assertArrayEquals(new String[]{"blackwork"}, tattoo.getTags());
        verify(embeddingService, never()).embedPassage(anyString());
    }

    private Tattoo portfolioTattoo(long id, UUID staffId, boolean showcase) {
        Tattoo tattoo = new Tattoo();
        tattoo.setId(id);
        tattoo.setStaffId(staffId);
        tattoo.setImageUrl("https://cdn.example.com/" + id + ".jpg");
        tattoo.setShowcase(showcase);
        return tattoo;
    }
}
