package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Transaction;
import com.inkflow.crm.domain.enums.PaymentMethod;
import com.inkflow.crm.domain.enums.TransactionCategory;
import com.inkflow.crm.domain.enums.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TransactionSpecifications {

    private TransactionSpecifications() {}

    public static Specification<Transaction> belongsToTenant(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<Transaction> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Transaction> typeIs(TransactionType type) {
        if (type == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Transaction> categoryIs(TransactionCategory category) {
        if (category == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<Transaction> paymentMethodIs(PaymentMethod paymentMethod) {
        if (paymentMethod == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("paymentMethod"), paymentMethod);
    }

    public static Specification<Transaction> staffIdIn(List<UUID> staffIds) {
        if (staffIds == null || staffIds.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("staff").get("id").in(staffIds);
    }

    public static Specification<Transaction> dateFrom(Instant from) {
        if (from == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), from);
    }

    public static Specification<Transaction> dateBefore(Instant to) {
        if (to == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThan(root.get("date"), to);
    }

    public static Specification<Transaction> amountBetween(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("amount"), min, max);
            }
            if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("amount"), min);
            }
            return cb.lessThanOrEqualTo(root.get("amount"), max);
        };
    }

    public static Specification<Transaction> filtered(
            UUID tenantId,
            String type,
            String category,
            Instant from,
            Instant to,
            List<UUID> staffIds,
            String paymentMethod,
            BigDecimal amountMin,
            BigDecimal amountMax
    ) {
        TransactionType transactionType = type != null && !type.isBlank()
                ? TransactionType.fromValue(type)
                : null;
        TransactionCategory transactionCategory = category != null && !category.isBlank()
                ? TransactionCategory.fromValue(category)
                : null;
        PaymentMethod method = paymentMethod != null && !paymentMethod.isBlank()
                ? PaymentMethod.fromValue(paymentMethod)
                : null;

        return Specification.allOf(
                belongsToTenant(tenantId),
                notDeleted(),
                typeIs(transactionType),
                categoryIs(transactionCategory),
                paymentMethodIs(method),
                staffIdIn(staffIds),
                dateFrom(from),
                dateBefore(to),
                amountBetween(amountMin, amountMax)
        );
    }
}
