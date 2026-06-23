package com.inkflow.crm.config;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads {@code .env} from project root into Spring default properties.
 * Shell / IntelliJ env vars take precedence (not overwritten).
 */
@Slf4j
public final class LocalEnvLoader {

    private static final String PROFILE_ENV = "SPRING_PROFILES_ACTIVE";
    private static final String PROFILE_PROP = "spring.profiles.active";

    private LocalEnvLoader() {
    }

    public static Map<String, Object> loadDefaults() {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Dotenv dotenv = Dotenv.configure()
                .directory(projectRoot.toString())
                .filename(".env")
                .ignoreIfMissing()
                .ignoreIfMalformed()
                .load();

        Map<String, Object> defaults = new HashMap<>();
        dotenv.entries().forEach(entry -> {
            String key = entry.getKey();
            if (isSet(key)) {
                return;
            }
            defaults.put(key, entry.getValue());
            if (PROFILE_ENV.equals(key)) {
                defaults.put(PROFILE_PROP, entry.getValue());
            }
        });

        if (!defaults.isEmpty()) {
            log.info("Loaded {} keys from {}", defaults.size(), projectRoot.resolve(".env"));
        } else if (projectRoot.resolve(".env").toFile().exists()) {
            log.warn(".env exists but all keys overridden by shell/IDE env");
        }

        return defaults;
    }

    private static boolean isSet(String key) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return true;
        }
        String prop = System.getProperty(key);
        return prop != null && !prop.isBlank();
    }
}
