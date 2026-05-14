package com.inkflow.crm.module.catalog.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnsplashSeederService {

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
                SearchResponse response = restClient.get()
                        .uri(baseUrl + "/search/photos?query={q}&page={p}&per_page={pp}&order_by=relevant",
                                query, page, perPage)
                        .header("Authorization", "Client-ID " + apiKey)
                        .retrieve()
                        .body(SearchResponse.class);

                if (response == null || response.results() == null) break;

                for (UnsplashPhoto photo : response.results()) {
                    if (tattooRepository.existsBySourceAndSourceId("unsplash", photo.id())) continue;
                    tattooRepository.save(toEntity(photo));
                    saved++;
                }
                log.info("Seeded page {}/{}, total saved so far: {}", page, pages, saved);
                Thread.sleep(200);
            } catch (Exception e) {
                log.error("Failed on page {}: {}", page, e.getMessage());
            }
        }
        log.info("Seeding complete. Total new records: {}", saved);
        return saved;
    }

    private Tattoo toEntity(UnsplashPhoto photo) {
        Tattoo t = new Tattoo();
        t.setSource("unsplash");
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
        t.setEmbedding(embeddingService.embedPassage(buildEmbedText(photo, tags)));
        return t;
    }

    private String buildEmbedText(UnsplashPhoto photo, String[] tags) {
        StringBuilder sb = new StringBuilder();
        if (photo.altDescription() != null) sb.append(photo.altDescription()).append(". ");
        if (photo.description() != null) sb.append(photo.description()).append(". ");
        if (tags.length > 0) sb.append("Tags: ").append(String.join(", ", tags));
        return sb.toString().trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(List<UnsplashPhoto> results, int total) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UnsplashPhoto(
            String id,
            int width,
            int height,
            @JsonProperty("blur_hash") String blurHash,
            String color,
            String description,
            @JsonProperty("alt_description") String altDescription,
            Urls urls,
            UnsplashUser user,
            List<UnsplashTag> tags
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Urls(String raw, String full, String regular, String small, String thumb) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UnsplashUser(String name, UserLinks links) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UserLinks(String html) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UnsplashTag(String title) {}
}
