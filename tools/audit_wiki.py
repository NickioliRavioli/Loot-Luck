"""Read-only Wiki research tool; offline inventory/audit or explicit --refresh.

Independent expectations come from page wikitext, never from the Java parser.
Unsupported Lua/transcluded expressions are reported, not interpreted as exact.
"""
import argparse
import ast
from collections import defaultdict, Counter
from fractions import Fraction
import hashlib
import html
import json
from pathlib import Path
import re
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from html.parser import HTMLParser

ROOT = Path(__file__).resolve().parents[1]
PAGES = ROOT / "tools/wiki-pages"
OUT = ROOT / "tools/wiki-audit"


class DropHTML(HTMLParser):
    """Independent rendered-table reader, including Lua-generated rows."""
    def __init__(self, owner):
        super().__init__()
        self.owner, self.rows = owner, []
        self.cells, self.cell, self.source, self.rate = [], None, "", ""
        self.table_depth = 0

    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        if tag == "table":
            if self.table_depth or "item-drops" in attrs.get("class", "").split():
                self.table_depth += 1
        if not self.table_depth:
            return
        if tag == "tr":
            self.cells, self.source, self.rate = [], "", ""
        if tag == "td":
            self.cell = {"text": "", "item": "item-col" in attrs.get("class", "").split(), "title": ""}
        if tag == "img":
            match = re.search(r": (.*?) drops .*? with rarity ", attrs.get("alt", ""))
            if match:
                self.source = match.group(1)
        if tag == "span" and "data-drop-fraction" in attrs and not self.rate:
            self.rate = attrs["data-drop-fraction"]
        if tag == "a" and self.cell and self.cell["item"] and not self.cell["title"] and attrs.get("href", "").startswith("/w/"):
            self.cell["title"] = attrs.get("title", "")

    def handle_data(self, data):
        if self.cell is not None:
            self.cell["text"] += data

    def handle_endtag(self, tag):
        if tag == "td" and self.cell is not None:
            self.cells.append(self.cell)
            self.cell = None
        if tag == "tr" and self.table_depth:
            index = next((i for i, cell in enumerate(self.cells) if cell["item"]), None)
            if index is not None and index + 2 < len(self.cells) and self.source.split("#")[0] == self.owner:
                item, quantity, rate = self.cells[index:index + 3]
                self.rows.append({"table": self.source, "item": item["title"] or item["text"].strip(),
                                  "rate": self.rate or rate["text"].strip(), "quantity": quantity["text"].strip(),
                                  "notes": "", "line": 0})
        if tag == "table" and self.table_depth:
            self.table_depth -= 1


def normalize(value):
    value = re.sub(r"\[\[([^]|]+)(?:\|[^]]*)?\]\]", r"\1", value)
    value = re.sub(r"<[^>]*>", "", html.unescape(value))
    return " ".join(value.replace("’", "'").replace("_", " ").split()).casefold()


def quantity(value):
    # A few transcluded Wiki tables contain UTF-8 text decoded as Windows-1252.
    value = value.replace("\u00e2\u20ac\u201c", "\u2013").replace("\u00e2\u20ac\u201d", "\u2014").replace("\u00c2\u00a0", "\u00a0")
    value = re.sub(r"\[[^]]*\]", "", normalize(value))
    value = re.sub(r"(?<=\d),(?=\d{3}(?:\D|$))", "", value)
    return re.sub(r"\s*[;,]\s*", ";", value).replace("-", "–").replace("—", "–").strip()


def same_rate(left, right):
    # Roll count and approximation are separate structured export fields.
    left = re.sub(r"^\d+\s*[×x]\s*", "", left).lstrip("~ ")
    right = re.sub(r"^\d+\s*[×x]\s*", "", right).lstrip("~ ")
    a, b = probability(left), probability(right)
    if a is not None or b is not None:
        return a == b
    return normalize(re.sub(r"\[[^]]*\]", "", left)) == normalize(re.sub(r"\[[^]]*\]", "", right))


def probability(value):
    value = re.sub(r"\[[^]]*\]", "", value).strip().replace(",", "")
    value = re.sub(r"^\d+\s*[×x]\s*", "", value)
    value = re.sub(r"\{\{#expr:([^{}]+)\}\}", lambda m: str(arithmetic(m.group(1))), value)
    if value.casefold() in ("always", "guaranteed"):
        return Fraction(1)
    if value.casefold() in ("never", "0"):
        return Fraction(0)
    try:
        parts = value.split("/")
        p = Fraction(parts[0]) / Fraction(parts[1]) if len(parts) == 2 else Fraction(value.rstrip("%")) / (100 if value.endswith("%") else 1)
        return p if 0 <= p <= 1 else None
    except (ValueError, ZeroDivisionError):
        return None


