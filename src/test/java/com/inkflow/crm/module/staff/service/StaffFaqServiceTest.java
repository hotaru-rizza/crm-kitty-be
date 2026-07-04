package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.StaffFaq;
import com.inkflow.crm.domain.repository.StaffFaqRepository;
import com.inkflow.crm.module.staff.dto.StaffFaqDto;
import com.inkflow.crm.module.staff.dto.UpsertFaqRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffFaqServiceTest {

    @Mock
    private StaffLookup staffLookup;

    @Mock
    private StaffFaqRepository staffFaqRepository;

    @Captor
    private ArgumentCaptor<StaffFaq> faqCaptor;

    @InjectMocks
    private StaffFaqService staffFaqService;

    @Test
    void shouldReturnOrderedItemsWhenGettingFaq() {
        UUID staffId = UUID.randomUUID();
        when(staffLookup.requireStaff(staffId)).thenReturn(Staff.builder().id(staffId).build());
        when(staffFaqRepository.findByStaffIdOrderBySortOrderAsc(staffId)).thenReturn(List.of(
                StaffFaq.builder()
                        .id(UUID.randomUUID())
                        .question("How long?")
                        .answer("2 hours")
                        .sortOrder(0)
                        .build()
        ));

        List<StaffFaqDto> faq = staffFaqService.getFaq(staffId);

        assertEquals(1, faq.size());
        assertEquals("How long?", faq.getFirst().getQuestion());
        assertEquals(0, faq.getFirst().getSortOrder());
    }

    @Test
    void shouldReplaceAllItemsWhenUpsertingFaq() {
        UUID staffId = UUID.randomUUID();
        when(staffLookup.requireStaff(staffId)).thenReturn(Staff.builder().id(staffId).tenantId(UUID.randomUUID()).build());
        when(staffFaqRepository.findByStaffIdOrderBySortOrderAsc(staffId)).thenReturn(List.of());
        when(staffFaqRepository.save(any())).thenAnswer(invocation -> {
            StaffFaq faq = invocation.getArgument(0);
            if (faq.getId() == null) {
                faq.setId(UUID.randomUUID());
            }
            return faq;
        });

        UpsertFaqRequest request = UpsertFaqRequest.builder()
                .items(List.of(new UpsertFaqRequest.FaqItem("Price?", "From $100")))
                .build();

        List<StaffFaqDto> saved = staffFaqService.upsertFaq(staffId, request);

        var inOrder = inOrder(staffFaqRepository);
        inOrder.verify(staffFaqRepository).findByStaffIdOrderBySortOrderAsc(staffId);
        inOrder.verify(staffFaqRepository).deleteAll(List.of());
        inOrder.verify(staffFaqRepository).flush();
        inOrder.verify(staffFaqRepository).save(faqCaptor.capture());

        StaffFaq persisted = faqCaptor.getValue();
        assertEquals(staffId, persisted.getStaffId());
        assertEquals("Price?", persisted.getQuestion());
        assertEquals("From $100", persisted.getAnswer());
        assertEquals(0, persisted.getSortOrder());
        assertEquals(1, saved.size());
    }

    @Test
    void shouldClearFaqWhenUpsertingWithNullItems() {
        UUID staffId = UUID.randomUUID();
        when(staffLookup.requireStaff(staffId)).thenReturn(Staff.builder().id(staffId).tenantId(UUID.randomUUID()).build());
        when(staffFaqRepository.findByStaffIdOrderBySortOrderAsc(staffId)).thenReturn(List.of());

        UpsertFaqRequest request = UpsertFaqRequest.builder().items(null).build();

        List<StaffFaqDto> saved = staffFaqService.upsertFaq(staffId, request);

        verify(staffFaqRepository).findByStaffIdOrderBySortOrderAsc(staffId);
        verify(staffFaqRepository).deleteAll(List.of());
        verify(staffFaqRepository, never()).save(any());
        assertTrue(saved.isEmpty());
    }

    @Test
    void shouldPersistSortOrderWhenUpsertingMultipleItems() {
        UUID staffId = UUID.randomUUID();
        when(staffLookup.requireStaff(staffId)).thenReturn(Staff.builder().id(staffId).tenantId(UUID.randomUUID()).build());
        when(staffFaqRepository.findByStaffIdOrderBySortOrderAsc(staffId)).thenReturn(List.of());
        when(staffFaqRepository.save(any())).thenAnswer(invocation -> {
            StaffFaq faq = invocation.getArgument(0);
            faq.setId(UUID.randomUUID());
            return faq;
        });

        UpsertFaqRequest request = UpsertFaqRequest.builder()
                .items(List.of(
                        new UpsertFaqRequest.FaqItem("First?", "A1"),
                        new UpsertFaqRequest.FaqItem("Second?", "A2")
                ))
                .build();

        staffFaqService.upsertFaq(staffId, request);

        verify(staffFaqRepository, org.mockito.Mockito.times(2)).save(faqCaptor.capture());
        List<StaffFaq> saved = faqCaptor.getAllValues();
        assertEquals(0, saved.get(0).getSortOrder());
        assertEquals(1, saved.get(1).getSortOrder());
        assertEquals("First?", saved.get(0).getQuestion());
        assertEquals("Second?", saved.get(1).getQuestion());
    }
}
