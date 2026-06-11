package com.inkflow.crm.module.google.service;

import com.inkflow.crm.config.GoogleCalendarProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleOAuthStateSignerTest {

    private GoogleOAuthStateSigner signer;

    @BeforeEach
    void setUp() {
        GoogleCalendarProperties properties = new GoogleCalendarProperties();
        properties.setClientSecret("test-secret");
        signer = new GoogleOAuthStateSigner(properties);
    }

    @Test
    void signsAndVerifiesStaffId() {
        UUID staffId = UUID.randomUUID();

        String state = signer.sign(staffId);

        assertEquals(staffId, signer.verify(state));
    }

    @Test
    void rejectsTamperedState() {
        UUID staffId = UUID.randomUUID();
        String state = signer.sign(staffId) + "tampered";

        assertThrows(IllegalArgumentException.class, () -> signer.verify(state));
    }
}
