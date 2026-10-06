# Recorded loot audit — 6 October 2026

A read-only snapshot of both local ledger namespaces was audited before the condition-variant UI changes. The private snapshots and detailed item/quantity report live under ignored `build/`; they are not committed. Continued gameplay does not change the frozen inputs.

All **145 recorded source/item contexts** resolve to published Wiki rows after the fix. The source-page audit refreshed the 20 represented Wiki owners plus Ghost and independently compared **899 exported rows**, finding no numerical conflicts in those comparisons. **152 rows** from these pages lacked an independently resolved rendered table/item/quantity match; they are not certified. Public coverage is preserved in `tools/wiki-audit/recorded-sources-report.json`.

The reported Zombie bone issue was an identity mismatch: the Zombie export uses `Zombie bone#Unpolished`, while the original identity index omitted variant anchors. The Wiki's explicit `version_anchor` now supplies exact IDs for anchored rows generically. The unpolished bone resolves to **1/4**, while the polished bone cannot match that drop row. Rat bone already resolved to **1/4**. Source-page quest notes now survive the audit's `bucketname` mapping, including the Rag and Bone Man II restriction. No quest-based exact-luck model was enabled.

Other findings:

- Saved Tempoross fish rewards have fishing-level table alternatives. RuneLite records boosted Fishing level in metadata, but the Wiki uses base Fishing level and ignores boosts. Old metadata therefore cannot safely select those tables. Published alternatives remain visible without claiming exact luck.
- Easy caskets have Standard/Entrana alternatives; older events did not capture the opening location. Do not infer it from received loot.
- Ghost's looting bag requires Wilderness eligibility. Older events lack location and must stay unknown rather than becoming eligible after a bag drop.
- Goblin's armed/unarmed appearance is not a complete table selector: the Wiki documents an unarmed exception with the armed table. NPC IDs and verified drop-table labels remain the authority.

Historical collection-time unknown rates remain immutable. Current displayed rates use corrected matching; this audit does not rewrite older ledger history or certify gameplay attempt boundaries.

To repeat the actual Java lookup against explicitly supplied read-only snapshots:

```powershell
.\gradlew.bat auditRecordedLoot --args="path/to/snapshot.json path/to/other-snapshot.json"
```

Compare current source-page evidence separately with `tools/audit_wiki.py --refresh --render --owners ...`. The audit task writes only `build/recorded-loot-audit.json`.
