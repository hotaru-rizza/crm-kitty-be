package com.inkflow.crm.module.giftcertificate;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.GiftCertificate;
import com.inkflow.crm.domain.repository.GiftCertificateRepository;
import com.inkflow.crm.module.giftcertificate.dto.CreateCertificateRequest;
import com.inkflow.crm.module.giftcertificate.dto.GiftCertificateDto;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GiftCertificateService {

    private final GiftCertificateRepository repo;
    private static final SecureRandom RNG = new SecureRandom();
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Transactional(readOnly = true)
    public Page<GiftCertificateDto> getAll(String search, int page, int size) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return repo.findAll(tenantId, search, PageRequest.of(page, size)).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public GiftCertificateDto getByCode(String code) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return repo.findByCodeAndTenantIdAndDeletedAtIsNull(code, tenantId)
                .map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found: " + code));
    }

    @Transactional
    public GiftCertificateDto create(CreateCertificateRequest req) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        String code = generateUniqueCode();
        GiftCertificate cert = GiftCertificate.builder()
                .tenantId(tenantId)
                .code(code)
                .initialAmount(req.getAmount())
                .remainingAmount(req.getAmount())
                .buyerName(req.getBuyerName())
                .buyerPhone(req.getBuyerPhone())
                .holderName(req.getHolderName())
                .holderPhone(req.getHolderPhone())
                .notes(req.getNotes())
                .status("ACTIVE")
                .expiresAt(req.getExpiresAt())
                .build();
        return toDto(repo.save(cert));
    }

    @Transactional
    public GiftCertificateDto redeem(String code, BigDecimal amount) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        GiftCertificate cert = repo.findByCodeAndTenantIdAndDeletedAtIsNull(code, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found: " + code));

        if (!cert.isUsable()) throw new IllegalStateException("Certificate is not usable");
        if (amount.compareTo(cert.getRemainingAmount()) > 0)
            throw new IllegalArgumentException("Amount exceeds remaining balance");

        cert.setRemainingAmount(cert.getRemainingAmount().subtract(amount));
        if (cert.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
            cert.setStatus("USED");
            cert.setUsedAt(Instant.now());
        }
        return toDto(repo.save(cert));
    }

    @Transactional
    public void cancel(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        GiftCertificate cert = repo.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.promotion(id.toString()));
        cert.setStatus("CANCELLED");
        repo.save(cert);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        GiftCertificate cert = repo.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.promotion(id.toString()));
        cert.setDeletedAt(Instant.now());
        repo.save(cert);
    }

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder("GC-");
            for (int i = 0; i < 8; i++) sb.append(CHARS.charAt(RNG.nextInt(CHARS.length())));
            code = sb.toString();
        } while (repo.existsByCodeAndDeletedAtIsNull(code));
        return code;
    }

    private GiftCertificateDto toDto(GiftCertificate g) {
        return GiftCertificateDto.builder()
                .id(g.getId())
                .code(g.getCode())
                .initialAmount(g.getInitialAmount())
                .remainingAmount(g.getRemainingAmount())
                .buyerName(g.getBuyerName())
                .buyerPhone(g.getBuyerPhone())
                .holderName(g.getHolderName())
                .holderPhone(g.getHolderPhone())
                .notes(g.getNotes())
                .status(g.getStatus())
                .expiresAt(g.getExpiresAt())
                .usedAt(g.getUsedAt())
                .createdAt(g.getCreatedAt())
                .build();
    }
}
