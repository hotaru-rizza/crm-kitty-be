package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TattooTaggerService {

    private final TattooRepository tattooRepository;

    private static final Map<String, List<String>> KEYWORD_MAP = new LinkedHashMap<>();

    static {
        KEYWORD_MAP.put("blackwork",  List.of("blackwork", "black work", "black ink", "black tattoo", "black and grey", "black and white", "grayscale", "grey"));
        KEYWORD_MAP.put("minimalist", List.of("minimalist", "minimal", "simple", "small", "tiny", "fine line", "delicate", "subtle"));
        KEYWORD_MAP.put("japanese",   List.of("japanese", "japan", "irezumi", "samurai", "koi", "dragon", "geisha", "oni"));
        KEYWORD_MAP.put("geometric",  List.of("geometric", "geometry", "sacred geometry", "mandala", "triangle", "hexagon", "symmetr"));
        KEYWORD_MAP.put("dotwork",    List.of("dotwork", "dot work", "stippling", "pointillism"));
        KEYWORD_MAP.put("watercolor", List.of("watercolor", "water color", "watercolour", "colorful", "colourful", "vibrant color"));
        KEYWORD_MAP.put("traditional",List.of("traditional", "old school", "american traditional", "classic"));
        KEYWORD_MAP.put("floral",     List.of("floral", "flower", "rose", "lily", "lotus", "peony", "botanical", "plant", "leaves", "leaf"));
        KEYWORD_MAP.put("animal",     List.of("wolf", "lion", "tiger", "snake", "eagle", "bear", "fox", "cat", "dog", "bird", "butterfly", "animal"));
        KEYWORD_MAP.put("lettering",  List.of("lettering", "script", "word", "text", "quote", "calligraphy", "font", "letter"));
        KEYWORD_MAP.put("sleeve",     List.of("sleeve", "full arm", "half sleeve", "arm"));
        KEYWORD_MAP.put("portrait",   List.of("portrait", "face", "realistic", "realism", "photo realistic"));
    }

    public String[] tagFromText(String... texts) {
        String combined = Arrays.stream(texts)
                .filter(t -> t != null && !t.isBlank())
                .map(String::toLowerCase)
                .reduce("", (a, b) -> a + " " + b);

        Set<String> tags = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : KEYWORD_MAP.entrySet()) {
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
