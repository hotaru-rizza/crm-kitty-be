package com.inkflow.crm.module.appointment.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.AppointmentItem;
import com.inkflow.crm.domain.entity.ArtistServicePricing;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AppointmentItemSource;
import com.inkflow.crm.domain.repository.ArtistServicePricingRepository;
import com.inkflow.crm.module.appointment.dto.AppointmentItemRequest;
import com.inkflow.crm.module.appointment.dto.CreateAppointmentRequest;
import com.inkflow.crm.module.appointment.dto.UpdateAppointmentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AppointmentItemService {

    private final AppointmentEntityResolver entityResolver;
    private final AppointmentPricingService pricingService;
    private final ArtistServicePricingRepository artistServicePricingRepository;

    public List<AppointmentItem> buildItemsForCreate(
            UUID tenantId,
            CreateAppointmentRequest request,
            Staff artist,
            Appointment appointment) {
        List<AppointmentItemRequest> itemRequests = resolveCreateItemRequests(request);
        validateItemRequests(itemRequests);

        return buildItems(tenantId, itemRequests, artist, appointment);
    }

    public void replaceItems(
            UUID tenantId,
            Appointment appointment,
            UpdateAppointmentRequest request,
            Staff artist) {
        if (request.getItems() == null) {
            return;
        }

        validateItemRequests(request.getItems());
        appointment.getItems().clear();
        appointment.getItems().addAll(buildItems(tenantId, request.getItems(), artist, appointment));
    }

    private List<AppointmentItemRequest> resolveCreateItemRequests(CreateAppointmentRequest request) {
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            return request.getItems();
        }

        if (request.getServiceId() == null || request.getPrice() == null) {
            throw new BusinessRuleException("Either items or serviceId with price are required");
        }

        int durationMinutes = resolveLegacyDurationMinutes(request.getStartTime(), request.getEndTime());

        return List.of(AppointmentItemRequest.builder()
                .source(AppointmentItemSource.SERVICE.getValue())
                .serviceId(request.getServiceId())
                .unitPrice(request.getPrice())
                .durationMinutes(durationMinutes)
                .quantity(1)
                .sortOrder(0)
                .build());
    }

    private List<AppointmentItem> buildItems(
            UUID tenantId,
            List<AppointmentItemRequest> itemRequests,
            Staff artist,
            Appointment appointment) {
        List<AppointmentItem> items = new ArrayList<>();

        for (int index = 0; index < itemRequests.size(); index++) {
            AppointmentItemRequest itemRequest = itemRequests.get(index);
            items.add(buildItem(tenantId, itemRequest, artist, appointment, index));
        }

        return items;
    }

    private AppointmentItem buildItem(
            UUID tenantId,
            AppointmentItemRequest itemRequest,
            Staff artist,
            Appointment appointment,
            int index) {
        AppointmentItemSource source = resolveSource(itemRequest);
        int quantity = itemRequest.getQuantity() != null && itemRequest.getQuantity() > 0
                ? itemRequest.getQuantity()
                : 1;
        int sortOrder = itemRequest.getSortOrder() != null ? itemRequest.getSortOrder() : index;

        if (source == AppointmentItemSource.SERVICE) {
            return buildServiceItem(tenantId, itemRequest, artist, appointment, quantity, sortOrder);
        }

        return buildCustomItem(tenantId, itemRequest, appointment, quantity, sortOrder);
    }

    private AppointmentItem buildServiceItem(
            UUID tenantId,
            AppointmentItemRequest itemRequest,
            Staff artist,
            Appointment appointment,
            int quantity,
            int sortOrder) {
        if (itemRequest.getServiceId() == null) {
            throw new BusinessRuleException("Service ID is required for service line items");
        }

        Service service = entityResolver.requireService(tenantId, itemRequest.getServiceId());
        ArtistServicePricing staffPricing = artistServicePricingRepository
                .findByStaffIdAndServiceId(artist.getId(), service.getId())
                .orElse(null);

        int durationMinutes = itemRequest.getDurationMinutes() != null
                ? itemRequest.getDurationMinutes()
                : resolveServiceDuration(service, staffPricing);

        BigDecimal unitPrice = itemRequest.getUnitPrice() != null
                ? itemRequest.getUnitPrice()
                : resolveStaffPrice(staffPricing, service, durationMinutes);

        AppointmentItem item = AppointmentItem.builder()
                .tenantId(tenantId)
                .appointment(appointment)
                .service(service)
                .source(AppointmentItemSource.SERVICE)
                .title(service.getTitle())
                .quantity(quantity)
                .unitPrice(unitPrice)
                .durationMinutes(durationMinutes)
                .sortOrder(sortOrder)
                .build();
        item.recalculateLineTotal();
        return item;
    }

    private AppointmentItem buildCustomItem(
            UUID tenantId,
            AppointmentItemRequest itemRequest,
            Appointment appointment,
            int quantity,
            int sortOrder) {
        if (itemRequest.getTitle() == null || itemRequest.getTitle().isBlank()) {
            throw new BusinessRuleException("Title is required for custom line items");
        }
        if (itemRequest.getUnitPrice() == null) {
            throw new BusinessRuleException("Unit price is required for custom line items");
        }

        int durationMinutes = itemRequest.getDurationMinutes() != null ? itemRequest.getDurationMinutes() : 0;

        AppointmentItem item = AppointmentItem.builder()
                .tenantId(tenantId)
                .appointment(appointment)
                .source(AppointmentItemSource.CUSTOM)
                .title(itemRequest.getTitle().trim())
                .quantity(quantity)
                .unitPrice(itemRequest.getUnitPrice())
                .durationMinutes(durationMinutes)
                .sortOrder(sortOrder)
                .build();
        item.recalculateLineTotal();
        return item;
    }

    private AppointmentItemSource resolveSource(AppointmentItemRequest itemRequest) {
        if (itemRequest.getSource() != null && !itemRequest.getSource().isBlank()) {
            return AppointmentItemSource.fromValue(itemRequest.getSource());
        }
        return itemRequest.getServiceId() != null
                ? AppointmentItemSource.SERVICE
                : AppointmentItemSource.CUSTOM;
    }

    private int resolveServiceDuration(Service service, ArtistServicePricing staffPricing) {
        if (staffPricing != null && staffPricing.getDuration() != null && staffPricing.getDuration() > 0) {
            return staffPricing.getDuration();
        }
        return service.getDuration() != null ? service.getDuration() : 60;
    }

    private BigDecimal resolveStaffPrice(
            ArtistServicePricing staffPricing,
            Service service,
            int durationMinutes) {
        if (staffPricing != null && staffPricing.getPrice() != null) {
            return staffPricing.getPrice();
        }
        return pricingService.suggestUnitPrice(service, durationMinutes);
    }

    private int resolveLegacyDurationMinutes(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            return 60;
        }
        long minutes = Duration.between(startTime, endTime).toMinutes();
        return (int) Math.max(minutes, 1);
    }

    private void validateItemRequests(List<AppointmentItemRequest> itemRequests) {
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new BusinessRuleException("At least one line item is required");
        }
    }
}
