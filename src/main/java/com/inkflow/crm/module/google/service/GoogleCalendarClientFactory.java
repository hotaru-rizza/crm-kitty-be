package com.inkflow.crm.module.google.service;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.config.GoogleCalendarProperties;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
class GoogleCalendarClientFactory {

    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR_EVENTS);
    private static final String APP_NAME = "INKAT CRM";

    private final GoogleCalendarProperties properties;
    private final StaffRepository staffRepository;

    private HttpTransport httpTransport;
    private JsonFactory jsonFactory;

    Calendar getCalendarService(Staff artist) throws Exception {
        ensureTransport();
        String accessToken = refreshTokenIfNeeded(artist);

        Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(accessToken);

        return new Calendar.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(APP_NAME)
                .build();
    }

    Calendar getCalendarService(String accessToken) throws Exception {
        ensureTransport();
        Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(accessToken);

        return new Calendar.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(APP_NAME)
                .build();
    }

    GoogleTokenResponse exchangeAuthorizationCode(String code) throws IOException {
        ensureTransport();
        return new GoogleAuthorizationCodeTokenRequest(
                httpTransport, jsonFactory,
                properties.getClientId(), properties.getClientSecret(),
                code, properties.getRedirectUri()
        ).execute();
    }

    synchronized void ensureTransport() {
        if (httpTransport != null) {
            return;
        }
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            jsonFactory = JacksonFactory.getDefaultInstance();
        } catch (Exception exception) {
            throw new BusinessRuleException("Google Calendar transport init failed: " + exception.getMessage());
        }
    }

    com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow buildFlow() {
        ensureTransport();
        return new com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory,
                properties.getClientId(), properties.getClientSecret(),
                SCOPES
        ).setAccessType("offline").build();
    }

    private String refreshTokenIfNeeded(Staff artist) throws Exception {
        if (artist.getGoogleTokenExpiresAt() != null
                && artist.getGoogleTokenExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return artist.getGoogleAccessToken();
        }

        GoogleTokenResponse tokenResponse = refreshAccessToken(artist.getGoogleRefreshToken());

        artist.setGoogleAccessToken(tokenResponse.getAccessToken());
        artist.setGoogleTokenExpiresAt(
                Instant.now().plusSeconds(tokenResponse.getExpiresInSeconds())
        );
        staffRepository.save(artist);

        return tokenResponse.getAccessToken();
    }

    GoogleTokenResponse refreshAccessToken(String refreshToken) throws IOException {
        ensureTransport();
        return new GoogleRefreshTokenRequest(
                httpTransport, jsonFactory,
                refreshToken,
                properties.getClientId(),
                properties.getClientSecret()
        ).execute();
    }
}
