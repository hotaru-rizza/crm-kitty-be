package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.module.catalog.dto.TattooDto;
import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.entity.TattooStatus;
import com.inkflow.crm.module.catalog.mapper.TattooMapper;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
import com.inkflow.crm.module.catalog.support.PortfolioShowcaseResolver;
import com.inkflow.crm.module.staff.service.StaffLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private static final int MAX_SHOWCASE = 10;

    private final TattooRepository tattooRepository;
    private final StaffLookup staffLookup;
    private final EmbeddingService embeddingService;
    private final PortfolioProcessor portfolioProcessor;
    private final TattooMapper tattooMapper;
    private final PortfolioShowcaseResolver showcaseResolver;

    public List<TattooDto> getPortfolio(UUID staffId) {
        requireStaff(staffId);
        return tattooMapper.toDtoList(
                tattooRepository.findByStaffIdOrderBySortOrderAscCreatedAtDesc(staffId)
        );
    }

    public List<TattooDto> uploadBulk(UUID staffId, List<String> imageUrls) {
        Staff staff = requireStaff(staffId);
        String authorName = staff.getFirstName() + " " + staff.getLastName();
        List<Tattoo> created = new ArrayList<>();

        for (int index = 0; index < imageUrls.size(); index++) {
            Tattoo tattoo = new Tattoo();
            tattoo.setStaffId(staffId);
            tattoo.setSource(Tattoo.SOURCE_PORTFOLIO);
            tattoo.setSourceId(UUID.randomUUID().toString());
            tattoo.setStatus(TattooStatus.PROCESSING);
            tattoo.setImageUrl(imageUrls.get(index));
            tattoo.setThumbnailUrl(imageUrls.get(index));
            tattoo.setAuthorName(authorName);
            tattoo.setSortOrder(index);
            created.add(tattooRepository.save(tattoo));
        }

        List<Long> ids = created.stream().map(Tattoo::getId).toList();
        portfolioProcessor.processImages(ids);

        log.info("Portfolio bulk upload: staffId={} count={}", staffId, created.size());
        return tattooMapper.toDtoList(created);
    }

    public TattooDto update(UUID staffId, Long tattooId, String description, List<String> tags) {
        Tattoo tattoo = requireTattooForStaff(staffId, tattooId);

        if (description != null) {
            tattoo.setDescription(description);
        }
        if (tags != null) {
            tattoo.setTags(tags.toArray(new String[0]));
        }

        reEmbedIfNeeded(tattoo);
        tattoo = tattooRepository.save(tattoo);

        log.info("Portfolio tattoo updated: staffId={} tattooId={}", staffId, tattooId);
        return tattooMapper.toDto(tattoo);
    }

    public List<TattooDto> setShowcase(UUID staffId, List<Long> tattooIds) {
        requireStaff(staffId);

        if (tattooIds.size() > MAX_SHOWCASE) {
            throw new IllegalArgumentException("Maximum " + MAX_SHOWCASE + " showcase photos allowed");
        }

        List<Tattoo> currentShowcase = tattooRepository.findByStaffIdAndShowcaseTrueOrderBySortOrderAsc(staffId);
        currentShowcase.forEach(tattoo -> tattoo.setShowcase(false));
        tattooRepository.saveAll(currentShowcase);

        if (!tattooIds.isEmpty()) {
            List<Tattoo> newShowcase = tattooRepository.findAllByIdIn(tattooIds);
            newShowcase.stream()
                    .filter(tattoo -> staffId.equals(tattoo.getStaffId()))
                    .forEach(tattoo -> tattoo.setShowcase(true));
            tattooRepository.saveAll(newShowcase);
        }

        log.info("Portfolio showcase updated: staffId={} count={}", staffId, tattooIds.size());
        return getPortfolio(staffId);
    }

    public List<String> getShowcaseUrls(UUID staffId) {
        return showcaseResolver.resolveUrls(staffId);
    }

    public void delete(UUID staffId, Long tattooId) {
        Tattoo tattoo = requireTattooForStaff(staffId, tattooId);
        tattooRepository.delete(tattoo);
        log.info("Portfolio tattoo deleted: staffId={} tattooId={}", staffId, tattooId);
    }

    private Staff requireStaff(UUID staffId) {
        return staffLookup.requireStaff(staffId);
    }

    private Tattoo requireTattooForStaff(UUID staffId, Long tattooId) {
        requireStaff(staffId);

        Tattoo tattoo = tattooRepository.findById(tattooId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Tattoo not found: " + tattooId));

        if (!staffId.equals(tattoo.getStaffId())) {
            throw new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Tattoo not found: " + tattooId);
        }

        return tattoo;
    }

    private void reEmbedIfNeeded(Tattoo tattoo) {
        if (tattoo.getDescription() == null || tattoo.getDescription().isBlank()) {
            return;
        }

        try {
            tattoo.setEmbedding(embeddingService.embedPassage(tattoo.buildEmbedText()));
        } catch (Exception e) {
            log.warn("Failed to re-embed tattoo {}: {}", tattoo.getId(), e.getMessage());
        }
    }
}
