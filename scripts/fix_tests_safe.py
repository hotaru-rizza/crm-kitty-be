#!/usr/bin/env python3
"""Safe tenant filter test updates."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_DIR = ROOT / "src/test/java"

RENAMES = [
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
    ("findByEmailAndTenantIdAndAcceptedAtIsNull", "findByEmailAndAcceptedAtIsNull"),
    ("existsByEmailAndTenantIdAndAcceptedAtIsNull", "existsByEmailAndAcceptedAtIsNull"),
    ("findByClientIdAndTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc", "findByClientIdAndDeletedAtIsNullOrderByCreatedAtDesc"),
    ("countByTenantIdAndLocationIdAndStartTimeBetweenAndDeletedAtIsNull", "countByLocationIdAndStartTimeBetweenAndDeletedAtIsNull"),
    ("countByTenantIdAndIsActiveAndDeletedAtIsNull", "countByIsActiveAndDeletedAtIsNull"),
    ("countByTenantIdAndStatusAndDeletedAtIsNull", "countByStatusAndDeletedAtIsNull"),
    ("countByTenantIdAndDeletedAtIsNull", "countByDeletedAtIsNull"),
    ("existsByTenantIdAndDeletedAtIsNull", "existsByDeletedAtIsNull"),
    ("existsByTenantIdAndActorIdAndActionAndDetails", "existsByActorIdAndActionAndDetails"),
    ("deleteByTenantIdAndRole", "deleteByRole"),
    ("findArtistsByTenantId", "findArtists"),
    ("findByTenantIdAndRoleAndPermission", "findByRoleAndPermission"),
    ("findByTenantIdAndRole", "findByRole"),
    ("findAllByTenantAndLocationAndDateRange", "findAllByLocationAndDateRange"),
    ("findAllByTenantAndLocation", "findAllByLocation"),
    ("findAllByTenantAndDateRange", "findAllByDateRange"),
    ("findAllByTenant", "findAllNotDeleted"),
    ("findByPhoneAndTenantIdAndDeletedAtIsNull", "findByPhoneAndDeletedAtIsNull"),
    ("findByIdAndTenantId", "findById"),
]

# Remove tenantId argument from repository calls - line by line safe patterns
ARG_FIXES = [
    (r"\.findByIdAndDeletedAtIsNull\(\s*([^,\s)]+)\s*,\s*[^)]+\)", r".findByIdAndDeletedAtIsNull(\1)"),
    (r"\.findById\(\s*([^,\s)]+)\s*,\s*[^)]+\)", r".findById(\1)"),
    (r"\.findByIdInAndDeletedAtIsNull\(\s*([^,\s)]+)\s*,\s*[^)]+\)", r".findByIdInAndDeletedAtIsNull(\1)"),
    (r"\.findByEmailIgnoreCaseAndDeletedAtIsNull\(\s*([^,\s)]+)\s*,\s*[^)]+\)", r".findByEmailIgnoreCaseAndDeletedAtIsNull(\1)"),
    (r"\.existsByEmailIgnoreCaseAndDeletedAtIsNull\(\s*([^,\s)]+)\s*,\s*[^)]+\)", r".existsByEmailIgnoreCaseAndDeletedAtIsNull(\1)"),
    (r"\.existsByPhoneAndDeletedAtIsNull\(\s*([^,\s)]+)\s*,\s*[^)]+\)", r".existsByPhoneAndDeletedAtIsNull(\1)"),
    (r"\.existsByEmailAndDeletedAtIsNull\(\s*([^,\s)]+)\s*,\s*[^)]+\)", r".existsByEmailAndDeletedAtIsNull(\1)"),
    (r"\.findByEmailAndAcceptedAtIsNull\(\s*([^,\s)]+)\s*,\s*[^)]+\)", r".findByEmailAndAcceptedAtIsNull(\1)"),
    (r"\.findByPhoneAndDeletedAtIsNull\(\s*([^,\s)]+)\s*,\s*[^)]+\)", r".findByPhoneAndDeletedAtIsNull(\1)"),
    (r"\.findByCategoryKeyAndDeletedAtIsNull\(\s*[^,\s)]+\s*,\s*([^)]+)\)", r".findByCategoryKeyAndDeletedAtIsNull(\1)"),
    (r"\.findByDeletedAtIsNullOrderByIsDefaultDescLabelAsc\(\s*[^)]+\)", r".findByDeletedAtIsNullOrderByIsDefaultDescLabelAsc()"),
    (r"\.findByRole\(\s*[^,\s)]+\s*,\s*([^)]+)\)", r".findByRole(\1)"),
    (r"\.findByRoleAndPermission\(\s*[^,\s)]+\s*,\s*([^,]+),\s*([^)]+)\)", r".findByRoleAndPermission(\1, \2)"),
    (r"\.deleteByRole\(\s*[^,\s)]+\s*,\s*([^)]+)\)", r".deleteByRole(\1)"),
    (r"\.findByDateRange\(\s*[^,\s)]+\s*,\s*", r".findByDateRange("),
    (r"\.findByArtistIdAndDateRange\(\s*[^,\s)]+\s*,\s*", r".findByArtistIdAndDateRange("),
    (r"\.findByDateRange\(\s*,\s*", r".findByDateRange("),
    (r"\.findByTriggerTypeAndEnabledTrue\(\s*[^,\s)]+\s*,\s*", r".findByTriggerTypeAndEnabledTrue("),
    (r"\.findAllByOrderByCategoryAscTriggerTypeAscBuiltinKeyAsc\(\s*[^)]+\)", r".findAllByOrderByCategoryAscTriggerTypeAscBuiltinKeyAsc()"),
    (r"\.findByIdAndDeletedAtIsNull\(\s*serviceId\s*,\s*currentTenantId\s*\)", r".findByIdAndDeletedAtIsNull(serviceId)"),
    (r"\.countByDeletedAtIsNull\(\s*[^)]+\)", r".countByDeletedAtIsNull()"),
    (r"\.countByIsActiveAndDeletedAtIsNull\(\s*[^,\s)]+\s*,\s*", r".countByIsActiveAndDeletedAtIsNull("),
    (r"\.existsByActorIdAndActionAndDetails\(\s*[^,\s)]+\s*,\s*", r".existsByActorIdAndActionAndDetails("),
    (r"\.findOverlappingLeaves\(\s*eq\([^)]+\)\s*,\s*", r".findOverlappingLeaves("),
    (r"\.findOverlappingLeaves\(\s*[^,\s)]+\s*,\s*", r".findOverlappingLeaves("),
    (r"\.findActiveLeaveForDate\(\s*[^,\s)]+\s*,\s*", r".findActiveLeaveForDate("),
    (r"\.findApprovedInRange\(\s*[^,\s)]+\s*,\s*", r".findApprovedInRange("),
    (r"\.sumByTypeAndDateRange\(\s*[^,\s)]+\s*,\s*", r".sumByTypeAndDateRange("),
    (r"\.sumByCategoryAndDateRange\(\s*[^,\s)]+\s*,\s*", r".sumByCategoryAndDateRange("),
    (r"when\(rolePermissionRepository\.findByTenantId\([^)]+\)\)", r"when(rolePermissionRepository.count()).thenReturn(1L)"),
    (r"when\(rolePermissionRepository\.findAll\([^)]+\)\)", r"when(rolePermissionRepository.count()).thenReturn(1L)"),
    (r"TransactionSpecifications\.filtered\(\s*[^,\s)]+\s*,\s*", r"TransactionSpecifications.filtered("),
    (r"\.where\(AppointmentSpecifications\.belongsToTenant\([^)]+\)\)", ""),
    (r"\.where\(ClientSpecifications\.belongsToTenant\([^)]+\)\)", ""),
    (r"\.where\(ProjectSpecifications\.belongsToTenant\([^)]+\)\)", ""),
    (r"\.where\(RequestSpecifications\.belongsToTenant\([^)]+\)\)", ""),
]

def process(path: Path) -> bool:
    if "Subscription" in path.name and "SubscriptionServiceTest" in path.name:
        pass  # keep findByTenantId
    text = path.read_text()
    orig = text
    for old, new in RENAMES:
        if old == "findByIdAndTenantId" and "RequestRepository" not in text and "requestRepository" not in text:
            # only rename to findById for request repo usages; others use findByIdAndDeletedAtIsNull via earlier rename
            continue
        text = text.replace(old, new)
    for pat, repl in ARG_FIXES:
        text = re.sub(pat, repl, text)
    if text != orig:
        path.write_text(text)
        return True
    return False

def main():
    count = 0
    for path in TEST_DIR.rglob("*.java"):
        if process(path):
            count += 1
            print(path.relative_to(ROOT))
    print(f"updated {count} files")

if __name__ == "__main__":
    main()