def arithmetic(expression):
    """Exact, allowlisted arithmetic only. Never execute Wiki text as code."""
    def visit(node):
        if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
            return Fraction(str(node.value))
        if isinstance(node, ast.BinOp):
            left, right = visit(node.left), visit(node.right)
            if isinstance(node.op, ast.Add):
                return left + right
            if isinstance(node.op, ast.Sub):
                return left - right
            if isinstance(node.op, ast.Mult):
                return left * right
            if isinstance(node.op, ast.Div):
                return left / right
        raise ValueError("Unsupported Wiki arithmetic")
    try:
        result = visit(ast.parse(expression.strip(), mode="eval").body)
        return str(result.numerator) if result.denominator == 1 else str(float(result))
    except (ValueError, SyntaxError, ZeroDivisionError):
        return "unresolved"


def templates(text):
    """Balanced templates, including nested templates, in source order."""
    stack = []
    found = []
    for match in re.finditer(r"\{\{|\}\}", text):
        if match.group() == "{{":
            stack.append(match.start())
        elif stack:
            start = stack.pop()
            found.append((start, text[start + 2:match.start()]))
    return sorted(found)


def fields(body):
    # Split only top-level pipes; links and child templates can contain pipes.
    parts, last, depth = [], 0, 0
    for match in re.finditer(r"\{\{|\}\}|\[\[|\]\]|\|", body):
        token = match.group()
        if token in ("{{", "[["):
            depth += 1
        elif token in ("}}", "]]"):
            depth -= 1
        elif depth == 0:
            parts.append(body[last:match.start()].strip())
            last = match.end()
    parts.append(body[last:].strip())
    result = {}
    for part in parts[1:]:
        if "=" in part:
            key, value = part.split("=", 1)
            result[key.strip().lower()] = value.strip()
    return parts[0].strip().lower(), result


def page_evidence(owner, data):
    text = data["parse"]["wikitext"]["*"]
    rows, levels, table_ids, ids, names = [], defaultdict(set), defaultdict(set), {}, {}
    table = owner
    headings = list(re.finditer(r"(?m)^==+([^=\n]+)==+\s*$", text))
    for start, body in templates(text):
        name, params = fields(body)
        if name == "dropstablehead":
            table = owner + ("#" + params["dropversion"] if params.get("dropversion") else "")
            heading = next((m.group(1).strip() for m in reversed(headings) if m.start() < start and "drop" in m.group(1).lower()), "")
            # Only an explicit level heading provides evidence, not arbitrary digits.
            if re.fullmatch(r"Levels? [\d, /&and]+ drops", heading, re.I):
                levels[table].update(map(int, re.findall(r"\d+", heading)))
        elif name == "locline" and params.get("dropversion"):
            value = params.get("levels", "")
            if re.fullmatch(r"[\d, ]+", value):
                for variant in params["dropversion"].split(","):
                    levels[owner + "#" + variant.strip()].update(map(int, re.findall(r"\d+", value)))
        elif name == "infobox monster":
            for key, value in params.items():
                if re.fullmatch(r"id\d*", key) and re.fullmatch(r"[\d, ]+", value):
                    suffix = key[2:]
                    combat = params.get("combat" + suffix, params.get("combat", ""))
                    if combat.isdigit():
                        for npc_id in re.findall(r"\d+", value):
                            ids[npc_id] = int(combat)
                            variant = params.get("dropversion" + suffix, params.get("dropversion", ""))
                            if variant and "," not in variant:
                                table_ids[owner + "#" + variant].add(int(npc_id))
        elif name == "infobox item":
            for key, value in params.items():
                if re.fullmatch(r"id\d*", key) and re.fullmatch(r"[\d, ]+", value):
                    suffix = key[2:]
                    item_name = params.get("name" + suffix, params.get("name", ""))
                    if item_name and "{{" not in item_name:
                        for item_id in re.findall(r"\d+", value):
                            names[item_id] = item_name
        elif name.startswith("dropsline"):
            item = params.get("bucketname", params.get("name", ""))
            if name == "dropslineclue":
                item = "Clue scroll (" + params.get("type", "") + ")"
            if item:
                row_table = owner + "#" + params["dropversion"] if params.get("dropversion") else table
                rows.append({"table": row_table, "item": item, "rate": params.get("rarity", ""),
                             "quantity": params.get("quantity", "1"),
                             "notes": " ".join(params.get(k, "") for k in ("namenotes", "raritynotes")),
                             "line": text.count("\n", 0, start) + 1})
    for row in rows:
        if re.search(r"only dropped by those found in the \[\[Wilderness\]\]", row["notes"], re.I):
            row["requiredArea"] = "Wilderness"
    return {"owner": owner, "revision": data["parse"]["revid"], "fetchedAt": data["fetchedAt"],
            "url": "https://oldschool.runescape.wiki/w/" + urllib.parse.quote(owner.replace(" ", "_")),
            "sha256": hashlib.sha256(text.encode()).hexdigest(), "rows": rows,
            "tableLevels": {k: sorted(v) for k, v in levels.items()},
            "tableNpcIds": {k: sorted(v) for k, v in table_ids.items()}, "npcLevels": ids, "itemNames": names}


