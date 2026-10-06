"""Offline development helper. Close RuneLite before editing its snapshot.

Adds/removes only labelled example source families; keeps real observations and
collection history unchanged. Every write has a separate timestamped backup.
"""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import tempfile
import time

TYPE = "DEVELOPMENT_EXAMPLE"
EXAMPLES = {
    "Example: rare loot": [("3rd age pickaxe", 1, 1), ("twisted bow", 3, 3)],
    "Example: large quantities": [("coins", 2_147_483_647, 1_234_567_890),
                                   ("runite bar", 1_234_567_890, 987_654_321)],
    "Example: trillion GP": [("3rd age pickaxe", 500, 500)],
}


def identity(name, variant="", context=""):
    return dict(type=TYPE, name=name, variant=variant, context=context,
                wikiOwner="", deathLocation="")


def key(source):
    return "\x1f".join(source[field] for field in ("type", "name", "variant", "context"))


def empty_source(source):
    return dict(identity=source, notifications=0, reportedAmount=0, excluded=0,
                lastReceived=0, items={}, segments={}, variants={})


def item_identity(name, index, ledger):
    entry = index[name]
    candidates = entry["ids"]
    if len(candidates) == 1:
        return candidates[0], entry["name"]
    # Resolve multi-ID Wiki entries using the account's already-recorded canonical
    # identity rather than choosing a historical/quest version of the item.
    recorded = {item["id"] for family in ledger["sources"].values()
                for variant in family["variants"].values()
                for item in variant["items"].values()
                if item["name"].lower() == entry["name"].lower()
                and item["id"] in candidates}
    if len(recorded) != 1:
        raise ValueError(f"Ambiguous item identity for {name}; no unique recorded ID")
    return recorded.pop(), entry["name"]


def modify(ledger, index, remove=False, timestamp=None):
    if ledger.get("schema") != 3:
        raise ValueError("Expected schema 3; original data retained")
    result = copy.deepcopy(ledger)
    now = timestamp if timestamp is not None else time.time_ns() // 1_000_000
    for name, examples in EXAMPLES.items():
        family_id = identity(name)
        family_key = key(family_id)
        previous = result["sources"].get(family_key)
        if previous and previous["identity"] != family_id:
            raise ValueError("Example identity collision; original data retained")
        if remove:
            result["sources"].pop(family_key, None)
            continue
        variant_id = identity(name, "synthetic", "development example; not real loot")
        family = empty_source(family_id)
        variant = empty_source(variant_id)
        variant["notifications"] = max(occurrences for _, _, occurrences in examples)
        variant["reportedAmount"] = variant["notifications"]
        variant["excluded"] = variant["notifications"]
        variant["lastReceived"] = now
        for item_name, quantity, occurrences in examples:
            item_id, display_name = item_identity(item_name, index, ledger)
            variant["items"][str(item_id)] = dict(
                id=item_id, name=display_name, quantity=quantity,
                occurrences=occurrences, lastReceived=now,
                originalIds=[item_id], recordedRates={})
        family["variants"][key(variant_id)] = variant
        result["sources"][family_key] = family
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("snapshot", type=Path)
    parser.add_argument("--remove", action="store_true")
    parser.add_argument("--client-closed", action="store_true", required=True)
    args = parser.parse_args()
    target = args.snapshot.resolve(strict=True)
    original = target.read_bytes()
    ledger = json.loads(original)
    index_path = Path(__file__).resolve().parents[1] / "src/main/resources/rates/item-identities.json"
    updated = modify(ledger, json.loads(index_path.read_text(encoding="utf-8")), args.remove)
    encoded = (json.dumps(updated, indent=2, ensure_ascii=False) + "\n").encode("utf-8")
    # Round-trip exact integers and prove every non-example source is unchanged.
    checked = json.loads(encoded)
    for source_key, family in ledger["sources"].items():
        if family["identity"]["type"] != TYPE:
            assert checked["sources"][source_key] == family
    assert {k: v for k, v in checked.items() if k != "sources"} == {
        k: v for k, v in ledger.items() if k != "sources"}
    stamp = time.strftime("%Y%m%d-%H%M%S") + f"-{time.time_ns()}"
    backup = target.with_name(target.stem + f".before-examples-{stamp}.json")
    with backup.open("xb") as stream:
        stream.write(original)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=target.parent, prefix="examples-", suffix=".tmp", delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(encoded)
            stream.flush()
            os.fsync(stream.fileno())
        if hashlib.sha256(target.read_bytes()).digest() != hashlib.sha256(original).digest():
            raise RuntimeError("Snapshot changed during preparation; no replacement made. Close RuneLite and retry.")
        os.replace(temporary, target)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
    print(f"{'Removed' if args.remove else 'Added'} labelled example sources. Backup: {backup}")
    print("Real sources and collection history verified unchanged; no gameplay events simulated.")


if __name__ == "__main__":
    main()
