package com.inkflow.crm.module.invoice;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.module.invoice.dto.*;
import com.inkflow.crm.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/inventory/invoices")
@RequiredArgsConstructor
public class SupplyInvoiceController {

    private final SupplyInvoiceService invoiceService;

    @GetMapping
    @RequirePermission({"inventory.view"})
    public ResponseEntity<ApiResponse<PageResult<SupplyInvoiceDto>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SupplyInvoiceDto> result = invoiceService.getAll(page, size);
        return ResponseEntity.ok(ApiResponse.success(new PageResult<>(result.getContent(), PaginationDto.from(result))));
    }

    @GetMapping("/{id}")
    @RequirePermission({"inventory.view"})
    public ResponseEntity<ApiResponse<SupplyInvoiceDto>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.getById(id)));
    }

    @PostMapping
    @RequirePermission({"inventory.manage"})
    public ResponseEntity<ApiResponse<SupplyInvoiceDto>> create(@RequestBody CreateSupplyInvoiceRequest req) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.create(req)));
    }

    @PostMapping("/{id}/confirm")
    @RequirePermission({"inventory.manage"})
    public ResponseEntity<ApiResponse<SupplyInvoiceDto>> confirm(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.confirm(id)));
    }

    @DeleteMapping("/{id}")
    @RequirePermission({"inventory.manage"})
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        invoiceService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
