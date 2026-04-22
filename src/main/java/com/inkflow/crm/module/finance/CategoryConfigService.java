package com.inkflow.crm.module.finance;

import com.inkflow.crm.domain.entity.TransactionCategoryConfig;
import com.inkflow.crm.domain.repository.TransactionCategoryConfigRepository;
import com.inkflow.crm.module.finance.dto.CategoryConfigDto;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryConfigService {

    private final TransactionCategoryConfigRepository repo;

    private static final List<Object[]> DEFAULTS = List.of(
        new Object[]{"service",  "Послуга",     "#6366f1", "INCOME"},
        new Object[]{"merch",    "Мерч",         "#22c55e", "INCOME"},
        new Object[]{"rent",     "Оренда",       "#f59e0b", "EXPENSE"},
        new Object[]{"supplies", "Витратні матеріали", "#fb923c", "EXPENSE"},
        new Object[]{"salary",   "Зарплата",     "#ec4899", "EXPENSE"},
        new Object[]{"other",    "Інше",         "#94a3b8", "NEUTRAL"}
    );

    @Transactional(readOnly = true)
    public List<CategoryConfigDto> getAll() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        ensureDefaults(tenantId);
        return repo.findByTenantIdAndDeletedAtIsNullOrderByIsDefaultDescLabelAsc(tenantId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public CategoryConfigDto upsert(String categoryKey, String label, String color, String plType) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        ensureDefaults(tenantId);

        Optional<TransactionCategoryConfig> existing =
                repo.findByTenantIdAndCategoryKeyAndDeletedAtIsNull(tenantId, categoryKey);

        TransactionCategoryConfig cfg = existing.orElseGet(() ->
                TransactionCategoryConfig.builder()
                        .tenantId(tenantId)
                        .categoryKey(categoryKey)
                        .isDefault(false)
                        .build()
        );
        cfg.setLabel(label);
        cfg.setColor(color);
        cfg.setPlType(plType);
        cfg.setIsActive(true);
        return toDto(repo.save(cfg));
    }

    @Transactional
    public CategoryConfigDto create(String label, String color, String plType) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        String key = "custom_" + UUID.randomUUID().toString().substring(0, 8);
        TransactionCategoryConfig cfg = TransactionCategoryConfig.builder()
                .tenantId(tenantId)
                .categoryKey(key)
                .label(label)
                .color(color)
                .plType(plType)
                .isDefault(false)
                .isActive(true)
                .build();
        return toDto(repo.save(cfg));
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        repo.findById(id).ifPresent(cfg -> {
            if (cfg.getTenantId().equals(tenantId) && !Boolean.TRUE.equals(cfg.getIsDefault())) {
                cfg.setDeletedAt(java.time.Instant.now());
                repo.save(cfg);
            }
        });
    }

    @Transactional
    public void ensureDefaults(UUID tenantId) {
        if (repo.existsByTenantIdAndDeletedAtIsNull(tenantId)) return;
        for (Object[] row : DEFAULTS) {
            TransactionCategoryConfig cfg = TransactionCategoryConfig.builder()
                    .tenantId(tenantId)
                    .categoryKey((String) row[0])
                    .label((String) row[1])
                    .color((String) row[2])
                    .plType((String) row[3])
                    .isDefault(true)
                    .isActive(true)
                    .build();
            repo.save(cfg);
        }
    }

    private CategoryConfigDto toDto(TransactionCategoryConfig c) {
        return CategoryConfigDto.builder()
                .id(c.getId())
                .categoryKey(c.getCategoryKey())
                .label(c.getLabel())
                .color(c.getColor())
                .plType(c.getPlType())
                .isActive(c.getIsActive())
                .isDefault(c.getIsDefault())
                .build();
    }
}
