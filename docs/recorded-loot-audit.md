# Recorded loot audit — 6 October 2026

A read-only snapshot of both local ledger namespaces was audited before the condition-variant UI changes. The private snapshots and detailed item/quantity report live under ignored `build/`; they are not committed. Continued gameplay does not change the frozen inputs.

The later snapshot includes continued gameplay, including Dark wizards. All **183 recorded source/item contexts** resolve to Wiki rows. Java lookup checks independently verify **271 rate rows**, with **zero conflicts** and **one row not compared**: Dark wizard's Energy potion(2), whose direct Wiki entry leaves the rarity unspecified. A successful item lookup alone does not establish a numerical rate.

The source-page audit covers the 28 represented Wiki owners plus Ghost and independently compares **1,124 exported rows**, finding no rate conflicts in those comparisons. **133 rows** from these pages lack an independently resolved rendered table/item/quantity match; they are not certified. Public coverage is preserved in `tools/wiki-audit/recorded-sources-report.json`. Source-owned reward subpages are cached with their revision/hash and included in the parent evidence contract.

The reported Zombie bone issue was an identity mismatch: the Zombie export uses `Zombie bone#Unpolished`, while the original identity index omitted variant anchors. The Wiki's explicit `version_anchor` now supplies exact IDs for anchored rows generically. The unpolished bone resolves to **1/4**, while the polished bone cannot match that drop row. Rat bone already resolved to **1/4**. Source-page quest notes now survive the audit's `bucketname` mapping, including the Rag and Bone Man II restriction. No quest-based exact-luck model was enabled.

Other findings:

- Saved Tempoross fish rewards have fishing-level table alternatives. RuneLite records boosted Fishing level in metadata, but the Wiki uses base Fishing level and ignores boosts. Old metadata therefore cannot safely select those tables. Published alternatives remain visible without claiming exact luck.
- Easy caskets have Standard/Entrana alternatives; older events did not capture the opening location. Do not infer it from received loot.
- Ghost's looting bag requires Wilderness eligibility. Older events lack location and must stay unknown rather than becoming eligible after a bag drop.
- Goblin's armed/unarmed appearance is not a complete table selector: the Wiki documents an unarmed exception with the armed table. NPC IDs and verified drop-table labels remain the authority.
- Dark wizard's low table covers levels 7/11 and its high table covers 20/22/23. Consistent structured `Drop level` sets now resolve these tables generically, even without a custom page mapping. Earth runes have separate quantity rows; a 36-rune drop is published as 1/32.
- Skeleton's 12-or-15 Air rune quantity is a discrete alternative, not an inclusive range. Matching now rejects 14; the audit separately normalizes alternative separators, thousands separators and damaged dash encodings in Wiki tables.

New observations capture a bounded NPC death snapshot and join it to core Loot Tracker's processed event by the original collection object's identity. Wilderness classification is deliberately limited to recognized ordinary map areas, corroborated by death-time state. Instances, safe enclaves, boundary areas, ambiguous same-ID deaths and unrecognized dungeons remain unknown. Raw death coordinates are retained with collection context but do not create a new source identity per tile. Ghost exact luck remains unsupported; old unknown-location kills never become eligible retrospectively.

Historical collection-time unknown rates remain immutable. Current displayed rates use corrected matching; this audit does not rewrite older ledger history or certify gameplay attempt boundaries.

To repeat the actual Java lookup against explicitly supplied read-only snapshots:

```powershell
.\gradlew.bat auditRecordedLoot --args="path/to/snapshot.json path/to/other-snapshot.json"
```

Compare current source-page evidence separately with `tools/audit_wiki.py --refresh --render --owners ...`. The audit task writes only `build/recorded-loot-audit.json`.
