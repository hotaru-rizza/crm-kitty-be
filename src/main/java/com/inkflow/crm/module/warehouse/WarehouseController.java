package com.inkflow.crm.module.warehouse;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.warehouse.dto.CreateWarehouseRequest;
import com.inkflow.crm.module.warehouse.dto.WarehouseDto;
import com.inkflow.crm.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/inventory/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    @RequirePermission({"inventory.view"})
    public ResponseEntity<ApiResponse<List<WarehouseDto>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.getAll()));
    }

    @PostMapping
    @RequirePermission({"inventory.manage"})
    public ResponseEntity<ApiResponse<WarehouseDto>> create(@RequestBody CreateWarehouseRequest req) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.create(req)));
    }

    @PutMapping("/{id}")
    @RequirePermission({"inventory.manage"})
    public ResponseEntity<ApiResponse<WarehouseDto>> update(@PathVariable UUID id, @RequestBody CreateWarehouseRequest req) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    @RequirePermission({"inventory.manage"})
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        warehouseService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
