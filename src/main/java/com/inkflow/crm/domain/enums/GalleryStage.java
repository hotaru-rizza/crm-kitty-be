package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GalleryStage {
    SKETCH("sketch", "Initial sketch"),
    IN_PROGRESS("in_progress", "Work in progress"),
    FRESH("fresh", "Fresh tattoo"),
    HEALED("healed", "Healed tattoo");

    private final String value;
    private final String description;

    public static GalleryStage fromValue(String value) {
        for (GalleryStage stage : values()) {
            if (stage.value.equalsIgnoreCase(value)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("Unknown gallery stage: " + value);
    }
}
