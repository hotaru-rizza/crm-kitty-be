package com.inkflow.crm.module.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FirebaseConfig {

    private final FcmProperties fcmProperties;

    @Bean
    @ConditionalOnProperty(prefix = "fcm", name = "enabled", havingValue = "true")
    public FirebaseApp firebaseApp() throws IOException {
        if (!fcmProperties.hasCredentials()) {
            throw new IllegalStateException(
                    "fcm.enabled=true but neither fcm.credentials-json nor fcm.credentials-path is set");
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        try (InputStream credentialsStream = openCredentialsStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                    .build();
            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("FirebaseApp initialized for FCM");
            return app;
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "fcm", name = "enabled", havingValue = "true")
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    private InputStream openCredentialsStream() throws IOException {
        if (StringUtils.hasText(fcmProperties.getCredentialsJson())) {
            return new ByteArrayInputStream(
                    fcmProperties.getCredentialsJson().getBytes(StandardCharsets.UTF_8));
        }

        Path path = Path.of(fcmProperties.getCredentialsPath());
        return Files.newInputStream(path);
    }
}
