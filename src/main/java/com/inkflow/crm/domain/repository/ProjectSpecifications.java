package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.enums.ProjectStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProjectSpecifications {

    private ProjectSpecifications() {}

    public static Specification<Project> belongsToTenant(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<Project> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Project> statusIs(ProjectStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Project> excludeArchivedWhenNoStatusFilter(String statusFilter) {
        if (statusFilter != null && !statusFilter.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.notEqual(root.get("status"), ProjectStatus.ARCHIVED);
    }

    public static Specification<Project> artistIn(List<UUID> artistIds) {
        if (artistIds == null || artistIds.isEmpty()) return null;
        return (root, query, cb) -> root.get("artist").get("id").in(artistIds);
    }

    public static Specification<Project> clientIs(UUID clientId) {
        if (clientId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("client").get("id"), clientId);
    }

    public static Specification<Project> searchLike(String search) {
        if (search == null || search.isBlank()) return null;
        return (root, query, cb) -> {
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("client").get("firstName")), pattern),
                    cb.like(cb.lower(root.get("client").get("lastName")), pattern)
            );
        };
    }

    public static Specification<Project> locationIs(UUID locationId) {
        if (locationId == null) return null;
        return (root, query, cb) -> cb.or(
                cb.isNull(root.get("location")),
                cb.equal(root.get("location").get("id"), locationId)
        );
    }

    public static Specification<Project> budgetBetween(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) return null;
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("estimatedCost"), min, max);
            } else if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("estimatedCost"), min);
            } else {
                return cb.lessThanOrEqualTo(root.get("estimatedCost"), max);
            }
        };
    }

    public static Specification<Project> paidPercentBetween(Integer minPct, Integer maxPct) {
        if (minPct == null && maxPct == null) return null;
        return (root, query, cb) -> {
            var paid = root.<BigDecimal>get("totalPaid");
            var cost = root.<BigDecimal>get("estimatedCost");
            var pctExpr = cb.prod(
                    cb.quot(paid, cb.nullif(cost, BigDecimal.ZERO)).as(BigDecimal.class),
                    new BigDecimal(100)
            );

            if (minPct != null && maxPct != null) {
                return cb.and(
                        cb.greaterThanOrEqualTo(pctExpr, new BigDecimal(minPct)),
                        cb.lessThanOrEqualTo(pctExpr, new BigDecimal(maxPct))
                );
            } else if (minPct != null) {
                return cb.greaterThanOrEqualTo(pctExpr, new BigDecimal(minPct));
            } else {
                return cb.lessThanOrEqualTo(pctExpr, new BigDecimal(maxPct));
            }
        };
    }

    public static Specification<Project> totalSessionsBetween(Integer min, Integer max) {
        if (min == null && max == null) return null;
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("totalSessions"), min, max);
            } else if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("totalSessions"), min);
            } else {
                return cb.lessThanOrEqualTo(root.get("totalSessions"), max);
            }
        };
    }

    public static Specification<Project> completedSessionsBetween(Integer min, Integer max) {
        if (min == null && max == null) return null;
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("completedSessions"), min, max);
            } else if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("completedSessions"), min);
            } else {
                return cb.lessThanOrEqualTo(root.get("completedSessions"), max);
            }
        };
    }

    public static Specification<Project> createdBetween(Instant from, Instant to) {
        if (from == null && to == null) return null;
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("createdAt"), from, to);
            } else if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
            } else {
                return cb.lessThanOrEqualTo(root.get("createdAt"), to);
            }
        };
    }

    public static Specification<Project> updatedBetween(Instant from, Instant to) {
        if (from == null && to == null) return null;
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("updatedAt"), from, to);
            } else if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("updatedAt"), from);
            } else {
                return cb.lessThanOrEqualTo(root.get("updatedAt"), to);
            }
        };
    }

    public static Specification<Project> hasSketch(Boolean value) {
        if (value == null) return null;
        return (root, query, cb) -> value
                ? cb.isNotNull(root.get("sketchImage"))
                : cb.isNull(root.get("sketchImage"));
    }

    public static Specification<Project> hasPhotos(Boolean value) {
        if (value == null) return null;
        return (root, query, cb) -> value
                ? cb.greaterThan(cb.size(root.get("photos")), 0)
                : cb.equal(cb.size(root.get("photos")), 0);
    }

    public static Specification<Project> hasDebt(Boolean value) {
        if (!Boolean.TRUE.equals(value)) return null;
        return (root, query, cb) ->
                cb.greaterThan(root.get("estimatedCost"), root.get("totalPaid"));
    }
}
