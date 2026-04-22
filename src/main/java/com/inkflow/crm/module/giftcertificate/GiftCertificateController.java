package com.inkflow.crm.module.giftcertificate;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.giftcertificate.dto.CreateCertificateRequest;
import com.inkflow.crm.module.giftcertificate.dto.GiftCertificateDto;
import com.inkflow.crm.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/gift-certificates")
@RequiredArgsConstructor
public class GiftCertificateController {

    private final GiftCertificateService service;

    @GetMapping
    @RequirePermission("finance.view")
    public ResponseEntity<ApiResponse<Page<GiftCertificateDto>>> getAll(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.getAll(search, page, size)));
    }

    @GetMapping("/lookup/{code}")
    @RequirePermission({"calendar.view_all", "calendar.view_own"})
    public ResponseEntity<ApiResponse<GiftCertificateDto>> lookup(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(service.getByCode(code)));
    }

    @PostMapping
    @RequirePermission("finance.create")
    public ResponseEntity<ApiResponse<GiftCertificateDto>> create(@Valid @RequestBody CreateCertificateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(req)));
    }

    @PostMapping("/{code}/redeem")
    @RequirePermission({"calendar.view_all", "calendar.view_own"})
    public ResponseEntity<ApiResponse<GiftCertificateDto>> redeem(
            @PathVariable String code,
            @RequestBody RedeemRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.redeem(code, req.getAmount())));
    }

    @PostMapping("/{id}/cancel")
    @RequirePermission("finance.create")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id) {
        service.cancel(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @DeleteMapping("/{id}")
    @RequirePermission("finance.create")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @Data
    public static class RedeemRequest {
        private BigDecimal amount;
    }
}
