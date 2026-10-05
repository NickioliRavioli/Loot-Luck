"""Development-only primary Wiki export; never receives account data."""
import json
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

target = Path("tools/wiki-inputs/full-catalogue.json")
rows = []
for offset in range(0, 100000, 500):
    query = "bucket('dropsline').select('page_name','page_name_sub','item_name','drop_json','rare_drop_table').orderBy('page_name','asc').limit(500).offset(%d).run()" % offset
    url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode({"action": "bucket", "format": "json", "query": query})
    for retry in range(4):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "LootLuck/0.1.0 development catalogue generation; read-only Wiki export"})
            with urllib.request.urlopen(request, timeout=45) as response:
                body = response.read(4 * 1024 * 1024 + 1)
            if len(body) > 4 * 1024 * 1024:
                raise ValueError("Oversized response")
            page = json.loads(body)["bucket"]
            if not isinstance(page, list):
                raise ValueError("Invalid response")
            break
        except Exception:
            if retry == 3:
                raise
            time.sleep(5 * 2 ** retry)
    rows.extend(page)
    print(f"{len(rows)} rows", flush=True)
    if len(page) < 500:
        break
    time.sleep(1.1)
else:
    raise ValueError("Catalogue exceeds row bound; previous export retained")
target.parent.mkdir(parents=True, exist_ok=True)
temporary = target.with_suffix(".tmp")
temporary.write_text(json.dumps({"bucket": rows, "fetchedAt": datetime.now(timezone.utc).isoformat()}, ensure_ascii=False), encoding="utf-8")
temporary.replace(target)
print(f"Complete: {len(rows)} rows, {len({r['page_name'] for r in rows})} owners", flush=True)
