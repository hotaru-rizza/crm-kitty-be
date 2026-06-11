package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.entity.TattooStyle;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
import com.inkflow.crm.module.catalog.repository.TattooStyleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

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
        String combined = Arrays.stream(texts)
                .filter(t -> t != null && !t.isBlank())
                .map(String::toLowerCase)
                .reduce("", (a, b) -> a + " " + b);

        Set<String> tags = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : keywordMap.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (combined.contains(keyword)) {
                    tags.add(entry.getKey());
                    break;
                }
            }
        }
        return tags.toArray(new String[0]);
    }

    public int retagAll() {
        List<Tattoo> all = tattooRepository.findAll();
        int updated = 0;
        for (Tattoo t : all) {
            String[] tags = tagFromText(t.getAltDescription(), t.getDescription());
            t.setTags(tags);
            tattooRepository.save(t);
            updated++;
        }
        log.info("Retagged {} tattoos", updated);
        return updated;
    }
}
