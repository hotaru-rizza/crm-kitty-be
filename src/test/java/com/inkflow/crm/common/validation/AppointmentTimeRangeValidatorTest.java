package com.inkflow.crm.common.validation;

import com.inkflow.crm.module.appointment.dto.CreateAppointmentRequest;
import com.inkflow.crm.module.appointment.dto.UpdateAppointmentRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentTimeRangeValidatorTest {

    private final AppointmentTimeRangeValidator validator = new AppointmentTimeRangeValidator();

    @Test
    void shouldAcceptEndTimeAfterStartTimeOnCreate() {
        Instant start = Instant.parse("2026-06-15T10:00:00Z");
        Instant end = Instant.parse("2026-06-15T11:00:00Z");

        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .startTime(start)
                .endTime(end)
                .build();

        assertTrue(validator.isValid(request, null));
    }

    @Test
    void shouldRejectEndTimeBeforeStartTimeOnCreate() {
        Instant start = Instant.parse("2026-06-15T11:00:00Z");
        Instant end = Instant.parse("2026-06-15T10:00:00Z");

        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .startTime(start)
                .endTime(end)
                .build();

        assertFalse(validator.isValid(request, null));
    }

    @Test
    void shouldRejectEqualStartAndEndTimeOnCreate() {
        Instant sameInstant = Instant.parse("2026-06-15T10:00:00Z");

        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .startTime(sameInstant)
                .endTime(sameInstant)
                .build();

        assertFalse(validator.isValid(request, null));
    }

    @Test
    void shouldSkipValidationWhenCreateRequestHasMissingTime() {
        assertTrue(validator.isValid(
                CreateAppointmentRequest.builder().startTime(Instant.parse("2026-06-15T10:00:00Z")).build(),
                null));
        assertTrue(validator.isValid(
                CreateAppointmentRequest.builder().endTime(Instant.parse("2026-06-15T11:00:00Z")).build(),
                null));
    }

    @Test
    void shouldSkipPartialUpdateWhenOnlyStartTimeProvided() {
        UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                .startTime(Instant.parse("2026-06-15T10:00:00Z"))
                .build();

        assertTrue(validator.isValid(request, null));
    }

    @Test
    void shouldSkipPartialUpdateWhenOnlyEndTimeProvided() {
        UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                .endTime(Instant.parse("2026-06-15T11:00:00Z"))
                .build();

        assertTrue(validator.isValid(request, null));
    }

    @Test
    void shouldRejectInvalidRangeWhenBothTimesProvidedOnUpdate() {
        UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                .startTime(Instant.parse("2026-06-15T12:00:00Z"))
                .endTime(Instant.parse("2026-06-15T11:00:00Z"))
                .build();

        assertFalse(validator.isValid(request, null));
    }

    @Test
    void shouldAcceptValidRangeWhenBothTimesProvidedOnUpdate() {
        UpdateAppointmentRequest request = UpdateAppointmentRequest.builder()
                .startTime(Instant.parse("2026-06-15T10:00:00Z"))
                .endTime(Instant.parse("2026-06-15T11:00:00Z"))
                .build();

        assertTrue(validator.isValid(request, null));
    }

    @Test
    void shouldAcceptNullValue() {
        assertTrue(validator.isValid(null, null));
    }

    @Test
    void shouldAcceptUnsupportedPayloadType() {
        assertTrue(validator.isValid("not-an-appointment-request", null));
    }
}
