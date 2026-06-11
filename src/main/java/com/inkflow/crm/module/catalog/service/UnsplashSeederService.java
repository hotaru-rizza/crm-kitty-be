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
            PageSeedResult result = seedPage(page, saved);

            if (result.shouldStop()) {
                break;
            }

            saved += result.savedCount();
        }

        log.info("Seeding complete. Total new records: {}", saved);
        return saved;
    }

    private PageSeedResult seedPage(int page, int savedSoFar) {
        try {
            UnsplashApiDto.SearchResponse response = fetchPage(page);

            if (response == null || response.results() == null) {
                return PageSeedResult.stop();
            }

            int pageSaved = savePhotos(response);

            log.info("Seeded page {}/{}, total saved so far: {}", page, pages, savedSoFar + pageSaved);
            Thread.sleep(PAGE_DELAY_MS);

            return PageSeedResult.saved(pageSaved);
        } catch (Exception e) {
            log.error("Failed on page {}: {}", page, e.getMessage());
            return PageSeedResult.saved(0);
        }
    }

    private record PageSeedResult(int savedCount, boolean shouldStop) {
        static PageSeedResult stop() {
            return new PageSeedResult(0, true);
        }

        static PageSeedResult saved(int count) {
            return new PageSeedResult(count, false);
        }
    }

    private UnsplashApiDto.SearchResponse fetchPage(int page) {
        return restClient.get()
                .uri(baseUrl + "/search/photos?query={q}&page={p}&per_page={pp}&order_by=relevant",
                        query, page, perPage)
                .header("Authorization", "Client-ID " + apiKey)
                .retrieve()
                .body(UnsplashApiDto.SearchResponse.class);
    }

    private int savePhotos(UnsplashApiDto.SearchResponse response) {
        int saved = 0;

        for (UnsplashApiDto.Photo photo : response.results()) {
            if (tattooRepository.existsBySourceAndSourceId(Tattoo.SOURCE_UNSPLASH, photo.id())) {
                continue;
            }

            tattooRepository.save(toEntity(photo));
            saved++;
        }

        return saved;
    }

    private Tattoo toEntity(UnsplashApiDto.Photo photo) {
        Tattoo tattoo = new Tattoo();
        tattoo.setSource(Tattoo.SOURCE_UNSPLASH);
        tattoo.setSourceId(photo.id());
        tattoo.setImageUrl(photo.urls().regular());
        tattoo.setThumbnailUrl(photo.urls().small());
        tattoo.setWidth(photo.width());
        tattoo.setHeight(photo.height());
        tattoo.setBlurHash(photo.blurHash());
        tattoo.setDominantColor(photo.color());
        tattoo.setAuthorName(photo.user().name());
        tattoo.setAuthorUrl(photo.user().links().html() + "?utm_source=inkflow&utm_medium=referral");
        tattoo.setDescription(photo.description());
        tattoo.setAltDescription(photo.altDescription());

        String[] tags = taggerService.tagFromText(photo.altDescription(), photo.description());
        tattoo.setTags(tags);
        tattoo.setEmbedding(embeddingService.embedPassage(tattoo.buildEmbedText()));

        return tattoo;
    }
}
