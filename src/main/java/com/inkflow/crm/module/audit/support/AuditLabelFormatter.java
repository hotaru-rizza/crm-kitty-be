package com.inkflow.crm.module.audit.support;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.LeaveType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AuditLabelFormatter {

    private static final Locale UK = Locale.forLanguageTag("uk");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", UK);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", UK);

    private final InkflowProperties inkflowProperties;

    public String appointment(Client client, Instant startTime) {
        if (client == null) {
            return "Запис · " + formatDateTime(startTime);
        }
        return "Запис · " + client.getFirstName() + " " + client.getLastName() + " · " + formatDateTime(startTime);
    }

    public String leave(Staff staff, LeaveType leaveType, LocalDate startDate, LocalDate endDate) {
        return leaveType.getDisplayName() + " · " + staff.getFullName() + " · " + formatDateRange(startDate, endDate);
    }

    public String staff(Staff staff) {
        return staff.getFullName();
    }

    public String project(String title) {
        return "Проєкт · " + title;
    }

    public String location(String name) {
        return "Локація · " + name;
    }

    public String catalogService(String title) {
        return "Послуга · " + title;
    }

    public String tenantSetting(String label) {
        return "Налаштування · " + label;
    }

    public String emailTemplate(String subject) {
        return "Шаблон · " + subject;
    }

    public String portfolio(Staff staff) {
        return "Портфоліо · " + staff.getFullName();
    }

    public String financeCategory(String label) {
        return "Категорія · " + label;
    }

    private String formatDateTime(Instant instant) {
        return DATE_TIME.format(instant.atZone(inkflowProperties.defaultZoneId()));
    }

    private String formatDateRange(LocalDate start, LocalDate end) {
        if (start.equals(end)) {
            return DATE.format(start);
        }
        return DATE.format(start) + "–" + DATE.format(end);
    }
}
