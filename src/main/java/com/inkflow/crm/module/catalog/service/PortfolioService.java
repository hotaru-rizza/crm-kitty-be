package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.catalog.dto.TattooDto;
import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.entity.TattooStatus;
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

    public List<TattooDto> getPortfolio(UUID staffId) {
        return tattooRepository.findByStaffIdOrderBySortOrderAscCreatedAtDesc(staffId)
                .stream()
                .map(TattooDto::from)
                .toList();
    }

    public List<TattooDto> uploadBulk(UUID staffId, List<String> imageUrls) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Staff not found: " + staffId));
        String authorName = staff.getFirstName() + " " + staff.getLastName();

        List<Tattoo> created = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            Tattoo t = new Tattoo();
            t.setStaffId(staffId);
            t.setSource(Tattoo.SOURCE_PORTFOLIO);
            t.setSourceId(UUID.randomUUID().toString());
            t.setStatus(TattooStatus.PROCESSING);
            t.setImageUrl(imageUrls.get(i));
            t.setThumbnailUrl(imageUrls.get(i));
            t.setAuthorName(authorName);
            t.setSortOrder(i);
            created.add(tattooRepository.save(t));
        }

        List<Long> ids = created.stream().map(Tattoo::getId).toList();
        portfolioProcessor.processImages(ids);

        return created.stream().map(TattooDto::from).toList();
    }

    public TattooDto update(Long tattooId, String description, List<String> tags) {
        Tattoo t = tattooRepository.findById(tattooId)
                .orElseThrow(() -> new RuntimeException("Tattoo not found: " + tattooId));

        if (description != null) {
            t.setDescription(description);
        }
        if (tags != null) {
            t.setTags(tags.toArray(new String[0]));
        }

        if (t.getDescription() != null && !t.getDescription().isBlank()) {
            try {
                String embedText = t.buildEmbedText();
                t.setEmbedding(embeddingService.embedPassage(embedText));
            } catch (Exception e) {
                log.warn("Failed to re-embed tattoo {}: {}", tattooId, e.getMessage());
            }
        }

        return TattooDto.from(tattooRepository.save(t));
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
    }

}
