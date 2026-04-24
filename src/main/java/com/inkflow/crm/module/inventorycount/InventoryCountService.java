package com.inkflow.crm.module.inventorycount;

import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.*;
import com.inkflow.crm.domain.repository.*;
import com.inkflow.crm.module.inventorycount.dto.*;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryCountService {

    private final InventoryCountRepository countRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockOperationRepository stockOperationRepository;

    @Transactional(readOnly = true)
    public Page<InventoryCountDto> getAll(int page, int size) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return countRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId, PageRequest.of(page, size))
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public InventoryCountDto getById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return toDto(countRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Count not found")));
    }

    @Transactional
    public InventoryCountDto create(CreateInventoryCountRequest req) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Warehouse warehouse = null;
        if (req.getWarehouseId() != null) {
            warehouse = warehouseRepository.findByIdAndTenantIdAndDeletedAtIsNull(req.getWarehouseId(), tenantId).orElse(null);
        }

        String name = req.getName() != null && !req.getName().isBlank()
                ? req.getName()
                : "Інвентаризація від " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        InventoryCount count = InventoryCount.builder()
                .tenantId(tenantId)
                .name(name)
                .status("IN_PROGRESS")
                .warehouse(warehouse)
                .build();

        // Pre-fill with all active products
        List<Product> products = productRepository.findByTenantIdAndIsActiveTrueAndDeletedAtIsNull(tenantId);
        for (Product p : products) {
            InventoryCountItem item = InventoryCountItem.builder()
                    .count(count)
                    .product(p)
                    .expectedQty(p.getStockQuantity())
                    .actualQty(null)
                    .build();
            count.getItems().add(item);
        }

        return toDto(countRepository.save(count));
    }

    @Transactional
    public InventoryCountDto updateItems(UUID id, UpdateCountItemsRequest req) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        InventoryCount count = countRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Count not found"));

        if ("DONE".equals(count.getStatus())) {
            throw new IllegalStateException("Count already finished");
        }

        for (var update : req.getItems()) {
            count.getItems().stream()
                    .filter(i -> i.getProduct().getId().equals(update.getProductId()))
                    .findFirst()
                    .ifPresent(i -> i.setActualQty(update.getActualQty()));
        }

        return toDto(countRepository.save(count));
    }

    @Transactional
    public InventoryCountDto confirm(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        InventoryCount count = countRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Count not found"));

        if ("DONE".equals(count.getStatus())) {
            throw new IllegalStateException("Already confirmed");
        }

        // Create ADJUSTMENT operations for discrepancies
        for (InventoryCountItem item : count.getItems()) {
            if (item.getActualQty() == null) continue;
            double diff = item.getActualQty() - item.getExpectedQty();
            if (Math.abs(diff) < 0.001) continue;

            Product product = item.getProduct();
            product.setStockQuantity(Math.max(0, product.getStockQuantity() + diff));
            productRepository.save(product);

            StockOperation op = StockOperation.builder()
                    .tenantId(tenantId)
                    .product(product)
                    .type("ADJUSTMENT")
                    .quantity(Math.abs(diff))
                    .note("Інвентаризація: " + count.getName() + (diff < 0 ? " (нестача)" : " (надлишок)"))
                    .build();
            stockOperationRepository.save(op);
        }

        count.setStatus("DONE");
        return toDto(countRepository.save(count));
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        InventoryCount count = countRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Count not found"));
        count.softDelete();
        countRepository.save(count);
    }

    private InventoryCountDto toDto(InventoryCount c) {
        List<InventoryCountItemDto> items = c.getItems().stream().map(i ->
                InventoryCountItemDto.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .productName(i.getProduct().getName())
                        .productUnit(i.getProduct().getUnit())
                        .expectedQty(i.getExpectedQty())
                        .actualQty(i.getActualQty())
                        .build()
        ).collect(Collectors.toList());

        return InventoryCountDto.builder()
                .id(c.getId())
                .name(c.getName())
                .status(c.getStatus())
                .warehouseId(c.getWarehouse() != null ? c.getWarehouse().getId() : null)
                .warehouseName(c.getWarehouse() != null ? c.getWarehouse().getName() : null)
                .items(items)
                .createdAt(c.getCreatedAt())
                .build();
    }
}
