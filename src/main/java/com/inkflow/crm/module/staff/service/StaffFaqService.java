package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.StaffFaq;
import com.inkflow.crm.domain.repository.StaffFaqRepository;
import com.inkflow.crm.module.staff.dto.StaffFaqDto;
import com.inkflow.crm.module.staff.dto.UpsertFaqRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffFaqService {

    private final StaffLookup staffLookup;
    private final StaffFaqRepository staffFaqRepository;

    @Transactional(readOnly = true)
    public List<StaffFaqDto> getFaq(UUID staffId) {
        staffLookup.requireStaff(staffId);

        return staffFaqRepository.findByStaffIdOrderBySortOrderAsc(staffId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public List<StaffFaqDto> upsertFaq(UUID staffId, UpsertFaqRequest request) {
        Staff staff = staffLookup.requireStaff(staffId);

        staffFaqRepository.deleteByStaffId(staffId);
        staffFaqRepository.flush();

        List<StaffFaq> saved = new ArrayList<>();
        List<UpsertFaqRequest.FaqItem> items = request.getItems() != null ? request.getItems() : List.of();

        for (int i = 0; i < items.size(); i++) {
            UpsertFaqRequest.FaqItem item = items.get(i);
            StaffFaq faq = StaffFaq.builder()
                    .staffId(staffId)
                    .question(item.getQuestion())
                    .answer(item.getAnswer())
                    .sortOrder(i)
                    .build();
            saved.add(staffFaqRepository.save(faq));
        }

        log.info("Staff FAQ updated: staffId={} tenantId={} items={}", staffId, staff.getTenantId(), saved.size());
        return saved.stream().map(this::toDto).toList();
    }

    private StaffFaqDto toDto(StaffFaq faq) {
        return StaffFaqDto.builder()
                .id(faq.getId())
                .question(faq.getQuestion())
                .answer(faq.getAnswer())
                .sortOrder(faq.getSortOrder())
                .build();
    }
}
