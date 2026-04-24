package com.inkflow.crm.module.invoice;

import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.*;
import com.inkflow.crm.domain.repository.*;
import com.inkflow.crm.module.invoice.dto.*;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplyInvoiceService {

    private final SupplyInvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockOperationRepository stockOperationRepository;

    @Transactional(readOnly = true)
    public Page<SupplyInvoiceDto> getAll(int page, int size) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return invoiceRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId, PageRequest.of(page, size))
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public SupplyInvoiceDto getById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return toDto(invoiceRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Invoice not found")));
    }

    @Transactional
    public SupplyInvoiceDto create(CreateSupplyInvoiceRequest req) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Warehouse warehouse = null;
        if (req.getWarehouseId() != null) {
            warehouse = warehouseRepository.findByIdAndTenantIdAndDeletedAtIsNull(req.getWarehouseId(), tenantId).orElse(null);
        }

        String name = req.getName() != null && !req.getName().isBlank()
                ? req.getName()
                : "Накладна від " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        SupplyInvoice invoice = SupplyInvoice.builder()
                .tenantId(tenantId)
                .name(name)
                .supplierName(req.getSupplierName())
                .note(req.getNote())
                .status("DRAFT")
                .warehouse(warehouse)
                .build();

        if (req.getItems() != null) {
            for (var item : req.getItems()) {
                Product product = productRepository.findByIdAndTenantIdAndDeletedAtIsNull(item.getProductId(), tenantId)
                        .orElse(null);
                if (product == null) continue;
                SupplyInvoiceItem si = SupplyInvoiceItem.builder()
                        .invoice(invoice)
                        .product(product)
                        .quantity(item.getQuantity())
                        .costPerUnit(item.getCostPerUnit())
                        .build();
                invoice.getItems().add(si);
            }
        }

        return toDto(invoiceRepository.save(invoice));
    }

    @Transactional
    public SupplyInvoiceDto confirm(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        SupplyInvoice invoice = invoiceRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Invoice not found"));

        if ("CONFIRMED".equals(invoice.getStatus())) {
            throw new IllegalStateException("Invoice already confirmed");
        }

        invoice.setStatus("CONFIRMED");

        // Create ARRIVAL stock operations for each item
        for (SupplyInvoiceItem item : invoice.getItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            if (item.getCostPerUnit() != null) {
                product.setCostPerUnit(item.getCostPerUnit());
            }
            productRepository.save(product);

            StockOperation op = StockOperation.builder()
                    .tenantId(tenantId)
                    .product(product)
                    .type("ARRIVAL")
                    .quantity(item.getQuantity())
                    .costPerUnit(item.getCostPerUnit() != null ? item.getCostPerUnit() : product.getCostPerUnit())
                    .note("Накладна: " + invoice.getName())
                    .build();
            stockOperationRepository.save(op);
        }

        return toDto(invoiceRepository.save(invoice));
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        SupplyInvoice invoice = invoiceRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Invoice not found"));
        if ("CONFIRMED".equals(invoice.getStatus())) {
            throw new IllegalStateException("Cannot delete confirmed invoice");
        }
        invoice.softDelete();
        invoiceRepository.save(invoice);
    }

    private SupplyInvoiceDto toDto(SupplyInvoice inv) {
        List<SupplyInvoiceItemDto> items = inv.getItems().stream().map(i ->
                SupplyInvoiceItemDto.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .productName(i.getProduct().getName())
                        .productUnit(i.getProduct().getUnit())
                        .quantity(i.getQuantity())
                        .costPerUnit(i.getCostPerUnit())
                        .build()
        ).collect(Collectors.toList());

        BigDecimal total = items.stream()
                .filter(i -> i.getCostPerUnit() != null)
                .map(i -> i.getCostPerUnit().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SupplyInvoiceDto.builder()
                .id(inv.getId())
                .name(inv.getName())
                .supplierName(inv.getSupplierName())
                .note(inv.getNote())
                .status(inv.getStatus())
                .warehouseId(inv.getWarehouse() != null ? inv.getWarehouse().getId() : null)
                .warehouseName(inv.getWarehouse() != null ? inv.getWarehouse().getName() : null)
                .items(items)
                .totalCost(total)
                .createdAt(inv.getCreatedAt())
                .build();
    }
}
