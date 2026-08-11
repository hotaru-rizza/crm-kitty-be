package com.inkflow.crm.module.audit.service;

import com.inkflow.crm.common.http.HttpRequestUtils;
import com.inkflow.crm.domain.entity.AuditLogEntry;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
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
                    AuditAction action, AuditEntityType entityType,
                    String entityId, String entityLabel,
                    UUID subjectClientId, String details) {
        log(tenantId, actorId, actorName, action, entityType, entityId, entityLabel,
                subjectClientId, details, null);
    }

    @Async
    public void log(UUID tenantId, UUID actorId, String actorName,
                    AuditAction action, AuditEntityType entityType,
                    String entityId, String entityLabel,
                    UUID subjectClientId, String details, String ipAddress) {
        try {
            repo.save(AuditLogEntry.builder()
                    .tenantId(tenantId)
                    .actorId(actorId)
                    .actorName(actorName)
                    .action(action.getValue())
                    .entityType(entityType.getValue())
                    .entityId(entityId)
                    .entityLabel(entityLabel)
                    .subjectClientId(subjectClientId)
                    .details(details)
                    .ipAddress(ipAddress)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    public void logCurrent(AuditAction action, AuditEntityType entityType,
                           String entityId, String entityLabel) {
        logCurrent(action, entityType, entityId, entityLabel, null, null);
    }

    public void logCurrent(AuditAction action, AuditEntityType entityType,
                           String entityId, String entityLabel, UUID subjectClientId) {
        logCurrent(action, entityType, entityId, entityLabel, subjectClientId, null);
    }

    public void logCurrent(AuditAction action, AuditEntityType entityType,
                           String entityId, String entityLabel,
                           UUID subjectClientId, String details) {
        try {
            com.inkflow.crm.security.UserPrincipal principal = SecurityUtils.getCurrentUser();
            if (principal == null) {
                return;
            }
            log(
                    principal.getTenantId(),
                    principal.getId(),
                    principal.getEmail(),
                    action,
                    entityType,
                    entityId,
                    entityLabel,
                    subjectClientId,
                    details,
                    HttpRequestUtils.clientIpAddress()
            );
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getLog(
            List<UUID> actorIds,
            UUID clientId,
            List<String> actions,
            String entityType,
            String entityId,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Specification<AuditLogEntry> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (actorIds != null && !actorIds.isEmpty()) {
                predicates.add(root.get("actorId").in(actorIds));
            } else {
                // Hide automated system@inkflow rows from the default journal view.
                predicates.add(cb.isNotNull(root.get("actorId")));
            }
            if (clientId != null) {
                predicates.add(cb.equal(root.get("subjectClientId"), clientId));
            }
            if (actions != null && !actions.isEmpty()) {
                predicates.add(root.get("action").in(actions));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (entityId != null && !entityId.isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
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
                .subjectClientId(e.getSubjectClientId())
                .details(e.getDetails())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
