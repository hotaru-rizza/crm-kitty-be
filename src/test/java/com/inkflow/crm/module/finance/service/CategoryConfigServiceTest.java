package com.inkflow.crm.module.finance.service;

import com.inkflow.crm.domain.entity.TransactionCategoryConfig;
import com.inkflow.crm.domain.repository.TransactionCategoryConfigRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryConfigServiceTest {

    @Mock
    private TransactionCategoryConfigRepository repo;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AuditLabelFormatter auditLabelFormatter;

    @InjectMocks
    private CategoryConfigService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void stubAuditLabels() {
        lenient().when(auditLabelFormatter.financeCategory(anyString())).thenAnswer(inv -> "Категорія · " + inv.getArgument(0));
    }

    @Test
    void ensureDefaults_seedsDefaultCategoriesForNewTenant() {
        UUID tenantId = UUID.randomUUID();
        when(repo.existsByDeletedAtIsNull()).thenReturn(false);
        when(repo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.ensureDefaults(tenantId);

        ArgumentCaptor<TransactionCategoryConfig> captor = ArgumentCaptor.forClass(TransactionCategoryConfig.class);
        verify(repo, atLeastOnce()).save(captor.capture());

        List<TransactionCategoryConfig> saved = captor.getAllValues();
        assertEquals(8, saved.size());
        assertEquals(tenantId, saved.getFirst().getTenantId());
    }

    @Test
    void ensureDefaults_skipsWhenTenantAlreadyHasCategories() {
        UUID tenantId = UUID.randomUUID();
        when(repo.existsByDeletedAtIsNull()).thenReturn(true);

        service.ensureDefaults(tenantId);

        verify(repo, never()).save(any());
    }

    @Test
    void delete_skipsDefaultCategory() {
        UUID tenantId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        authenticate(tenantId);

        TransactionCategoryConfig defaultConfig = TransactionCategoryConfig.builder()
                .id(configId)
                .tenantId(tenantId)
                .categoryKey("service")
                .isDefault(true)
                .build();

        when(repo.findById(configId)).thenReturn(Optional.of(defaultConfig));

        service.delete(configId);

        verify(repo, never()).save(any());
    }

    @Test
    void delete_softDeletesNonDefaultCategory() {
        UUID tenantId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        authenticate(tenantId);

        TransactionCategoryConfig customConfig = TransactionCategoryConfig.builder()
                .id(configId)
                .tenantId(tenantId)
                .categoryKey("custom_key")
                .label("Custom")
                .isDefault(false)
                .build();

        when(repo.findById(configId)).thenReturn(Optional.of(customConfig));
        when(repo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.delete(configId);

        ArgumentCaptor<TransactionCategoryConfig> captor = ArgumentCaptor.forClass(TransactionCategoryConfig.class);
        verify(repo).save(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    void upsert_createsCustomCategoryWhenMissing() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        when(repo.existsByDeletedAtIsNull()).thenReturn(true);
        when(repo.findByCategoryKeyAndDeletedAtIsNull("custom_key"))
                .thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(invocation -> {
            TransactionCategoryConfig cfg = invocation.getArgument(0);
            cfg.setId(UUID.randomUUID());
            return cfg;
        });

        var dto = service.upsert("custom_key", "Custom", "#111111", "EXPENSE");

        assertNotNull(dto.getId());
        assertEquals("custom_key", dto.getCategoryKey());
    }

    @Test
    void upsert_updatesExistingCategory() {
        UUID tenantId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        authenticate(tenantId);

        TransactionCategoryConfig existing = TransactionCategoryConfig.builder()
                .id(configId)
                .tenantId(tenantId)
                .categoryKey("custom_key")
                .label("Old Label")
                .color("#000000")
                .plType("INCOME")
                .isDefault(false)
                .isActive(true)
                .build();

        when(repo.existsByDeletedAtIsNull()).thenReturn(true);
        when(repo.findByCategoryKeyAndDeletedAtIsNull("custom_key"))
                .thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = service.upsert("custom_key", "Updated Label", "#ffffff", "EXPENSE");

        assertEquals(configId, dto.getId());
        assertEquals("Updated Label", dto.getLabel());
        assertEquals("#ffffff", dto.getColor());
        assertEquals("EXPENSE", dto.getPlType());

        ArgumentCaptor<TransactionCategoryConfig> captor = ArgumentCaptor.forClass(TransactionCategoryConfig.class);
        verify(repo).save(captor.capture());
        assertEquals("Updated Label", captor.getValue().getLabel());
    }

    private void authenticate(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
