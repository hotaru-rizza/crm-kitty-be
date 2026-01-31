python3 - <<'PY'
import re, shutil
from pathlib import Path

lst = Path.home()/"Desktop/cmr-kitty-be"
out = Path.home()/"Desktop/crm-kitty-be-restored/src/main/java"
pkg_re = re.compile(r'^\s*package\s+([a-zA-Z0-9_.]+)\s*;', re.MULTILINE)

files = [Path(line.strip()) for line in lst.read_text().splitlines() if line.strip()]
print("files:", len(files))

for f in files:
    txt = f.read_text(encoding="utf-8", errors="ignore")
    m = pkg_re.search(txt)
    if not m:
        continue
    pkg = m.group(1)
    dst_dir = out / Path(*pkg.split("."))
    dst_dir.mkdir(parents=True, exist_ok=True)
    # имя файла берём из реального имени типа если получится, иначе оставим как есть
    # но обычно это не важно, главное — содержимое + путь
    # попробуем вытащить public class/interface/enum/record
    m2 = re.search(r'^\s*public\s+(?:final\s+|sealed\s+|non-sealed\s+)?(?:class|interface|enum|record)\s+([A-Za-z_][A-Za-z0-9_]*)\b', txt, re.MULTILINE)
    name = (m2.group(1) + ".java") if m2 else f.name
    shutil.copyfile(f, dst_dir/name)

print("done ->", out)
PY
