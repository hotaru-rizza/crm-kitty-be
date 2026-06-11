package com.inkflow.crm.common.validation;

import com.inkflow.crm.module.appointment.dto.CreateAppointmentRequest;
import com.inkflow.crm.module.appointment.dto.UpdateAppointmentRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Instant;

public class AppointmentTimeRangeValidator implements ConstraintValidator<ValidAppointmentTimeRange, Object> {

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        Instant startTime;
        Instant endTime;

        if (value instanceof CreateAppointmentRequest request) {
            startTime = request.getStartTime();
            endTime = request.getEndTime();
        } else if (value instanceof UpdateAppointmentRequest request) {
            startTime = request.getStartTime();
            endTime = request.getEndTime();
            if (startTime == null || endTime == null) {
                return true;
            }
        } else {
            return true;
        }

        if (startTime == null || endTime == null) {
            return true;
        }

        return endTime.isAfter(startTime);
    }
}
