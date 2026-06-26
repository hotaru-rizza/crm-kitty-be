package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClientSpecifications {

    private ClientSpecifications() {}

    public static Specification<Client> belongsToTenant(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<Client> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Client> searchLike(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return (root, query, cb) -> {
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("firstName")), pattern),
                    cb.like(cb.lower(root.get("lastName")), pattern),
                    cb.like(root.get("phone"), "%" + search + "%"),
                    cb.like(cb.lower(root.get("email")), pattern)
            );
        };
    }

    public static Specification<Client> dormantIs(Boolean dormant) {
        if (dormant == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("dormant"), dormant);
    }

    public static Specification<Client> blacklisted(Boolean blacklisted) {
        if (blacklisted == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("blacklisted"), blacklisted);
    }

    public static Specification<Client> excludeBlacklisted(Boolean excludeBlacklisted) {
        if (!Boolean.TRUE.equals(excludeBlacklisted)) {
            return null;
        }
        return (root, query, cb) -> cb.isFalse(root.get("blacklisted"));
    }

    public static Specification<Client> totalVisitsBetween(Integer min, Integer max) {
        return intBetween("totalVisits", min, max);
    }

    public static Specification<Client> cancelledVisitsBetween(Integer min, Integer max) {
        return intBetween("cancelledVisits", min, max);
    }

    public static Specification<Client> balanceBetween(BigDecimal min, BigDecimal max) {
        return decimalBetween("balance", min, max);
    }

    public static Specification<Client> ltvBetween(BigDecimal min, BigDecimal max) {
        return decimalBetween("ltv", min, max);
    }

    public static Specification<Client> avgCheckBetween(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return null;
        }
        return (root, query, cb) -> {
            Expression<BigDecimal> avgExpression = cb.quot(
                    root.get("ltv").as(BigDecimal.class),
                    cb.nullif(root.get("totalVisits").as(BigDecimal.class), BigDecimal.ZERO)
            ).as(BigDecimal.class);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThan(root.get("totalVisits"), 0));
            if (min != null) {
                predicates.add(cb.greaterThanOrEqualTo(avgExpression, min));
            }
            if (max != null) {
                predicates.add(cb.lessThanOrEqualTo(avgExpression, max));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<Client> createdAtBetween(Instant from, Instant to) {
        return instantBetween("createdAt", from, to);
    }

    public static Specification<Client> lastVisitBetween(Instant from, Instant to) {
        return instantBetween("lastVisit", from, to);
    }

    public static Specification<Client> firstVisitBetween(Instant from, Instant to) {
        return instantBetween("firstVisit", from, to);
    }

    public static Specification<Client> birthdayBetween(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("birthDate"), from, to);
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("birthDate"), from);
            }
            return cb.lessThanOrEqualTo(root.get("birthDate"), to);
        };
    }

    public static Specification<Client> hasTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Object, Object> tagJoin = root.join("tags");
            return tagJoin.in(tags);
        };
    }

    public static Specification<Client> workedWithArtist(UUID artistId) {
        if (artistId == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<Appointment> appointmentRoot = subquery.from(Appointment.class);
            subquery.select(appointmentRoot.get("client").get("id"))
                    .where(
                            cb.equal(appointmentRoot.get("client").get("id"), root.get("id")),
                            cb.equal(appointmentRoot.get("artist").get("id"), artistId),
                            cb.isNull(appointmentRoot.get("deletedAt"))
                    );
            return cb.exists(subquery);
        };
    }

    public static Specification<Client> visitedServices(List<UUID> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<Appointment> appointmentRoot = subquery.from(Appointment.class);
            subquery.select(appointmentRoot.get("client").get("id"))
                    .where(
                            cb.equal(appointmentRoot.get("client").get("id"), root.get("id")),
                            appointmentRoot.get("service").get("id").in(serviceIds),
                            cb.isNull(appointmentRoot.get("deletedAt"))
                    );
            return cb.exists(subquery);
        };
    }

    public static Specification<Client> hasActiveAppointments(Boolean hasActive, Instant from, Instant to) {
        if (!Boolean.TRUE.equals(hasActive)) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<Appointment> appointmentRoot = subquery.from(Appointment.class);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(appointmentRoot.get("client").get("id"), root.get("id")));
            predicates.add(cb.isNull(appointmentRoot.get("deletedAt")));
            predicates.add(cb.equal(appointmentRoot.get("status"), AppointmentStatus.SCHEDULED));
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(appointmentRoot.get("startTime"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(appointmentRoot.get("startTime"), to));
            }
            subquery.select(appointmentRoot.get("client").get("id"))
                    .where(predicates.toArray(Predicate[]::new));
            return cb.exists(subquery);
        };
    }

    public static Specification<Client> lostSince(Instant cutoff) {
        if (cutoff == null) {
            return null;
        }
        return (root, query, cb) -> cb.and(
                cb.greaterThan(root.get("totalVisits"), 0),
                cb.or(
                        cb.isNull(root.get("lastVisit")),
                        cb.lessThan(root.get("lastVisit"), cutoff)
                )
        );
    }

    private static Specification<Client> intBetween(String field, Integer min, Integer max) {
        if (min == null && max == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get(field), min, max);
            }
            if (min != null) {
                return cb.greaterThanOrEqualTo(root.get(field), min);
            }
            return cb.lessThanOrEqualTo(root.get(field), max);
        };
    }

    private static Specification<Client> decimalBetween(String field, BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get(field), min, max);
            }
            if (min != null) {
                return cb.greaterThanOrEqualTo(root.get(field), min);
            }
            return cb.lessThanOrEqualTo(root.get(field), max);
        };
    }

    private static Specification<Client> instantBetween(String field, Instant from, Instant to) {
        if (from == null && to == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get(field), from, to);
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get(field), from);
            }
            return cb.lessThanOrEqualTo(root.get(field), to);
        };
    }
}
