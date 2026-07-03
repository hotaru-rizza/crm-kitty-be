package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.module.catalog.dto.CatalogRetagResultDto;
import com.inkflow.crm.module.catalog.dto.CatalogSeedResultDto;
import com.inkflow.crm.module.catalog.dto.TattooDto;
import com.inkflow.crm.module.catalog.dto.TattooStyleDto;
import com.inkflow.crm.module.catalog.entity.TattooStatus;
import com.inkflow.crm.module.catalog.entity.TattooStyle;
import com.inkflow.crm.module.catalog.mapper.TattooMapper;
import com.inkflow.crm.module.catalog.repository.StyleCoverView;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
import com.inkflow.crm.module.catalog.repository.TattooStyleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TattooCatalogService {

    private final TattooRepository tattooRepository;
    private final TattooStyleRepository tattooStyleRepository;
    private final UnsplashSeederService seederService;
    private final TattooTaggerService taggerService;
    private final EmbeddingService embeddingService;
    private final TattooMapper tattooMapper;

    @Transactional(readOnly = true)
    public Page<TattooDto> getFeed(String tag, String author, String staffId, Pageable pageable) {
        return tattooRepository.findByTagOrAll(tag, author, staffId, TattooStatus.READY.name(), pageable)
                .map(tattooMapper::toDto);
    }

    @Transactional(readOnly = true)
    public TattooDto getById(Long id) {
        return tattooRepository.findById(id)
                .map(tattooMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Tattoo not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<TattooDto> search(String query, int limit) {
        float[] embedding = embeddingService.embed(query);
        return tattooMapper.toDtoList(
                tattooRepository.findByEmbedding(embeddingService.toPgVector(embedding), limit)
        );
    }

    @Transactional(readOnly = true)
    public List<TattooDto> getSimilar(Long id, int limit) {
        return tattooMapper.toDtoList(tattooRepository.findSimilar(id, limit));
    }

    @Transactional(readOnly = true)
    public List<TattooDto> getByIds(List<Long> ids) {
        return tattooMapper.toDtoList(tattooRepository.findAllById(ids));
    }

    @Transactional(readOnly = true)
    public List<TattooStyleDto> getStyles() {
        List<TattooStyle> styles = tattooStyleRepository.findByActiveTrueOrderBySortOrderAsc();
        Map<String, String> coverBySlug = resolveStyleCovers(styles);

        return styles.stream()
                .map(style -> tattooMapper.toStyleDto(style, coverBySlug.get(style.getSlug())))
                .toList();
    }

    private Map<String, String> resolveStyleCovers(List<TattooStyle> styles) {
        if (styles.isEmpty()) {
            return Map.of();
        }

        List<String> slugs = styles.stream().map(TattooStyle::getSlug).toList();
        return tattooRepository.findCoverUrlsByTags(slugs).stream()
                .collect(Collectors.toMap(StyleCoverView::getTag, StyleCoverView::getCoverUrl, (a, b) -> a));
    }

    @Transactional(readOnly = true)
    public Set<String> getAvailableTags() {
        return taggerService.getAvailableTags();
    }

    @Transactional
    public CatalogSeedResultDto seed() {
        int count = seederService.seed();
        long total = tattooRepository.count();
        log.info("Catalog seed completed: added={}, total={}", count, total);
        return new CatalogSeedResultDto(count, total);
    }

    @Transactional
    public CatalogRetagResultDto retag() {
        int updated = taggerService.retagAll();
        log.info("Catalog retag completed: updated={}", updated);
        return new CatalogRetagResultDto(updated);
    }
}
