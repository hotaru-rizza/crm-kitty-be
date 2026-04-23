package com.inkflow.crm.module.inventory;

import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Product;
import com.inkflow.crm.domain.entity.StockOperation;
import com.inkflow.crm.domain.repository.ProductRepository;
import com.inkflow.crm.domain.repository.StockOperationRepository;
import com.inkflow.crm.module.inventory.dto.*;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final StockOperationRepository stockOperationRepository;

    @Transactional(readOnly = true)
    public List<ProductDto> getProducts() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return productRepository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenantId)
                .stream().map(this::toProductDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getLowStockProducts() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return productRepository.findByTenantIdAndIsActiveTrueAndDeletedAtIsNull(tenantId)
                .stream()
                .filter(p -> p.getMinStockLevel() != null && p.getStockQuantity() <= p.getMinStockLevel())
                .map(this::toProductDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductDto createProduct(CreateProductRequest req) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Product product = Product.builder()
                .tenantId(tenantId)
                .name(req.getName())
                .category(req.getCategory())
                .sku(req.getSku())
                .description(req.getDescription())
                .unit(req.getUnit())
                .costPerUnit(req.getCostPerUnit())
                .stockQuantity(req.getInitialStock() != null ? req.getInitialStock() : 0.0)
                .minStockLevel(req.getMinStockLevel())
                .isActive(true)
                .build();
        Product saved = productRepository.save(product);

        if (req.getInitialStock() != null && req.getInitialStock() > 0) {
            StockOperation op = StockOperation.builder()
                    .tenantId(tenantId)
                    .product(saved)
                    .type("ARRIVAL")
                    .quantity(req.getInitialStock())
                    .costPerUnit(req.getCostPerUnit())
                    .note("Початковий залишок")
                    .build();
            stockOperationRepository.save(op);
        }
        return toProductDto(saved);
    }

    @Transactional
    public ProductDto updateProduct(UUID id, UpdateProductRequest req) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Product product = productRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Product not found"));

        if (req.getName() != null) product.setName(req.getName());
        if (req.getCategory() != null) product.setCategory(req.getCategory());
        if (req.getSku() != null) product.setSku(req.getSku());
        if (req.getDescription() != null) product.setDescription(req.getDescription());
        if (req.getUnit() != null) product.setUnit(req.getUnit());
        if (req.getCostPerUnit() != null) product.setCostPerUnit(req.getCostPerUnit());
        if (req.getMinStockLevel() != null) product.setMinStockLevel(req.getMinStockLevel());
        if (req.getIsActive() != null) product.setIsActive(req.getIsActive());

        return toProductDto(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Product product = productRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Product not found"));
        product.softDelete();
        productRepository.save(product);
    }

    @Transactional
    public StockOperationDto addStockOperation(AddStockRequest req) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Product product = productRepository.findByIdAndTenantIdAndDeletedAtIsNull(req.getProductId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Product not found"));

        String opType = req.getType() != null ? req.getType().toUpperCase() : "ARRIVAL";
        double delta = "WRITEOFF".equals(opType) ? -req.getQuantity() : req.getQuantity();
        product.setStockQuantity(Math.max(0, product.getStockQuantity() + delta));
        productRepository.save(product);

        StockOperation op = StockOperation.builder()
                .tenantId(tenantId)
                .product(product)
                .type(opType)
                .quantity(req.getQuantity())
                .costPerUnit(req.getCostPerUnit() != null ? req.getCostPerUnit() : product.getCostPerUnit())
                .note(req.getNote())
                .build();
        return toOperationDto(stockOperationRepository.save(op));
    }

    @Transactional(readOnly = true)
    public Page<StockOperationDto> getOperations(int page, int size) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return stockOperationRepository
                .findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId, PageRequest.of(page, size))
                .map(this::toOperationDto);
    }

    @Transactional(readOnly = true)
    public List<StockOperationDto> getProductOperations(UUID productId) {
        return stockOperationRepository
                .findByProductIdAndDeletedAtIsNullOrderByCreatedAtDesc(productId)
                .stream().map(this::toOperationDto).collect(Collectors.toList());
    }

    private ProductDto toProductDto(Product p) {
        boolean low = p.getMinStockLevel() != null && p.getStockQuantity() <= p.getMinStockLevel();
        return ProductDto.builder()
                .id(p.getId())
                .name(p.getName())
                .category(p.getCategory())
                .sku(p.getSku())
                .description(p.getDescription())
                .unit(p.getUnit())
                .costPerUnit(p.getCostPerUnit())
                .stockQuantity(p.getStockQuantity())
                .minStockLevel(p.getMinStockLevel())
                .isActive(p.getIsActive())
                .lowStock(low)
                .createdAt(p.getCreatedAt())
                .build();
    }

    private StockOperationDto toOperationDto(StockOperation op) {
        return StockOperationDto.builder()
                .id(op.getId())
                .productId(op.getProduct().getId())
                .productName(op.getProduct().getName())
                .productUnit(op.getProduct().getUnit())
                .type(op.getType())
                .quantity(op.getQuantity())
                .costPerUnit(op.getCostPerUnit())
                .note(op.getNote())
                .staffName(op.getStaff() != null
                        ? op.getStaff().getFirstName() + " " + op.getStaff().getLastName() : null)
                .createdAt(op.getCreatedAt())
                .build();
    }
}
