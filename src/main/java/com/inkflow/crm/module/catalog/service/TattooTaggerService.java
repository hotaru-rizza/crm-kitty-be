package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.entity.TattooStyle;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
import com.inkflow.crm.module.catalog.repository.TattooStyleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TattooTaggerService {

    private final TattooStyleRepository tattooStyleRepository;
    private final TattooRepository tattooRepository;

    private Map<String, List<String>> keywordMap = new LinkedHashMap<>();

    @PostConstruct
    void loadStyles() {
        try {
            reloadKeywords();
        } catch (Exception e) {
            log.warn("Failed to preload tattoo styles: {}", e.getMessage());
        }
    }

    public void reloadKeywords() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        List<TattooStyle> styles = tattooStyleRepository.findByActiveTrueOrderBySortOrderAsc();

        for (TattooStyle style : styles) {
            if (style.getKeywords() != null && style.getKeywords().length > 0) {
                map.put(style.getSlug(), Arrays.asList(style.getKeywords()));
            }
        }

        this.keywordMap = map;
        log.info("Loaded {} tattoo styles from DB", map.size());
    }

    public Set<String> getAvailableTags() {
        return Collections.unmodifiableSet(keywordMap.keySet());
    }

    public String[] tagFromText(String... texts) {
        String combined = buildCombinedText(texts);
        Set<String> tags = new LinkedHashSet<>();

        for (Map.Entry<String, List<String>> entry : keywordMap.entrySet()) {
            if (matchesAnyKeyword(combined, entry.getValue())) {
                tags.add(entry.getKey());
            }
        }

        return tags.toArray(new String[0]);
    }

    public int retagAll() {
        List<Tattoo> all = tattooRepository.findAll();
        int updated = 0;

        for (Tattoo tattoo : all) {
            String[] tags = tagFromText(tattoo.getAltDescription(), tattoo.getDescription());
            tattoo.setTags(tags);
            tattooRepository.save(tattoo);
            updated++;
        }

        log.info("Retagged {} tattoos", updated);
        return updated;
    }

    private String buildCombinedText(String... texts) {
        return Arrays.stream(texts)
                .filter(t -> t != null && !t.isBlank())
                .map(String::toLowerCase)
                .reduce("", (a, b) -> a + " " + b);
    }

    private boolean matchesAnyKeyword(String combined, List<String> keywords) {
        for (String keyword : keywords) {
            if (combined.contains(keyword)) {
                return true;
            }
        }

        return false;
    }
}