def fetch(owner, refresh, render=False):
    path = PAGES / (hashlib.sha256(owner.encode()).hexdigest() + ".json")
    if path.exists():
        cached = json.loads(path.read_text(encoding="utf-8"))
        if not refresh and (not render or "dropHtml" in cached):
            return cached
    if not refresh:
        return None
    url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode({
        "action": "parse", "page": owner, "prop": "wikitext|revid" + ("|text" if render else ""), "format": "json", "redirects": 1})
    for retry in range(3):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "LootLuck/0.1.0 read-only source-page audit"})
            with urllib.request.urlopen(request, timeout=30) as response:
                body = response.read(4 * 1024 * 1024 + 1)
            if len(body) > 4 * 1024 * 1024:
                raise ValueError("Oversized Wiki page")
            data = json.loads(body)
            if "parse" not in data or not data["parse"].get("revid"):
                raise ValueError("No revision-backed source page: " + str(data.get("error")))
            data["fetchedAt"] = datetime.now(timezone.utc).isoformat()
            if render:
                markup = data["parse"].pop("text")["*"]
                data["dropHtml"] = "\n".join(re.findall(r'<table\b[^>]*\bitem-drops\b.*?</table>', markup, re.S))
                data["renderSha256"] = hashlib.sha256(markup.encode()).hexdigest()
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
            time.sleep(.25)
            return data
        except Exception:
            if retry == 2:
                raise
            time.sleep(2 ** retry)


