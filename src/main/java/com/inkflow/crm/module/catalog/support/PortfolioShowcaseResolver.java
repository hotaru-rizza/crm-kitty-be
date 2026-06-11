package com.inkflow.crm.module.catalog.support;

import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.entity.TattooStatus;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PortfolioShowcaseResolver {

    private static final int DEFAULT_SHOWCASE_COUNT = 5;

    private final TattooRepository tattooRepository;

    public List<String> resolveUrls(UUID staffId) {
        return resolveUrlsBatch(List.of(staffId)).getOrDefault(staffId, List.of());
    }

    public Map<UUID, List<String>> resolveUrlsBatch(Collection<UUID> staffIds) {
        if (staffIds == null || staffIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> ids = staffIds.stream().distinct().toList();
        Map<UUID, List<String>> urlsByStaff = new HashMap<>();
        ids.forEach(id -> urlsByStaff.put(id, new ArrayList<>()));

        appendShowcaseUrls(ids, urlsByStaff);
        appendFallbackUrls(ids, urlsByStaff);

        return Map.copyOf(urlsByStaff);
    }

    private void appendShowcaseUrls(List<UUID> staffIds, Map<UUID, List<String>> urlsByStaff) {
        for (Tattoo tattoo : tattooRepository.findByStaffIdInAndShowcaseTrueOrderBySortOrderAsc(staffIds)) {
            urlsByStaff.get(tattoo.getStaffId()).add(tattoo.getImageUrl());
        }
    }

    private void appendFallbackUrls(List<UUID> staffIds, Map<UUID, List<String>> urlsByStaff) {
        List<UUID> withoutShowcase = staffIds.stream()
                .filter(staffId -> urlsByStaff.get(staffId).isEmpty())
                .toList();

        if (withoutShowcase.isEmpty()) {
            return;
        }

        Map<UUID, Integer> counts = new HashMap<>();
        for (Tattoo tattoo : tattooRepository.findByStaffIdInAndStatusOrderBySortOrderAscCreatedAtDesc(
                withoutShowcase, TattooStatus.READY)) {
            UUID staffId = tattoo.getStaffId();
            int count = counts.getOrDefault(staffId, 0);
            if (count >= DEFAULT_SHOWCASE_COUNT) {
                continue;
            }

            urlsByStaff.get(staffId).add(tattoo.getImageUrl());
            counts.put(staffId, count + 1);
        }
    }
}
