"""Offline deterministic build of revision-backed runtime Wiki metadata."""
from collections import defaultdict
import json
from pathlib import Path
from audit_wiki import normalize

root = Path(__file__).resolve().parents[1]
resource = root / "src/main/resources/rates"
raw = json.loads((root / "tools/wiki-inputs/item-identities.json").read_text(encoding="utf-8"))
items = defaultdict(lambda: {"ids": set(), "names": set()})
for item in raw["bucket"]:
    ids = item.get("item_id", [])
    if not isinstance(ids, list):
        ids = [ids]
    for key in {normalize(item["page_name"]), normalize(item.get("item_name", ""))} - {""}:
        items[key]["ids"].update(int(value) for value in ids if str(value).isdigit() and int(value) > 0)
        if item.get("item_name"):
            items[key]["names"].add(item["item_name"])
index = {key: {"ids": sorted(value["ids"]), **({"name": next(iter(value["names"]))} if len(value["names"]) == 1 else {})}
         for key, value in sorted(items.items()) if value["ids"]}
(resource / "item-identities.json").write_text(json.dumps(index, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
evidence = json.loads((root / "tools/wiki-audit/evidence.json").read_text(encoding="utf-8"))
(resource / "page-evidence.json").write_text(json.dumps(evidence, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
print(f"Bundled {len(index)} item pages and {len(evidence)} source-page witnesses")
