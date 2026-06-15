package com.inkflow.crm.module.email.template;

import com.inkflow.crm.module.email.enums.BuiltInTemplateKey;
import com.inkflow.crm.module.email.enums.TemplateKey;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class BuiltInTemplateCatalog {

    private BuiltInTemplateCatalog() {}

    public record Entry(
            BuiltInTemplateKey key,
            String subject,
            String body
    ) {}

    private static final Map<BuiltInTemplateKey, Entry> ENTRIES = build();

    public static Entry get(BuiltInTemplateKey key) {
        Entry entry = ENTRIES.get(key);
        if (entry == null) {
            throw new IllegalStateException("No built-in template catalog entry for: " + key);
        }
        return entry;
    }

    public static BuiltInTemplateKey[] allKeys() {
        return BuiltInTemplateKey.values();
    }

    public static Optional<BuiltInTemplateKey> fromLegacyTemplateKey(String templateKey) {
        return Optional.ofNullable(LEGACY_KEY_MAP.get(templateKey));
    }

    private static final Map<String, BuiltInTemplateKey> LEGACY_KEY_MAP = Map.ofEntries(
            Map.entry(TemplateKey.BOOKING_CONFIRMED.name(), BuiltInTemplateKey.CONFIRMATION),
            Map.entry(TemplateKey.BOOKING_CANCELED.name(), BuiltInTemplateKey.CANCELLATION),
            Map.entry(TemplateKey.BOOKING_RESCHEDULED.name(), BuiltInTemplateKey.RESCHEDULE),
            Map.entry(TemplateKey.BOOKING_REMINDER.name(), BuiltInTemplateKey.REMINDER),
            Map.entry(TemplateKey.AFTERCARE_INSTRUCTIONS.name(), BuiltInTemplateKey.AFTERCARE),
            Map.entry(TemplateKey.PREP_INSTRUCTIONS.name(), BuiltInTemplateKey.PREP_INSTRUCTIONS),
            Map.entry(TemplateKey.REVIEW_REQUEST.name(), BuiltInTemplateKey.REVIEW_REQUEST),
            Map.entry(TemplateKey.BIRTHDAY.name(), BuiltInTemplateKey.BIRTHDAY),
            Map.entry(TemplateKey.WINBACK.name(), BuiltInTemplateKey.WINBACK)
    );

    private static Map<BuiltInTemplateKey, Entry> build() {
        return Arrays.stream(BuiltInTemplateKey.values())
                .collect(Collectors.toUnmodifiableMap(Function.identity(), BuiltInTemplateCatalog::entryFor));
    }

    private static Entry entryFor(BuiltInTemplateKey key) {
        TemplateKey legacyKey = legacyTemplateKey(key);
        RenderedContent content = TemplateDefaults.get(legacyKey);
        return new Entry(key, content.subject(), content.body());
    }

    private static TemplateKey legacyTemplateKey(BuiltInTemplateKey key) {
        return switch (key) {
            case CONFIRMATION -> TemplateKey.BOOKING_CONFIRMED;
            case CANCELLATION -> TemplateKey.BOOKING_CANCELED;
            case RESCHEDULE -> TemplateKey.BOOKING_RESCHEDULED;
            case REMINDER -> TemplateKey.BOOKING_REMINDER;
            case AFTERCARE -> TemplateKey.AFTERCARE_INSTRUCTIONS;
            case PREP_INSTRUCTIONS -> TemplateKey.PREP_INSTRUCTIONS;
            case REVIEW_REQUEST -> TemplateKey.REVIEW_REQUEST;
            case BIRTHDAY -> TemplateKey.BIRTHDAY;
            case WINBACK -> TemplateKey.WINBACK;
        };
    }
}