def features(rows):
    result = set()
    if len({r["page_name_sub"] for r in rows}) > 1:
        result.add("multiple-tables")
    for row in rows:
        d = json.loads(row["drop_json"])
        result.add("type:" + str(d.get("Drop type", "unknown")))
        if str(d.get("Rolls", "1")) != "1":
            result.add("multiple-rolls")
        for key in ("Rarity Notes", "Alt Rarity", "Name Notes"):
            if d.get(key):
                result.add(key)
        if probability(str(d.get("Rarity", ""))) is None:
            result.add("non-numeric-rate")
        if "noted" in str(d.get("Drop Quantity", "")):
            result.add("noted")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh", action="store_true", help="Fetch source pages explicitly; otherwise offline")
    parser.add_argument("--sample", type=int, default=150)
    parser.add_argument("--all-pages", action="store_true")
    parser.add_argument("--render", action="store_true", help="Also audit rendered tables, including transcluded rates")
    parser.add_argument("--transclusions", action="store_true", help="Fetch source-owned subpages referenced by the selected pages")
    parser.add_argument("--owners", nargs="*", default=[])
    args = parser.parse_args()
    full = json.loads((ROOT / "tools/wiki-inputs/full-catalogue.json").read_text(encoding="utf-8"))
    groups = defaultdict(list)
    for row in full["bucket"]:
        groups[row["page_name"]].append(row)
    inventory = [{"owner": owner, "rows": len(rows), "tables": sorted({r["page_name_sub"] for r in rows}),
                  "features": sorted(features(rows)), "url": "https://oldschool.runescape.wiki/w/" + urllib.parse.quote(owner.replace(" ", "_"))}
                 for owner, rows in sorted(groups.items())]
    identity_path = ROOT / "src/main/resources/rates/item-identities.json"
    identity_coverage = Counter()
    identity_gaps = []
    if identity_path.exists():
        identities = json.loads(identity_path.read_text(encoding="utf-8"))
        for row in full["bucket"]:
            item = identities.get(normalize(row["item_name"]))
            if item and item.get("ids"):
                identity_coverage["rowsWithWikiItemIds"] += 1
                if item.get("name") and normalize(item["name"]) != normalize(row["item_name"]):
                    identity_coverage["rowsWithDifferentPageAndGameNames"] += 1
            else:
                identity_gaps.append({"owner": row["page_name"], "item": row["item_name"],
                                      "reason": "No numeric Wiki identity; preserve exact-name fallback or named placeholder"})
        identity_coverage["rowsWithoutWikiItemIds"] = len(identity_gaps)
    priority = ["Giant rat", "Goblin", "Cow", "Chicken", "Man", "Woman", "Vorkath", "Zulrah", "Barrows",
                "Reward casket (beginner)", "Reward casket (easy)", "Reward casket (medium)", "Reward casket (hard)",
                "Reward casket (elite)", "Reward casket (master)", "Reward pool", "Reward Cart", "Mystery box",
                "Large salvage", "Small salvage", "Casket (Reward pool)", "Rewards Guardian", "The Whisperer",
                "Duke Sucellus", "The Leviathan", "Vardorvis", "Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut"]
    selected = list(dict.fromkeys(args.owners or [p for p in priority if p in groups]))
    if args.all_pages:
        selected = sorted(groups)
    elif not args.owners:
        manifest = OUT / "sample.json"
        existing = OUT / "evidence.json"
        if not manifest.exists() and existing.exists():
            manifest.write_text(json.dumps(sorted(json.loads(existing.read_text(encoding="utf-8"))), indent=2), encoding="utf-8")
        if manifest.exists() and len(json.loads(manifest.read_text(encoding="utf-8"))) == args.sample:
            selected = json.loads(manifest.read_text(encoding="utf-8"))
        else:
            selected = list(selected)
        # Deterministic diversity by feature signature, then fill with distributed owners.
        signatures = defaultdict(list)
        for item in inventory:
            signatures[tuple(item["features"])].append(item["owner"])
        for candidates in signatures.values():
            if len(selected) >= args.sample:
                break
            candidate = candidates[len(candidates) // 2]
            if candidate not in selected:
                selected.append(candidate)
        remaining = [p for p in sorted(groups) if p not in selected]
        needed = max(0, args.sample - len(selected))
        selected.extend(remaining[i * len(remaining) // needed] for i in range(needed))
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "sources.json").write_text(json.dumps(inventory, indent=2, ensure_ascii=False), encoding="utf-8")
    evidence, conflicts, changes, coverage, not_compared = {}, [], [], Counter(), []
    old_path = OUT / "evidence.json"
    old = json.loads(old_path.read_text(encoding="utf-8")) if old_path.exists() else {}
    for index, owner in enumerate(selected):
        try:
            data = fetch(owner, args.refresh, args.render)
            if data is None:
                conflicts.append({"owner": owner, "kind": "page-not-fetched"})
                continue
            page = page_evidence(owner, data)
            owned = sorted({"Template:" + body.split("|", 1)[0].strip() for _, body in templates(data["parse"]["wikitext"]["*"])
                            if body.split("|", 1)[0].strip().casefold().startswith(owner.casefold() + "/")})
            transclusions = []
            for title in owned:
                child = fetch(title, args.refresh and args.transclusions, args.render) if args.transclusions or (PAGES / (hashlib.sha256(title.encode()).hexdigest() + ".json")).exists() else None
                if child is None:
                    continue
                witness = page_evidence(owner, child)
                page["rows"].extend(witness["rows"])
                transclusions.append({"title": title, "revision": witness["revision"], "sha256": witness["sha256"]})
            if transclusions:
                page["transclusions"] = transclusions
                page["sha256"] = hashlib.sha256((page["sha256"] + "\n" + "\n".join(t["title"] + ":" + t["sha256"] for t in transclusions)).encode()).hexdigest()
            signatures = []
            for row in groups.get(owner, []):
                d = json.loads(row["drop_json"])
                notes = " ".join(str(d.get(k, "")) for k in ("Rarity Notes", "Name Notes", "Alt Rarity", "Alt Rarity Dash")).strip()
                signatures.append("\x1f".join((row["page_name_sub"], row["item_name"], str(d["Rarity"]), str(d.get("Drop Quantity", "")), notes, str(d.get("Rolls", "")))))
            page["exportHash"] = hashlib.sha256("\n".join(sorted(signatures)).encode()).hexdigest()
            if "dropHtml" in data:
                rendered = DropHTML(owner)
                rendered.feed(data["dropHtml"])
                for row in rendered.rows:
                    anchored = {direct["item"] for direct in page["rows"]
                                if "#" in direct["item"] and normalize(direct["item"].split("#")[0]) == normalize(row["item"]) and
                                quantity(direct["quantity"]) == quantity(row["quantity"]) and
                                same_rate(direct["rate"], row["rate"])}
                    if len(anchored) == 1:
                        row["item"] = anchored.pop()
                    if row["table"] != owner:
                        continue
                    # Rendered image alt text sometimes omits dropversion. Recover only
                    # an unambiguous match from independently preserved page templates.
                    tables = {direct["table"] for direct in page["rows"]
                              if normalize(direct["item"]) == normalize(row["item"]) and
                              quantity(direct["quantity"]) == quantity(row["quantity"]) and
                              same_rate(direct["rate"], row["rate"])}
                    if len(tables) == 1:
                        row["table"] = tables.pop()
                    elif tables:
                        row["tables"] = sorted(tables)
                page["renderedRows"] = rendered.rows
                page["renderSha256"] = data["renderSha256"]
            evidence[owner] = page
            if owner in old and old[owner]["sha256"] != page["sha256"]:
                changes.append({"owner": owner, "before": old[owner]["revision"], "after": page["revision"]})
            expected = defaultdict(list)
            for row in page.get("renderedRows", page["rows"]):
                expected[normalize(row["item"])].append(row)
            matched = 0
            page["corrections"] = []
            for row in groups.get(owner, []):
                drop = json.loads(row["drop_json"])
                candidates = [r for r in expected.get(normalize(row["item_name"]), [])
                              if (r["table"] == row["page_name_sub"] or row["page_name_sub"] in r.get("tables", [])) and
                              quantity(r["quantity"]) == quantity(str(drop.get("Drop Quantity", "")))]
                if not candidates:
                    coverage["rowsWithoutDirectPageTemplate"] += 1
                    not_compared.append({"owner": owner, "table": row["page_name_sub"], "item": row["item_name"],
                                         "quantity": drop.get("Drop Quantity", ""), "rate": drop["Rarity"],
                                         "reason": "No independently resolved rendered table/item/quantity match; rate not certified"})
                    continue
                if not any(same_rate(r["rate"], str(drop["Rarity"])) for r in candidates):
                    unique = {str(probability(r["rate"])) if probability(r["rate"]) is not None else normalize(re.sub(r"\[[^]]*\]", "", r["rate"])) for r in candidates}
                    correction = {"item": row["item_name"], "table": row["page_name_sub"],
                                  "quantity": drop["Drop Quantity"], "before": drop["Rarity"], "after": re.sub(r"\[[^]]*\]", "", re.sub(r"^\d+\s*[×x]\s*", "", candidates[0]["rate"])).strip()}
                    if len(unique) == 1:
                        page["corrections"].append(correction)
                    conflicts.append({"owner": owner, "kind": "rate-conflict", "item": row["item_name"],
                                      "table": row["page_name_sub"], "export": drop["Rarity"], "page": candidates,
                                      "resolution": "page-backed-correction" if len(unique) == 1 else "ambiguous"})
                else:
                    matched += 1
            coverage["directRowsCompared"] += matched
            if not page.get("renderedRows", page["rows"]):
                conflicts.append({"owner": owner, "kind": "requires-transclusion-or-mechanics-review"})
            if args.refresh:
                print(f"{index + 1}/{len(selected)} {owner}: {len(page['rows'])} direct page rows", flush=True)
        except Exception as ex:
            conflicts.append({"owner": owner, "kind": "fetch-error", "reason": str(ex)})
    # Preserve independently fetched pages outside a partial targeted run.
    evidence = {**old, **evidence}
    old_path.write_text(json.dumps(evidence, indent=2, ensure_ascii=False), encoding="utf-8")
    report = {"catalogueFetchedAt": full["fetchedAt"], "sourceCount": len(groups), "rowCount": len(full["bucket"]),
              "selectedPages": selected, "evidencePages": len(evidence), "coverage": dict(coverage),
              "pageChanges": changes, "unresolved": conflicts, "identityCoverage": dict(identity_coverage)}
    (OUT / "identity-gaps.json").write_text(json.dumps(identity_gaps, indent=2, ensure_ascii=False), encoding="utf-8")
    (OUT / "not-compared.json").write_text(json.dumps(not_compared, indent=2, ensure_ascii=False), encoding="utf-8")
    (OUT / "report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k not in ("selectedPages", "unresolved")}, indent=2))
    print(f"Unresolved entries: {len(conflicts)}; see tools/wiki-audit/report.json")


if __name__ == "__main__":
    main()
