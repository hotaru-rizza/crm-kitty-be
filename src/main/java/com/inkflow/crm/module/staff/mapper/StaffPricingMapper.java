package com.inkflow.crm.module.staff.mapper;

import com.inkflow.crm.domain.entity.ArtistServicePricing;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.module.staff.dto.StaffServiceDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StaffPricingMapper {

    public StaffServiceDto toDto(ArtistServicePricing pricing) {
        Service service = pricing.getService();

        return StaffServiceDto.builder()
                .id(pricing.getId())
                .serviceId(service.getId())
                .title(service.getTitle())
                .description(service.getDescription())
                .pricingType(service.getPricingType().getValue())
                .basePrice(service.getPrice())
                .customPrice(hasCustomPrice(pricing, service) ? pricing.getPrice() : null)
                .baseDuration(service.getDuration())
                .customDuration(hasCustomDuration(pricing, service) ? pricing.getDuration() : null)
                .color(service.getColor())
                .isActive(service.getIsActive())
                .build();
    }

    public ArtistServicePricing toEntity(
            Staff staff,
            Service service,
            BigDecimal customPrice,
            Integer customDuration) {
        return ArtistServicePricing.builder()
                .staff(staff)
                .service(service)
                .price(customPrice != null ? customPrice : service.getPrice())
                .duration(customDuration != null ? customDuration : service.getDuration())
                .build();
    }

    private boolean hasCustomPrice(ArtistServicePricing pricing, Service service) {
        return !pricing.getPrice().equals(service.getPrice());
    }

    private boolean hasCustomDuration(ArtistServicePricing pricing, Service service) {
        return pricing.getDuration() != null && !pricing.getDuration().equals(service.getDuration());
    }
}
