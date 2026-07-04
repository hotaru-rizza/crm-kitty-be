#!/usr/bin/env python3
"""Second-pass fixes for test files after tenant filter refactoring."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_DIR = ROOT / "src/test/java"

PATTERNS = [
    (r"\.findByIdInAndDeletedAtIsNull\(\s*([^,]+),\s*[^)]+\)", r".findByIdInAndDeletedAtIsNull(\1)"),
    (r"\.findByCategoryKeyAndDeletedAtIsNull\(\s*[^,]+,\s*([^)]+)\)", r".findByCategoryKeyAndDeletedAtIsNull(\1)"),
    (r"\.findByDeletedAtIsNullOrderByIsDefaultDescLabelAsc\(\s*[^)]+\)", r".findByDeletedAtIsNullOrderByIsDefaultDescLabelAsc()"),
    (r"\.existsByPhoneAndDeletedAtIsNull\(\s*([^,]+),\s*[^)]+\)", r".existsByPhoneAndDeletedAtIsNull(\1)"),
    (r"\.existsByEmailAndDeletedAtIsNull\(\s*([^,]+),\s*[^)]+\)", r".existsByEmailAndDeletedAtIsNull(\1)"),
    (r"\.existsByEmailIgnoreCaseAndDeletedAtIsNull\(\s*([^,]+),\s*[^)]+\)", r".existsByEmailIgnoreCaseAndDeletedAtIsNull(\1)"),
    (r"\.findByEmailAndAcceptedAtIsNull\(\s*([^,]+),\s*[^)]+\)", r".findByEmailAndAcceptedAtIsNull(\1)"),
    (r"\.findByIdAndDeletedAtIsNull\(\s*([^,]+),\s*[^)]+\)", r".findByIdAndDeletedAtIsNull(\1)"),
    (r"\.findByRole\(\s*[^,]+,\s*([^)]+)\)", r".findByRole(\1)"),
    (r"\.findByRoleAndPermission\(\s*[^,]+,\s*([^,]+),\s*([^)]+)\)", r".findByRoleAndPermission(\1, \2)"),
    (r"\.deleteByRole\(\s*[^,]+,\s*([^)]+)\)", r".deleteByRole(\1)"),
    (r"\.findByArtistIdAndDateRange\(\s*[^,]+,\s*([^,]+),\s*([^,]+),\s*([^)]+)\)", r".findByArtistIdAndDateRange(\1, \2, \3)"),
    (r"\.findOverlappingLeaves\(\s*eq\([^)]+\),\s*eq\(([^)]+)\),\s*eq\(([^)]+)\),\s*eq\(([^)]+)\)\)", r".findOverlappingLeaves(eq(\1), eq(\2), eq(\3))"),
    (r"\.findOverlappingLeaves\(\s*any\(\),\s*any\(\),\s*any\(\),\s*any\(\)\)", r".findOverlappingLeaves(any(), any(), any())"),
    (r"requestRepository\.findById\(\s*([^,]+),\s*[^)]+\)", r"requestRepository.findById(\1)"),
    (r"\.findById\(\s*([^,]+),\s*bundle\.tenant\(\)\.getId\(\)\)", r".findById(\1)"),
    (r"\.findById\(\s*([^,]+),\s*tenantId\)", r".findById(\1)"),
    (r"when\([^)]+\.findById\(\s*([^,]+),\s*[^)]+\)\)", lambda m: re.sub(r"findById\(\s*([^,]+),\s*[^)]+\)", r"findById(\1)", m.group(0))),
]

def process_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    original = text
    for pattern, repl in PATTERNS:
        if callable(repl):
            text = re.sub(pattern, repl, text)
        else:
            text = re.sub(pattern, repl, text)
    if text != original:
        path.write_text(text, encoding="utf-8")
        return True
    return False

def main():
    changed = 0
    for path in TEST_DIR.rglob("*.java"):
        if process_file(path):
            changed += 1
            print(f"updated: {path.relative_to(ROOT)}")
    print(f"Done. {changed} files updated.")

if __name__ == "__main__":
    main()
