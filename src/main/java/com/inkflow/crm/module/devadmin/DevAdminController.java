package com.inkflow.crm.module.devadmin;

import com.inkflow.crm.domain.entity.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/dev/admin")
@Profile("dev")
@RequiredArgsConstructor
public class DevAdminController {

    private final DevAdminService devAdminService;

    @GetMapping("/tenants")
    public ResponseEntity<Map<String, Object>> listTenants() {
        List<Tenant> tenants = devAdminService.listTenants();
        List<Map<String, Object>> data = tenants.stream()
                .map(DevAdminController::toTenantMap)
                .toList();
        return ResponseEntity.ok(Map.of("data", data));
    }

    @GetMapping("/tenants/{id}")
    public ResponseEntity<Map<String, Object>> getTenantDetail(@PathVariable UUID id) {
        DevAdminService.TenantDetail detail = devAdminService.getTenantDetail(id);
        Map<String, Object> tenantMap = toTenantMap(detail.tenant());
        tenantMap.put("staff", detail.staff().stream().map(DevAdminController::toStaffMap).toList());
        tenantMap.put("locationCount", detail.locationCount());
        return ResponseEntity.ok(Map.of("data", tenantMap));
    }

    @DeleteMapping("/tenants/{id}")
    public ResponseEntity<Map<String, String>> deleteTenant(@PathVariable UUID id) {
        devAdminService.deleteTenant(id);
        return ResponseEntity.ok(Map.of("message", "Tenant deleted"));
    }

    @DeleteMapping("/staff/{id}")
    public ResponseEntity<Map<String, String>> deleteStaff(@PathVariable UUID id) {
        devAdminService.deleteStaffMember(id);
        return ResponseEntity.ok(Map.of("message", "Staff deleted"));
    }

    private static Map<String, Object> toTenantMap(Tenant t) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", t.getId().toString());
        map.put("name", t.getName() != null ? t.getName() : "—");
        map.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
        map.put("isActive", t.getIsActive() != null ? t.getIsActive() : false);
        return map;
    }

    private static Map<String, Object> toStaffMap(DevAdminService.StaffSummary s) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", s.id().toString());
        map.put("firstName", s.firstName() != null ? s.firstName() : "");
        map.put("lastName", s.lastName() != null ? s.lastName() : "");
        map.put("email", s.email() != null ? s.email() : "");
        map.put("role", s.role() != null ? s.role() : "");
        map.put("hasAuthUser", s.authUserId() != null);
        return map;
    }
}
