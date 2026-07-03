package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RequestSpecifications {

    private RequestSpecifications() {}

    public static Specification<Request> statusIs(RequestStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Request> sourceIn(List<RequestSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("source").in(sources);
    }

    public static Specification<Request> createdBetween(Instant from, Instant to) {
        if (from == null && to == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("createdAt"), from, to);
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
            }
            return cb.lessThanOrEqualTo(root.get("createdAt"), to);
        };
    }

    public static Specification<Request> locationIs(UUID locationId) {
        if (locationId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("location").get("id"), locationId);
    }

    public static Specification<Request> searchLike(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return (root, query, cb) -> {
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("clientName")), pattern),
                    cb.like(cb.lower(root.get("message")), pattern),
                    cb.like(cb.lower(root.get("idea")), pattern)
            );
        };
    }

    public static Specification<Request> cityEquals(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("city"), city);
    }

    public static Specification<Request> tattooSizeEquals(String tattooSize) {
        if (tattooSize == null || tattooSize.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("tattooSize"), tattooSize);
    }

    public static Specification<Request> tattooTimingEquals(String tattooTiming) {
        if (tattooTiming == null || tattooTiming.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("tattooTiming"), tattooTiming);
    }

    public static Specification<Request> isCoverUpEquals(Boolean isCoverUp) {
        if (isCoverUp == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("isCoverUp"), isCoverUp);
    }

    public static Specification<Request> hasSketch(Boolean hasSketch) {
        if (hasSketch == null) {
            return null;
        }
        return (root, query, cb) -> hasSketch
                ? cb.isNotNull(root.get("sketchUrl"))
                : cb.isNull(root.get("sketchUrl"));
    }

    public static Specification<Request> hasReferences(Boolean hasReferences) {
        if (hasReferences == null) {
            return null;
        }
        return (root, query, cb) -> hasReferences
                ? cb.isNotNull(root.get("referenceUrls"))
                : cb.isNull(root.get("referenceUrls"));
    }

    public static Specification<Request> bodyZoneContains(String bodyZone) {
        if (bodyZone == null || bodyZone.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.like(
                cb.lower(root.get("bodyZones")),
                "%" + bodyZone.toLowerCase() + "%"
        );
    }

    public static Specification<Request> assignedStaffIs(UUID staffId) {
        if (staffId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("assignedStaff").get("id"), staffId);
    }
}
