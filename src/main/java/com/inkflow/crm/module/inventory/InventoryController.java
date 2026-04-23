package com.inkflow.crm.module.inventory;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.module.inventory.dto.*;
import com.inkflow.crm.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/products")
    @RequirePermission({"inventory.view"})
    public ResponseEntity<ApiResponse<List<ProductDto>>> getProducts() {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getProducts()));
    }

    @GetMapping("/products/low-stock")
    @RequirePermission({"inventory.view"})
    public ResponseEntity<ApiResponse<List<ProductDto>>> getLowStock() {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getLowStockProducts()));
    }

    @PostMapping("/products")
    @RequirePermission({"inventory.manage"})
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(@Valid @RequestBody CreateProductRequest req) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.createProduct(req)));
    }

    @PutMapping("/products/{id}")
    @RequirePermission({"inventory.manage"})
    public ResponseEntity<ApiResponse<ProductDto>> updateProduct(
            @PathVariable UUID id, @RequestBody UpdateProductRequest req) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.updateProduct(id, req)));
    }

    @DeleteMapping("/products/{id}")
    @RequirePermission({"inventory.manage"})
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID id) {
        inventoryService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/operations")
    @RequirePermission({"inventory.manage"})
    public ResponseEntity<ApiResponse<StockOperationDto>> addOperation(@Valid @RequestBody AddStockRequest req) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.addStockOperation(req)));
    }

    @GetMapping("/operations")
    @RequirePermission({"inventory.view"})
    public ResponseEntity<ApiResponse<PageResult<StockOperationDto>>> getOperations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        org.springframework.data.domain.Page<StockOperationDto> result = inventoryService.getOperations(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                new PageResult<>(result.getContent(), PaginationDto.from(result))));
    }

    @GetMapping("/products/{id}/operations")
    @RequirePermission({"inventory.view"})
    public ResponseEntity<ApiResponse<List<StockOperationDto>>> getProductOperations(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getProductOperations(id)));
    }
}
