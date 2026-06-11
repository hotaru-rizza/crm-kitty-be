package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.catalog.dto.TattooDto;
import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.entity.TattooStatus;
import com.inkflow.crm.module.catalog.mapper.TattooMapper;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
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
    private static final int DEFAULT_SHOWCASE_COUNT = 5;

    private final TattooRepository tattooRepository;
    private final StaffRepository staffRepository;
    private final EmbeddingService embeddingService;
    private final PortfolioProcessor portfolioProcessor;
    private final TattooMapper tattooMapper;

    public List<TattooDto> getPortfolio(UUID staffId) {
        return tattooMapper.toDtoList(
                tattooRepository.findByStaffIdOrderBySortOrderAscCreatedAtDesc(staffId)
        );
    }

    public List<TattooDto> uploadBulk(UUID staffId, List<String> imageUrls) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> ResourceNotFoundException.staff(staffId.toString()));

        String authorName = staff.getFirstName() + " " + staff.getLastName();
        List<Tattoo> created = new ArrayList<>();

        for (int i = 0; i < imageUrls.size(); i++) {
            Tattoo tattoo = new Tattoo();
            tattoo.setStaffId(staffId);
            tattoo.setSource(Tattoo.SOURCE_PORTFOLIO);
            tattoo.setSourceId(UUID.randomUUID().toString());
            tattoo.setStatus(TattooStatus.PROCESSING);
            tattoo.setImageUrl(imageUrls.get(i));
            tattoo.setThumbnailUrl(imageUrls.get(i));
            tattoo.setAuthorName(authorName);
            tattoo.setSortOrder(i);
            created.add(tattooRepository.save(tattoo));
        }

        List<Long> ids = created.stream().map(Tattoo::getId).toList();
        portfolioProcessor.processImages(ids);

        log.info("Portfolio bulk upload: staffId={} count={}", staffId, created.size());
        return tattooMapper.toDtoList(created);
    }

    public TattooDto update(Long tattooId, String description, List<String> tags) {
        Tattoo tattoo = tattooRepository.findById(tattooId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Tattoo not found: " + tattooId));

        if (description != null) {
            tattoo.setDescription(description);
        }
        if (tags != null) {
            tattoo.setTags(tags.toArray(new String[0]));
        }

        reEmbedIfNeeded(tattoo);
        tattoo = tattooRepository.save(tattoo);

        log.info("Portfolio tattoo updated: tattooId={}", tattooId);
        return tattooMapper.toDto(tattoo);
    }

    public List<TattooDto> setShowcase(UUID staffId, List<Long> tattooIds) {
        if (tattooIds.size() > MAX_SHOWCASE) {
            throw new IllegalArgumentException("Maximum " + MAX_SHOWCASE + " showcase photos allowed");
        }

        List<Tattoo> currentShowcase = tattooRepository.findByStaffIdAndShowcaseTrueOrderBySortOrderAsc(staffId);
        currentShowcase.forEach(t -> t.setShowcase(false));
        tattooRepository.saveAll(currentShowcase);

        if (!tattooIds.isEmpty()) {
            List<Tattoo> newShowcase = tattooRepository.findAllByIdIn(tattooIds);
            newShowcase.stream()
                    .filter(t -> staffId.equals(t.getStaffId()))
                    .forEach(t -> t.setShowcase(true));
            tattooRepository.saveAll(newShowcase);
        }

        log.info("Portfolio showcase updated: staffId={} count={}", staffId, tattooIds.size());
        return getPortfolio(staffId);
    }

    public List<String> getShowcaseUrls(UUID staffId) {
        List<Tattoo> showcase = tattooRepository.findByStaffIdAndShowcaseTrueOrderBySortOrderAsc(staffId);
        if (!showcase.isEmpty()) {
            return showcase.stream().map(Tattoo::getImageUrl).toList();
        }

        return tattooRepository.findByStaffIdOrderBySortOrderAscCreatedAtDesc(staffId).stream()
                .filter(t -> t.getStatus() == TattooStatus.READY)
                .limit(DEFAULT_SHOWCASE_COUNT)
                .map(Tattoo::getImageUrl)
                .toList();
    }

    public void delete(Long tattooId) {
        tattooRepository.deleteById(tattooId);
        log.info("Portfolio tattoo deleted: tattooId={}", tattooId);
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
