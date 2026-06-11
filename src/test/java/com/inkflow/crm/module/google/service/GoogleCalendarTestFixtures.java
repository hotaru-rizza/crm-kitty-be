package com.inkflow.crm.module.google.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.entity.Staff;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

final class GoogleCalendarTestFixtures {

    private GoogleCalendarTestFixtures() {
    }

    static Staff connectedArtist() {
        return Staff.builder()
                .id(UUID.randomUUID())
                .googleRefreshToken("refresh-token")
                .googleAccessToken("access-token")
                .googleCalendarId("primary")
                .googleTokenExpiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    static Appointment connectedAppointment(Staff artist) {
        Instant start = Instant.parse("2026-06-15T10:00:00Z");
        Instant end = Instant.parse("2026-06-15T12:00:00Z");
        return Appointment.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .artist(artist)
                .client(Client.builder()
                        .firstName("John")
                        .lastName("Doe")
                        .phone("+380501234567")
                        .build())
                .service(Service.builder().title("Tattoo Session").build())
                .location(Location.builder().address("Kyiv, Main St 1").build())
                .startTime(start)
                .endTime(end)
                .price(new BigDecimal("1500"))
                .prepayment(new BigDecimal("500"))
                .notes("Bring reference")
                .build();
    }
}
