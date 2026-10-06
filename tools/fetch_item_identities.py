"""Explicit development-only refresh of public Wiki item identities."""
import json
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

rows = []
for offset in range(0, 50000, 500):
    query = "bucket('infobox_item').select('page_name','item_name','item_id').orderBy('page_name','asc').limit(500).offset(%d).run()" % offset
    url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode({"action": "bucket", "format": "json", "query": query})
    for retry in range(3):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "LootLuck/0.1.0 public item identity audit"})
            with urllib.request.urlopen(request, timeout=30) as response:
                data = json.load(response)
            if not isinstance(data.get("bucket"), list):
                raise ValueError(str(data))
            break
        except Exception:
            if retry == 2:
                raise
            time.sleep(2 ** retry)
    page = data["bucket"]
    rows.extend(page)
    print(f"{len(rows)} item identities", flush=True)
    if len(page) < 500:
        break
    time.sleep(.3)
else:
    raise ValueError("Item identity bound exceeded")
target = Path("tools/wiki-inputs/item-identities.json")
temp = target.with_suffix(".tmp")
temp.write_text(json.dumps({"bucket": rows, "fetchedAt": datetime.now(timezone.utc).isoformat()}, ensure_ascii=False), encoding="utf-8")
temp.replace(target)
