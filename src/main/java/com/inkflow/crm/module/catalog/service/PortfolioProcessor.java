package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.module.catalog.dto.TattooAnalysisDto;
import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.entity.TattooStatus;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioProcessor {

    private final TattooRepository tattooRepository;
    private final VisionService visionService;
    private final TattooTaggerService taggerService;
    private final EmbeddingService embeddingService;

    @Async
    public void processImages(List<Long> tattooIds) {
        for (Long id : tattooIds) {
            Tattoo t = tattooRepository.findById(id).orElse(null);
            if (t == null) continue;

            try {
                TattooAnalysisDto analysis = visionService.analyze(t.getImageUrl());

                if (analysis != null) {
                    t.setDescription(analysis.description());
                    t.setAltDescription(analysis.altDescription());
                    if (analysis.tags() != null && !analysis.tags().isEmpty()) {
                        t.setTags(analysis.tags().toArray(new String[0]));
                    } else {
                        String[] keywordTags = taggerService.tagFromText(analysis.description(), analysis.altDescription());
                        t.setTags(keywordTags);
                    }
                } else {
                    t.setTags(new String[0]);
                }

                String embedText = t.buildEmbedText();
                if (!embedText.isBlank()) {
                    t.setEmbedding(embeddingService.embedPassage(embedText));
                }

                t.setStatus(TattooStatus.READY);
                log.info("Processed portfolio tattoo {} for staff {}", t.getId(), t.getStaffId());
            } catch (Exception e) {
                log.error("Failed to process tattoo {}: {}", t.getId(), e.getMessage());
                t.setStatus(TattooStatus.FAILED);
            }
            tattooRepository.save(t);
        }
    }

}
