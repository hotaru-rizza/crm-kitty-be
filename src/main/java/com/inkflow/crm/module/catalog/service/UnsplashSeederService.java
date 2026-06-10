package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.module.catalog.dto.UnsplashApiDto;
import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnsplashSeederService {

    private static final long PAGE_DELAY_MS = 200L;

    private final TattooRepository tattooRepository;
    private final EmbeddingService embeddingService;
    private final TattooTaggerService taggerService;

    @Value("${unsplash.api.key}")
    private String apiKey;

    @Value("${unsplash.api.base-url}")
    private String baseUrl;

    @Value("${unsplash.seed.query}")
    private String query;

    @Value("${unsplash.seed.pages}")
    private int pages;

    @Value("${unsplash.seed.per-page}")
    private int perPage;

    private final RestClient restClient = RestClient.create();

    public int seed() {
        int saved = 0;
        for (int page = 1; page <= pages; page++) {
            try {
                UnsplashApiDto.SearchResponse response = restClient.get()
                        .uri(baseUrl + "/search/photos?query={q}&page={p}&per_page={pp}&order_by=relevant",
                                query, page, perPage)
                        .header("Authorization", "Client-ID " + apiKey)
                        .retrieve()
                        .body(UnsplashApiDto.SearchResponse.class);

                if (response == null || response.results() == null) break;

                for (UnsplashApiDto.Photo photo : response.results()) {
                    if (tattooRepository.existsBySourceAndSourceId(Tattoo.SOURCE_UNSPLASH, photo.id())) continue;
                    tattooRepository.save(toEntity(photo));
                    saved++;
                }
                log.info("Seeded page {}/{}, total saved so far: {}", page, pages, saved);
                Thread.sleep(PAGE_DELAY_MS);
            } catch (Exception e) {
                log.error("Failed on page {}: {}", page, e.getMessage());
            }
        }
        log.info("Seeding complete. Total new records: {}", saved);
        return saved;
    }

    private Tattoo toEntity(UnsplashApiDto.Photo photo) {
        Tattoo t = new Tattoo();
        t.setSource(Tattoo.SOURCE_UNSPLASH);
        t.setSourceId(photo.id());
        t.setImageUrl(photo.urls().regular());
        t.setThumbnailUrl(photo.urls().small());
        t.setWidth(photo.width());
        t.setHeight(photo.height());
        t.setBlurHash(photo.blurHash());
        t.setDominantColor(photo.color());
        t.setAuthorName(photo.user().name());
        t.setAuthorUrl(photo.user().links().html() + "?utm_source=inkflow&utm_medium=referral");
        t.setDescription(photo.description());
        t.setAltDescription(photo.altDescription());
        String[] tags = taggerService.tagFromText(photo.altDescription(), photo.description());
        t.setTags(tags);
        t.setEmbedding(embeddingService.embedPassage(t.buildEmbedText()));
        return t;
    }
}
