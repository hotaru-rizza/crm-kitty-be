package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Appointment;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AppointmentSpecifications {

    private AppointmentSpecifications() {}

    public static Specification<Appointment> belongsToTenant(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<Appointment> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Appointment> withLocation(UUID locationId) {
        if (locationId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("location").get("id"), locationId);
    }

    public static Specification<Appointment> withArtist(UUID artistId) {
        if (artistId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("artist").get("id"), artistId);
    }

    public static Specification<Appointment> withArtists(List<UUID> artistIds) {
        if (artistIds == null || artistIds.isEmpty()) return null;
        if (artistIds.size() == 1) return withArtist(artistIds.get(0));
        return (root, query, cb) -> root.get("artist").get("id").in(artistIds);
    }

    public static Specification<Appointment> withStatus(String status) {
        if (status == null || status.isBlank()) return null;
        return (root, query, cb) -> cb.equal(cb.lower(root.get("status").as(String.class)), status.toLowerCase());
    }

    public static Specification<Appointment> startTimeAfter(Instant from) {
        if (from == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startTime"), from);
    }

    public static Specification<Appointment> startTimeBefore(Instant to) {
        if (to == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("startTime"), to);
    }

    public static Specification<Appointment> withService(UUID serviceId) {
        if (serviceId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("service").get("id"), serviceId);
    }
}
