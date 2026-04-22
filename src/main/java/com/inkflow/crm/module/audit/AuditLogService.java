package com.inkflow.crm.module.audit;

import com.inkflow.crm.domain.entity.AuditLogEntry;
import com.inkflow.crm.domain.repository.AuditLogRepository;
import com.inkflow.crm.module.audit.dto.AuditLogDto;
import com.inkflow.crm.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repo;

    @Async
    public void log(UUID tenantId, UUID actorId, String actorName,
                    String action, String entityType, String entityId, String entityLabel,
                    String details) {
        try {
            repo.save(AuditLogEntry.builder()
                    .tenantId(tenantId)
                    .actorId(actorId)
                    .actorName(actorName)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .entityLabel(entityLabel)
                    .details(details)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    /** Convenience: infer context from SecurityUtils */
    @Async
    public void logCurrent(String action, String entityType, String entityId, String entityLabel) {
        try {
            com.inkflow.crm.security.UserPrincipal principal = SecurityUtils.getCurrentUser();
            if (principal == null) return;
            UUID tenantId = principal.getTenantId();
            UUID actorId = principal.getId();
            String actorName = principal.getEmail();
            log(tenantId, actorId, actorName, action, entityType, entityId, entityLabel, null);
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getLog(UUID actorId, String entityType, Instant from, Instant to, int page, int size) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Specification<AuditLogEntry> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return repo.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toDto);
    }

    private AuditLogDto toDto(AuditLogEntry e) {
        return AuditLogDto.builder()
                .id(e.getId())
                .actorId(e.getActorId())
                .actorName(e.getActorName())
                .action(e.getAction())
                .entityType(e.getEntityType())
                .entityId(e.getEntityId())
                .entityLabel(e.getEntityLabel())
                .details(e.getDetails())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
