#!/usr/bin/env python3
"""Bulk-update repository method names and call sites after tenant filter refactoring."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DIRS = [ROOT / "src/main/java", ROOT / "src/test/java"]

# Order matters: longer/more specific patterns first.
REPLACEMENTS = [
    # Method renames (repository calls)
    ("findByTenantIdAndClientIdAndDeletedAtIsNullOrderByStartTimeDesc", "findByClientIdAndDeletedAtIsNullOrderByStartTimeDesc"),
    ("findByTenantIdAndLocationIdAndDeletedAtIsNull", "findByLocationIdAndDeletedAtIsNull"),
    ("findByTenantIdAndArtistIdAndDateRange", "findByArtistIdAndDateRange"),
    ("findByTenantIdAndLocationIdAndDateRange", "findByLocationIdAndDateRange"),
    ("findByTenantIdAndDateRange", "findByDateRange"),
    ("findByIdAndTenantIdAndDeletedAtIsNull", "findByIdAndDeletedAtIsNull"),
    ("findByEmailIgnoreCaseAndTenantIdAndDeletedAtIsNull", "findByEmailIgnoreCaseAndDeletedAtIsNull"),
    ("existsByEmailIgnoreCaseAndTenantIdAndDeletedAtIsNull", "existsByEmailIgnoreCaseAndDeletedAtIsNull"),
    ("existsByPhoneAndTenantIdAndDeletedAtIsNull", "existsByPhoneAndDeletedAtIsNull"),
    ("existsByEmailAndTenantIdAndDeletedAtIsNull", "existsByEmailAndDeletedAtIsNull"),
    ("findByEmailAndTenantIdAndDeletedAtIsNull", "findByEmailAndDeletedAtIsNull"),
    ("findByIdInAndTenantIdAndDeletedAtIsNull", "findByIdInAndDeletedAtIsNull"),
    ("findByTenantIdAndDeletedAtIsNullOrderByIsDefaultDescLabelAsc", "findByDeletedAtIsNullOrderByIsDefaultDescLabelAsc"),
    ("findByTenantIdAndCategoryKeyAndDeletedAtIsNull", "findByCategoryKeyAndDeletedAtIsNull"),
    ("findByTenantIdAndEntityIdOrderByCreatedAtDesc", "findByEntityIdOrderByCreatedAtDesc"),
    ("findByTenantIdAndTriggerTypeAndEnabledTrue", "findByTriggerTypeAndEnabledTrue"),
    ("findByTenantIdOrderByCategoryAscTriggerTypeAscBuiltinKeyAsc", "findAllByOrderByCategoryAscTriggerTypeAscBuiltinKeyAsc"),
    ("findByTenantIdAndBuiltinKey", "findByBuiltinKey"),
    ("existsByTenantIdAndBuiltinKey", "existsByBuiltinKey"),
    ("findByIdAndTenantId", "findById"),
    ("findByTenantIdAndStatusAndDeletedAtIsNull", "findByStatusAndDeletedAtIsNull"),
    ("findByTenantIdAndRoleAndDeletedAtIsNull", "findByRoleAndDeletedAtIsNull"),
    ("findByTenantIdAndIsActiveTrueAndDeletedAtIsNull", "findByIsActiveTrueAndDeletedAtIsNull"),
    ("findByTenantIdAndIsActiveAndDeletedAtIsNull", "findByIsActiveAndDeletedAtIsNull"),
    ("findByTenantIdAndDeletedAtIsNull", "findByDeletedAtIsNull"),
    ("findByTenantIdAndBirthDateAndDeletedAtIsNull", "findByBirthDateAndDeletedAtIsNull"),
    ("findByTenantIdAndTypeAndDeletedAtIsNull", "findByTypeAndDeletedAtIsNull"),
    ("findByTenantIdAndCategoryAndDeletedAtIsNull", "findByCategoryAndDeletedAtIsNull"),
    ("findByTenantIdAndStaffIdAndDeletedAtIsNull", "findByStaffIdAndDeletedAtIsNull"),
    ("findByTenantIdAndStatus", "findByStatus"),
    ("findByTenantIdAndSource", "findBySource"),
    ("findByTenantIdAndRoleAndPermission", "findByRoleAndPermission"),
    ("findByTenantIdAndRole", "findByRole"),
    ("findByEmailAndTenantIdAndAcceptedAtIsNull", "findByEmailAndAcceptedAtIsNull"),
    ("existsByEmailAndTenantIdAndAcceptedAtIsNull", "existsByEmailAndAcceptedAtIsNull"),
    ("findByClientIdAndTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc", "findByClientIdAndDeletedAtIsNullOrderByCreatedAtDesc"),
    ("countByTenantIdAndLocationIdAndStartTimeBetweenAndDeletedAtIsNull", "countByLocationIdAndStartTimeBetweenAndDeletedAtIsNull"),
    ("countByTenantIdAndIsActiveAndDeletedAtIsNull", "countByIsActiveAndDeletedAtIsNull"),
    ("countByTenantIdAndStatusAndDeletedAtIsNull", "countByStatusAndDeletedAtIsNull"),
    ("countByTenantIdAndStatusAndCreatedAtAfter", "countByStatusAndCreatedAtAfter"),
    ("countByTenantIdAndDateBetweenAndDeletedAtIsNull", "countByDateBetweenAndDeletedAtIsNull"),
    ("countByTenantIdAndStatus", "countByStatus"),
    ("countByTenantIdAndDeletedAtIsNull", "countByDeletedAtIsNull"),
    ("existsByTenantIdAndDeletedAtIsNull", "existsByDeletedAtIsNull"),
    ("existsByTenantIdAndActorIdAndActionAndDetails", "existsByActorIdAndActionAndDetails"),
    ("deleteByTenantIdAndRole", "deleteByRole"),
    ("findArtistsByTenantId", "findArtists"),
    ("findAllByTenantAndLocationAndDateRange", "findAllByLocationAndDateRange"),
    ("findAllByTenantAndLocation", "findAllByLocation"),
    ("findAllByTenantAndDateRange", "findAllByDateRange"),
    ("findAllByTenant", "findAllNotDeleted"),
    ("findByTenantId", "findAll"),  # RequestRepository only; SubscriptionRepository kept manually
    ("findByTenantId", "findAll"),  # duplicate ok
    # Specification
    (".where(AppointmentSpecifications.belongsToTenant(tenantId))", ""),
    (".where(ClientSpecifications.belongsToTenant(tenantId))", ""),
    (".where(ProjectSpecifications.belongsToTenant(tenantId))", ""),
    (".where(RequestSpecifications.belongsToTenant(tenantId))", ""),
    (".and(AppointmentSpecifications.belongsToTenant(tenantId))", ""),
    (".and(ClientSpecifications.belongsToTenant(tenantId))", ""),
    (".and(ProjectSpecifications.belongsToTenant(tenantId))", ""),
    (".and(RequestSpecifications.belongsToTenant(tenantId))", ""),
]

# Remove tenantId as first argument from specific multi-arg calls.
CALL_ARG_PATTERNS = [
    (r"\.findByDateRange\(\s*tenantId\s*,", ".findByDateRange("),
    (r"\.findByArtistIdAndDateRange\(\s*tenantId\s*,", ".findByArtistIdAndDateRange("),
    (r"\.findByLocationIdAndDateRange\(\s*tenantId\s*,", ".findByLocationIdAndDateRange("),
    (r"\.findInactiveClients\(\s*tenantId\s*,", ".findInactiveClients("),
    (r"\.markDormantClients\(\s*tenantId\s*,", ".markDormantClients("),
    (r"\.reactivateDormantClients\(\s*tenantId\s*,", ".reactivateDormantClients("),
    (r"\.findByBirthDateAndDeletedAtIsNull\(\s*tenantId\s*,", ".findByBirthDateAndDeletedAtIsNull("),
    (r"\.countPending\(\s*tenantId\s*\)", ".countPending()"),
    (r"\.findApprovedInRange\(\s*tenantId\s*,", ".findApprovedInRange("),
    (r"\.findAllNotDeleted\(\s*tenantId\s*,", ".findAllNotDeleted("),
    (r"\.findAllByDateRange\(\s*tenantId\s*,", ".findAllByDateRange("),
    (r"\.findAllByLocation\(\s*tenantId\s*,", ".findAllByLocation("),
    (r"\.findAllByLocationAndDateRange\(\s*tenantId\s*,", ".findAllByLocationAndDateRange("),
    (r"\.findByStatusAndDateRange\(\s*tenantId\s*,", ".findByStatusAndDateRange("),
    (r"\.findByStatusAndLocation\(\s*tenantId\s*,", ".findByStatusAndLocation("),
    (r"\.findByStatusAndLocationAndDateRange\(\s*tenantId\s*,", ".findByStatusAndLocationAndDateRange("),
    (r"\.countPendingByLocation\(\s*tenantId\s*,", ".countPendingByLocation("),
    (r"\.findByStaffId\(\s*tenantId\s*,", ".findByStaffId("),
    (r"\.findByStaffIdAndStatus\(\s*tenantId\s*,", ".findByStaffIdAndStatus("),
    (r"\.findActiveLeaveForDate\(\s*tenantId\s*,", ".findActiveLeaveForDate("),
    (r"\.findOverlappingLeaves\(\s*tenantId\s*,", ".findOverlappingLeaves("),
    (r"\.findByStaffIdAndDateRange\(\s*tenantId\s*,", ".findByStaffIdAndDateRange("),
    (r"\.findByStatus\(\s*tenantId\s*,", ".findByStatus("),
    (r"\.searchClients\(\s*tenantId\s*,", ".searchClients("),
    (r"\.findLostClients\(\s*tenantId\s*,", ".findLostClients("),
    (r"\.findByIdWithCollections\(\s*([^,]+),\s*tenantId\s*\)", r".findByIdWithCollections(\1)"),
    (r"\.findWithFilters\(\s*tenantId\s*,", ".findWithFilters("),
    (r"\.findRecentClientIds\(\s*tenantId\s*,", ".findRecentClientIds("),
    (r"\.sumByTypeAndDateRange\(\s*tenantId\s*,", ".sumByTypeAndDateRange("),
    (r"\.sumByTypeAndDateRangeForStaffs\(\s*tenantId\s*,", ".sumByTypeAndDateRangeForStaffs("),
    (r"\.countByTypeAndDateRange\(\s*tenantId\s*,", ".countByTypeAndDateRange("),
    (r"\.countByTypeAndDateRangeForStaffs\(\s*tenantId\s*,", ".countByTypeAndDateRangeForStaffs("),
    (r"\.sumByCategoryAndDateRange\(\s*tenantId\s*,", ".sumByCategoryAndDateRange("),
    (r"\.sumByCategoryAndDateRangeForStaffs\(\s*tenantId\s*,", ".sumByCategoryAndDateRangeForStaffs("),
    (r"\.sumByPaymentMethodAndDateRange\(\s*tenantId\s*,", ".sumByPaymentMethodAndDateRange("),
    (r"\.sumByPaymentMethodAndDateRangeForStaffs\(\s*tenantId\s*,", ".sumByPaymentMethodAndDateRangeForStaffs("),
    (r"\.sumByArtistAndDateRange\(\s*tenantId\s*,", ".sumByArtistAndDateRange("),
    (r"\.sumByArtistAndDateRangeForStaffs\(\s*tenantId\s*,", ".sumByArtistAndDateRangeForStaffs("),
    (r"\.sumRevenueByLocationAndDateRange\(\s*tenantId\s*,", ".sumRevenueByLocationAndDateRange("),
    (r"\.findFiltered\(\s*tenantId\s*,", ".findFiltered("),
    (r"\.findFilteredWithSearch\(\s*tenantId\s*,", ".findFilteredWithSearch("),
    (r"TransactionSpecifications\.filtered\(\s*tenantId\s*,", "TransactionSpecifications.filtered("),
    (r"\.findByDeletedAtIsNull\(\s*tenantId\s*,", ".findByDeletedAtIsNull("),
    (r"\.findByDeletedAtIsNull\(\s*tenantId\s*\)", ".findByDeletedAtIsNull()"),
    (r"\.findByIdAndDeletedAtIsNull\(\s*([^,]+),\s*tenantId\s*\)", r".findByIdAndDeletedAtIsNull(\1)"),
    (r"\.findById\(\s*([^,]+),\s*tenantId\s*\)", r".findById(\1)"),
    (r"\.findByEmailAndDeletedAtIsNull\(\s*([^,]+),\s*tenantId\s*\)", r".findByEmailAndDeletedAtIsNull(\1)"),
    (r"\.existsByEmailAndDeletedAtIsNull\(\s*([^,]+),\s*tenantId\s*\)", r".existsByEmailAndDeletedAtIsNull(\1)"),
    (r"\.existsByPhoneAndDeletedAtIsNull\(\s*([^,]+),\s*tenantId\s*\)", r".existsByPhoneAndDeletedAtIsNull(\1)"),
    (r"\.existsByEmailIgnoreCaseAndDeletedAtIsNull\(\s*([^,]+),\s*tenantId\s*\)", r".existsByEmailIgnoreCaseAndDeletedAtIsNull(\1)"),
    (r"\.findByEmailIgnoreCaseAndDeletedAtIsNull\(\s*([^,]+),\s*tenantId\s*\)", r".findByEmailIgnoreCaseAndDeletedAtIsNull(\1)"),
    (r"\.findByIdInAndDeletedAtIsNull\(\s*([^,]+),\s*tenantId\s*\)", r".findByIdInAndDeletedAtIsNull(\1)"),
    (r"\.findByClientIdAndDeletedAtIsNullOrderByCreatedAtDesc\(\s*([^,]+),\s*tenantId\s*,", r".findByClientIdAndDeletedAtIsNullOrderByCreatedAtDesc(\1,"),
    (r"\.findByDeletedAtIsNullOrderByIsDefaultDescLabelAsc\(\s*tenantId\s*\)", ".findByDeletedAtIsNullOrderByIsDefaultDescLabelAsc()"),
    (r"\.findByCategoryKeyAndDeletedAtIsNull\(\s*tenantId\s*,", ".findByCategoryKeyAndDeletedAtIsNull("),
    (r"\.existsByDeletedAtIsNull\(\s*tenantId\s*\)", ".existsByDeletedAtIsNull()"),
    (r"\.findByBuiltinKey\(\s*tenantId\s*,", ".findByBuiltinKey("),
    (r"\.existsByBuiltinKey\(\s*tenantId\s*,", ".existsByBuiltinKey("),
    (r"\.findByEntityIdOrderByCreatedAtDesc\(\s*tenantId\s*,", ".findByEntityIdOrderByCreatedAtDesc("),
    (r"\.findByTriggerTypeAndEnabledTrue\(\s*tenantId\s*,", ".findByTriggerTypeAndEnabledTrue("),
    (r"\.findAllByOrderByCategoryAscTriggerTypeAscBuiltinKeyAsc\(\s*tenantId\s*\)", ".findAllByOrderByCategoryAscTriggerTypeAscBuiltinKeyAsc()"),
    (r"\.findByRoleAndPermission\(\s*tenantId\s*,", ".findByRoleAndPermission("),
    (r"\.findByRole\(\s*tenantId\s*,", ".findByRole("),
    (r"\.deleteByRole\(\s*tenantId\s*,", ".deleteByRole("),
    (r"\.findByEmailAndAcceptedAtIsNull\(\s*([^,]+),\s*tenantId\s*\)", r".findByEmailAndAcceptedAtIsNull(\1)"),
    (r"\.existsByEmailAndAcceptedAtIsNull\(\s*([^,]+),\s*tenantId\s*\)", r".existsByEmailAndAcceptedAtIsNull(\1)"),
    (r"\.existsByActorIdAndActionAndDetails\(\s*tenantId\s*,", ".existsByActorIdAndActionAndDetails("),
    (r"\.countByDeletedAtIsNull\(\s*tenantId\s*\)", ".countByDeletedAtIsNull()"),
    (r"\.countByIsActiveAndDeletedAtIsNull\(\s*tenantId\s*,", ".countByIsActiveAndDeletedAtIsNull("),
    (r"\.countByStatusAndDeletedAtIsNull\(\s*tenantId\s*,", ".countByStatusAndDeletedAtIsNull("),
    (r"\.findByIsActiveAndDeletedAtIsNull\(\s*tenantId\s*,", ".findByIsActiveAndDeletedAtIsNull("),
    (r"\.findByClientIdAndDeletedAtIsNullOrderByStartTimeDesc\(\s*tenantId\s*,", ".findByClientIdAndDeletedAtIsNullOrderByStartTimeDesc("),
    (r"\.findByLocationIdAndDeletedAtIsNull\(\s*tenantId\s*,", ".findByLocationIdAndDeletedAtIsNull("),
    (r"\.countByLocationIdAndStartTimeBetweenAndDeletedAtIsNull\(\s*tenantId\s*,", ".countByLocationIdAndStartTimeBetweenAndDeletedAtIsNull("),
    (r"when\(appointmentRepository\.findByDateRange\(eq\(tenantId\)", "when(appointmentRepository.findByDateRange("),
    (r"when\(appointmentRepository\.findByArtistIdAndDateRange\(TENANT_ID,", "when(appointmentRepository.findByArtistIdAndDateRange("),
]

SKIP_SUBSCRIPTION_FIND_BY_TENANT = True


def process_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    original = text

    for old, new in REPLACEMENTS:
        if old == "findByTenantId" and "SubscriptionRepository" in text:
            continue
        text = text.replace(old, new)

    for pattern, repl in CALL_ARG_PATTERNS:
        text = re.sub(pattern, repl, text)

    # Fix empty .where() chains left after belongsToTenant removal
    text = re.sub(r"Specification\.where\(\s*\)\s*\.", "Specification.where(", text)
    text = re.sub(r"\.where\(\s*\)\s*\.", ".where(", text)

    # rolePermissionRepository.findAll() was wrongly renamed from findByTenantId - fix to count()>0 check sites
    # findAll(tenantId) -> count() > 0 or !isEmpty for existence checks handled in service manually

    if text != original:
        path.write_text(text, encoding="utf-8")
        return True
    return False


def main():
    changed = 0
    for base in DIRS:
        for path in base.rglob("*.java"):
            if "SubscriptionRepository.java" in str(path):
                continue
            if process_file(path):
                changed += 1
                print(f"updated: {path.relative_to(ROOT)}")
    print(f"Done. {changed} files updated.")


if __name__ == "__main__":
    main()
