package com.inkflow.crm.module.service.mapper;

import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.enums.PricingType;
import com.inkflow.crm.module.service.dto.*;
import org.mapstruct.*;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ServiceMapper {

    @Mapping(target = "pricingType", expression = "java(service.getPricingType().getValue())")
    ServiceDto toDto(Service service);

    List<ServiceDto> toDtoList(List<Service> services);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "artistPricings", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "pricingType", expression = "java(mapPricingType(request.getPricingType()))")
    Service toEntity(CreateServiceRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "artistPricings", ignore = true)
    @Mapping(target = "pricingType", expression = "java(request.getPricingType() != null ? mapPricingType(request.getPricingType()) : service.getPricingType())")
    void updateEntity(UpdateServiceRequest request, @MappingTarget Service service);

    default ServiceDetailDto toDetailDto(Service service) {
        List<ServiceDetailDto.ArtistPricingOverrideDto> artistPricing = service.getArtistPricings().stream()
                .map(ap -> ServiceDetailDto.ArtistPricingOverrideDto.builder()
                        .artistId(ap.getStaff().getId())
                        .artistName(ap.getStaff().getFullName())
                        .price(ap.getPrice())
                        .duration(ap.getDuration())
                        .build())
                .collect(Collectors.toList());

        return ServiceDetailDto.builder()
                .id(service.getId())
                .title(service.getTitle())
                .description(service.getDescription())
                .pricingType(service.getPricingType().getValue())
                .price(service.getPrice())
                .duration(service.getDuration())
                .color(service.getColor())
                .isActive(service.getIsActive())
                .costPrice(service.getCostPrice())
                .artistPricing(artistPricing)
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }

    default PricingType mapPricingType(String pricingType) {
        return PricingType.fromValue(pricingType);
    }
}
