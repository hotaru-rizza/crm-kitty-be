package com.inkflow.crm.module.devadmin;

import com.inkflow.crm.config.BypassTenantFilter;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.infrastructure.supabase.SupabaseAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
@BypassTenantFilter
public class DevAdminService {

    private final TenantRepository tenantRepository;
    private final StaffRepository staffRepository;
    private final SupabaseAdminService supabaseAdminService;
    private final JdbcTemplate jdbcTemplate;

    public List<Tenant> listTenants() {
        return tenantRepository.findAll();
    }

    @Transactional
    public void deleteTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        log.warn("DEV ADMIN: Purging tenant {} ({})", tenant.getName(), tenantId);

        deleteSupabaseUsersForTenant(tenantId);

        // 1. Leaf tables with no tenant_id (linked via parent FK)
        deleteViaParent("artist_service_pricing", "staff_id", "staff", tenantId);
        deleteViaParent("staff_schedules", "staff_id", "staff", tenantId);
        deleteViaParent("staff_faq", "staff_id", "staff", tenantId);
        deleteViaParent("staff_dont_do", "staff_id", "staff", tenantId);
        deleteViaParent("staff_portfolio_images", "staff_id", "staff", tenantId);
        deleteViaParent("staff_specializations", "staff_id", "staff", tenantId);
        deleteViaParent("staff_locations", "staff_id", "staff", tenantId);
        deleteViaParent("staff_invite_locations", "invite_id", "staff_invites", tenantId);
        deleteViaParent("client_tags", "client_id", "clients", tenantId);
        deleteViaParent("client_medical_conditions", "client_id", "clients", tenantId);

        // 2. Tables with tenant_id, in strict FK order (children before parents)
        deleteTenantRows("email_message", tenantId);
        deleteTenantRows("email_template", tenantId);
        deleteTenantRows("client_balance_entries", tenantId);
        deleteTenantRows("monobank_invoices", tenantId);
        deleteTenantRows("transactions", tenantId);
        deleteTenantRows("gallery_photos", tenantId);
        deleteTenantRows("appointment_items", tenantId);
        deleteTenantRows("appointments", tenantId);
        deleteTenantRows("projects", tenantId);
        deleteTenantRows("requests", tenantId);
        deleteTenantRows("leave_requests", tenantId);
        deleteTenantRows("notifications", tenantId);
        deleteTenantRows("staff_invites", tenantId);
        deleteTenantRows("staff", tenantId);
        deleteTenantRows("clients", tenantId);
        deleteTenantRows("services", tenantId);
        deleteTenantRows("locations", tenantId);
        deleteTenantRows("audit_log", tenantId);
        deleteTenantRows("role_permissions", tenantId);
        deleteTenantRows("subscriptions", tenantId);
        deleteTenantRows("transaction_category_configs", tenantId);

        // 3. Legacy / optional tables with direct FK to tenants (schema version dependent)
        deleteTenantRowsIfExists("company_settings", tenantId);
        deleteTenantRowsIfExists("notification_preference", tenantId);
        deleteTenantRowsIfExists("email_template_override", tenantId);

        tenantRepository.delete(tenant);
        log.warn("DEV ADMIN: Tenant {} purged completely", tenantId);
    }

    @Transactional
    public void deleteStaffMember(UUID staffId) {
        Staff staff = staffRepository.findByIdAndDeletedAtIsNull(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Staff not found: " + staffId));

        log.warn("DEV ADMIN: Deleting staff {} ({})", staff.getFullName(), staffId);

        if (staff.getAuthUserId() != null) {
            supabaseAdminService.deleteUser(staff.getAuthUserId());
        }

        String[] staffChildTables = {
                "leave_requests",
                "artist_service_pricing",
                "staff_schedules",
                "staff_faq",
                "staff_dont_do",
                "staff_portfolio_images",
                "staff_specializations",
                "staff_locations",
        };
        for (String table : staffChildTables) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE staff_id = ?", staffId);
        }

        jdbcTemplate.update("UPDATE appointments SET artist_id = NULL WHERE artist_id = ?", staffId);
        jdbcTemplate.update("UPDATE transactions SET staff_id = NULL WHERE staff_id = ?", staffId);
        jdbcTemplate.update("UPDATE transactions SET processed_by_id = NULL WHERE processed_by_id = ?", staffId);
        jdbcTemplate.update("UPDATE requests SET staff_id = NULL WHERE staff_id = ?", staffId);
        jdbcTemplate.update("UPDATE projects SET artist_id = NULL WHERE artist_id = ?", staffId);

        staffRepository.delete(staff);
        log.warn("DEV ADMIN: Staff {} deleted", staffId);
    }

    public record TenantDetail(Tenant tenant, List<StaffSummary> staff, long locationCount) {}
    public record StaffSummary(UUID id, String firstName, String lastName, String email, String role, String authUserId) {}

    public TenantDetail getTenantDetail(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        List<StaffSummary> staffSummaries = jdbcTemplate.query(
                """
                SELECT id, first_name, last_name, role, email, auth_user_id
                FROM staff
                WHERE tenant_id = ? AND deleted_at IS NULL
                ORDER BY last_name, first_name
                """,
                (rs, rowNum) -> new StaffSummary(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getString("role"),
                        rs.getString("auth_user_id")
                ),
                tenantId
        );

        Long locationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM locations WHERE tenant_id = ? AND deleted_at IS NULL", Long.class, tenantId);

        return new TenantDetail(tenant, staffSummaries, locationCount != null ? locationCount : 0);
    }

    private void deleteSupabaseUsersForTenant(UUID tenantId) {
        List<String> authUserIds = jdbcTemplate.queryForList(
                "SELECT auth_user_id FROM staff WHERE tenant_id = ? AND auth_user_id IS NOT NULL",
                String.class, tenantId);
        for (String authUserId : authUserIds) {
            supabaseAdminService.deleteUser(authUserId);
        }
    }

    private void deleteTenantRows(String table, UUID tenantId) {
        int deleted = jdbcTemplate.update("DELETE FROM " + table + " WHERE tenant_id = ?", tenantId);
        if (deleted > 0) {
            log.info("  Deleted {} rows from {}", deleted, table);
        }
    }

    private void deleteTenantRowsIfExists(String table, UUID tenantId) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_name = ?
                )
                """,
                Boolean.class,
                table
        );
        if (Boolean.TRUE.equals(exists)) {
            deleteTenantRows(table, tenantId);
        }
    }

    private void deleteViaParent(String childTable, String childFk, String parentTable, UUID tenantId) {
        String sql = "DELETE FROM " + childTable
                + " WHERE " + childFk + " IN (SELECT id FROM " + parentTable + " WHERE tenant_id = ?)";
        int deleted = jdbcTemplate.update(sql, tenantId);
        if (deleted > 0) {
            log.info("  Deleted {} rows from {} (via {})", deleted, childTable, parentTable);
        }
    }
}
