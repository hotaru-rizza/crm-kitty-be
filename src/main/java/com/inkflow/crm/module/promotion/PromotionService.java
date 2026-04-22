package com.inkflow.crm.module.promotion;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import java.util.UUID;
import com.inkflow.crm.domain.entity.Promotion;
import com.inkflow.crm.domain.enums.DiscountType;
import com.inkflow.crm.domain.repository.PromotionRepository;
import com.inkflow.crm.module.promotion.dto.CreatePromotionRequest;
import com.inkflow.crm.module.promotion.dto.PromotionDto;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromotionService {

    private final PromotionRepository promotionRepository;

    @Transactional(readOnly = true)
    public List<PromotionDto> getAllPromotions() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return promotionRepository
                .findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PromotionDto> getActivePromotions() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return promotionRepository
                .findByTenantIdAndIsActiveTrueAndDeletedAtIsNull(tenantId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public PromotionDto createPromotion(CreatePromotionRequest req) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Promotion promotion = Promotion.builder()
                .tenantId(tenantId)
                .name(req.getName())
                .description(req.getDescription())
                .discountType(DiscountType.fromValue(req.getDiscountType()))
                .discountValue(req.getDiscountValue())
                .validFrom(req.getValidFrom())
                .validTo(req.getValidTo())
                .isActive(true)
                .serviceIds(req.getServiceIds() != null ? new ArrayList<>(req.getServiceIds()) : new ArrayList<>())
                .build();
        return toDto(promotionRepository.save(promotion));
    }

    @Transactional
    public PromotionDto updatePromotion(UUID id, CreatePromotionRequest req) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Promotion promotion = promotionRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.promotion(id.toString()));

        if (req.getName() != null) promotion.setName(req.getName());
        if (req.getDescription() != null) promotion.setDescription(req.getDescription());
        if (req.getDiscountType() != null) promotion.setDiscountType(DiscountType.fromValue(req.getDiscountType()));
        if (req.getDiscountValue() != null) promotion.setDiscountValue(req.getDiscountValue());
        if (req.getValidFrom() != null) promotion.setValidFrom(req.getValidFrom());
        if (req.getValidTo() != null) promotion.setValidTo(req.getValidTo());
        if (req.getServiceIds() != null) {
            promotion.getServiceIds().clear();
            promotion.getServiceIds().addAll(req.getServiceIds());
        }
        return toDto(promotionRepository.save(promotion));
    }

    @Transactional
    public void toggleActive(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Promotion promotion = promotionRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.promotion(id.toString()));
        promotion.setIsActive(!Boolean.TRUE.equals(promotion.getIsActive()));
        promotionRepository.save(promotion);
    }

    @Transactional
    public void deletePromotion(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Promotion promotion = promotionRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.promotion(id.toString()));
        promotion.setDeletedAt(Instant.now());
        promotionRepository.save(promotion);
    }

    private PromotionDto toDto(Promotion p) {
        return PromotionDto.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .discountType(p.getDiscountType().getValue())
                .discountValue(p.getDiscountValue())
                .validFrom(p.getValidFrom())
                .validTo(p.getValidTo())
                .isActive(p.getIsActive())
                .serviceIds(new ArrayList<>(p.getServiceIds()))
                .build();
    }
}
