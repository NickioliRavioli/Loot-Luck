# Loot Luck

A local-first RuneLite loot ledger with an expandable, source-grouped sidebar.

## Run locally

Requirements: JDK 17 and network access for the first Gradle dependency download. Java output targets release 11. The tested RuneLite client is pinned to **1.13.1**, resolved on 3 October 2026; no global Gradle installation is required.

```powershell
.\gradlew.bat test recordRuntime
.\gradlew.bat run
```

Enable **Loot Luck** and RuneLite's built-in **Loot Tracker**. The plugin does not enable Loot Tracker automatically. Production observations are received through processed `LootReceived` events; this is not a universal kill/opening detector.

For Jagex accounts, follow RuneLite's development-client login instructions: https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts. Only the user should interact with RuneScape; automated game input is not used.

## Review the chosen interface

- Source headers show recorded notification count and optional current GE total. They do not claim an unvalidated count is kills or openings.
- The **W** button opens that source's OSRS Wiki page in your browser. **Show source Wiki links** defaults on and can hide these buttons independently of background Wiki refresh.
- Cosmetic variants share a source header; original NPC/world contexts remain separate underneath. Wiki NPC IDs distinguish same-named enemies belonging to different source pages. Overall expected drops sum validated segments; binomial tails require a shared probability. Dry streaks use only the latest continuous validated segment.
- Item rows show the real ItemManager sprite, name, rarity-colored reciprocal rate, yellow quantity and muted `xoccurrences` where the style includes them.
- **Quantity on icons (trial)** is the default for new settings: an overlay paints yellow integer quantities over the unlabelled sprite, with `xoccurrences` on the right. Unobtained sprites omit the overlay. Abbreviated counts round down (12,500 becomes 12K), using the full recorded quantity rather than a capped game-image count. Existing saved style choices are preserved.
- **Quantity and occurrences on right (trial)** shows a yellow abbreviated quantity, with muted `xoccurrences` underneath when quantity exceeds occurrences. **Quantity on right** shows the full yellow quantity. Previews reuse the compact item-row renderer with the same 12,500 units/seven occurrences and no effect. Abbreviated quantities and values have no decimals; published Wiki rates keep their precision.
- Noted and unnoted items retain separate game IDs and rows; noted rows are labeled **(noted)**. Published table rows are matched to their noted status, so Brutus's guaranteed unnoted steak is not mixed with the noted-steak reward.
- Click a row to expand detailed stats in place; click again to collapse. One row opens at a time. Item hover shows only the rate and last-received date; the expanded **i** button opens scrollable rate history, conditions, provenance and model information.
- Choose Lifetime/Session and most-recent, rarity or driest ordering. Driest includes known unobtained items and ranks only certified event models.
- The view selector provides **By source**, **Recent collections** and **All items**. Recent collections shows individual received notifications, newest first, in pages of 50; identical genuine rewards remain separate. All items totals quantities and item-present notifications across sources, with recent/occurrence/quantity sorting and expandable source breakdowns. These two observation views omit rates and luck statistics.
- **Collection history limit** defaults to **10,000 notifications per account/session**. Set **0 for unlimited**. Reducing it prunes the oldest collection records without changing aggregate totals or rate-version history. Unlimited has no event-count cap but increases memory, disk and snapshot-writing costs. No earlier event history is reconstructed from existing aggregates; new history begins with received notifications. Session reset clears session history only. An empty record is labeled **No items reported**, without claiming a validated empty attempt.
- Configuration provides optional Wiki refresh, unobtained items, luck colors, GE and HA values. Refresh is disabled by default; no account names or loot history are transmitted.
- **Show all known rates** defaults off. Enable it to show each distinct current published rate beneath an item name. Expanded details list published and historical tables plus recorded quantities where a snapshot identified one row; unresolved observations remain under Unknown table. This does not certify NPC-to-table mapping or combine different luck models.
- HA totals exclude rune costs. GE totals use current RuneLite prices, not historical sale proceeds.

## Development fixture client

For large-number examples in an existing development profile, close RuneLite and
run `python tools/large_value_examples.py "PATH_TO_PROFILE_SNAPSHOT.json" --client-closed`.
The offline helper adds three labelled `Example:` source families with five item
rows, uses bundled Wiki identities, and retains a timestamped backup. Real
sources and collection history stay unchanged; All items totals include the
examples. They use normal RuneLite prices and intentionally have no certified
rates or attempt statistics. Repeating the command replaces the same examples
instead of accumulating them. Add `--remove` to remove only those example
families while keeping subsequent real loot. Keep RuneLite closed during removal
too; the helper refuses replacement if the snapshot changes during preparation.

```powershell
.\gradlew.bat fixture --args="--developer-mode --debug"
```

This launcher loads **the test-source FixturePlugin**, not the production Loot Luck plugin. It also demonstrates synthetic Vorkath/Zulrah models and an unobtained item. The salvage fixture yields 1,000 attempts, seven Cotton occurrences and 22 total seeds. Models are explicitly labeled synthetic; they are not certifications of real gameplay. The fixture has no loot subscriptions, profile access, networking or storage. RuneLite itself still loads its ordinary configuration/plugins. Item icons become available when its game cache has loaded. Many full-table untradeables remain unresolved in the fixture because it never scans account/game definitions.

For deterministic native Swing layout inspection, `PanelTest` creates `build/ui-check.png`. That preview checks UI layout, not gameplay behavior.

## Rate data

The bundled catalogue retains **39,786 rows across 2,244 Wiki source pages**, exported from the public `dropsline` Bucket table on 6 October 2026. This includes salvage, monsters, bosses, caskets and reward tables. Coverage is of the exported structured table; a published rate is not automatically an event-level probability.

Source pages are the audit authority. A frozen 150-page sample covers different table features and source families. Preserved wikitext and rendered tables independently compare **4,324 rows**, with no numeric rate conflicts found in those comparisons. **789 sampled rows remain unverified** because a rendered table/item/quantity match could not be resolved. The full catalogue receives parser/identity checks; this does not certify every rate or mechanic on all 2,244 pages.

Wiki item IDs cover 39,635 exported rows; 151 lack numeric Wiki IDs. The identity index includes page titles and game names, including 1,187 rows where they differ. A separate NPC index resolves 3,209 unambiguous NPC IDs to catalogue source pages. Ambiguous identities remain unresolved. Names also receive conservative Unicode, game-tag and whitespace normalization.

Preserved primary responses are in `tools/wiki-inputs/`; the four original audit fixtures remain alongside `full-catalogue.json`. The generator and runtime share their parser. To fetch and generate a new full snapshot:

```powershell
python tools/fetch_catalogue.py
python tools/fetch_item_identities.py
python tools/fetch_monster_identities.py
python tools/audit_wiki.py --refresh --render
python tools/build_wiki_metadata.py
.\gradlew.bat generateRates
.\gradlew.bat test
python -m unittest discover -s tools -p test_*.py
```

These explicit tasks do not run during standard builds. `audit_wiki.py --render` reruns offline against preserved pages; `--all-pages --refresh --render` expands page research to the whole catalogue and can take substantial time. Inspect `tools/wiki-audit/report.json`, `not-compared.json`, `identity-gaps.json` and `sources.json`. Rows retain owner/variant, fraction, quantities, conditions/rolls, provenance and hashes. Unresolved item IDs retain a named placeholder instead of a guessed sprite.

Table selection uses event NPC ID, combat level, membership and source type against explicit Wiki evidence. Giant rat levels 3/6 and 26 resolve to their respective tables; same-level Goblin tables use NPC IDs. Missing or overlapping evidence retains candidate tables and suppresses exact luck. Arbitrary quest, location, equipment or completion conditions require additional observable context and a reviewed rule; there is no safe generic interpretation of all Wiki prose. Keep any exceptions in the evidence/model registry with page revisions and regression tests.

Runtime refresh updates structured rates. Item/NPC identity exports and independently reviewed page prose remain bundled and require the development refresh above. A changed structured export removes stale derived conditions and invalidates gameplay approval. Prose-only Wiki edits cannot be detected by the current runtime refresh; maintainers must rerun page research to review them.

Optional runtime refresh updates the full catalogue with throttled, paginated Wiki queries. Seven-day freshness is checked on startup, enabling networking and loot receipt; Refresh forces an update. Complete results are staged and cached before publication. Malformed, incomplete or unexpectedly shrunken results retain last-good data. Source lookups share the same single worker, one-second request spacing, coalescing, response limits, timeouts and backoff. Failures have a 15-minute automatic retry cooldown. Full refresh can take minutes. There is no hosted update service: opted-in clients query the Wiki directly, which uses more requests than downloading a hosted generated snapshot. Offline startup uses bundled/last-good data. Unmatched names retain observations with unavailable rates.

### Historical published rates

Sources show their combined item rows directly under one main header by default, without condition subheaders. **Show variants separately** in settings gives each condition group its own top-level header. Labels use Wiki table evidence and captured context, including Dark wizard combat levels. Cosmetic NPC IDs sharing verified conditions combine; unknown conditions stay visible. Totals do not change when switching views. Combined item details preserve published table alternatives; a combined source never borrows one member's NPC ID or combat level to select its table. New death-location evidence is conservative, and older unknown locations are never inferred from later loot.

New observations retain immutable rate versions per item, with quantities, item-present notification counts and first/last timestamps. The expanded item's **i** button shows current published data and recorded history. Snapshots preserve fractions, conditions, variants, rolls, provenance and fetch time: the rate known at collection, not proof of the game's effective probability on that date. Unknown-at-collection observations stay unknown after later refreshes.

This private test build starts a fresh ledger in `profiles-wiki-v1`; no previous ledger is migrated. Older files are left in place. Published-rate history remains aggregated by version, while collection history preserves raw notifications under the configured limit.

## Important current limitation

Dry/luck presentation is temporarily hidden through `PanelState.LUCK_STATISTICS_VISIBLE=false`. This hides validation buttons, luck statistics in list/grid details and item information, driest sorting and luck-color controls. Quantities, occurrences, values and published Wiki rates remain visible. Model code, configuration keys and saved statistics are retained; existing approved tracking continues in the background. The model behavior below describes the retained implementation for later re-enabling.

**Gameplay validation remains pending.** Reviewed candidate models exist for selected Cow, Chicken, Giant rat, Vorkath and Zulrah items in `source-models.json`. Each account/source requires gameplay approval after the user confirms its real tracking boundary; the validation button is currently hidden. Approval binds the rate/page/model evidence and affects future observations only. Unapproved sources still record quantities, notification occurrences, values and published rates.

Success means one eligible kill containing the item, regardless of its quantity. Published per-roll rates remain primary; expanded details show the separately derived per-attempt presence chance. Fixed independent rolls use `1-(1-q)^r`. Mutually exclusive outcomes are combined only under an explicit reviewed rule (Chicken feathers). Variable-roll caskets, salvage gates, batches, unresolved conditions and special worlds remain unsupported for exact production luck. See [gameplay-validation.md](docs/gameplay-validation.md).

Dry probability is `(1-p)^d` within continuous recorded coverage; it never claims a pre-tracking streak. Login/restart/hop, disabled core tracking and other coverage interruptions end the active streak. Overall luck retains validated segments: expected drops are `sum(n*p)`, and A/E is recorded successes divided by that expectation. Different probabilities do not receive an averaged binomial tail. The probability tests use preserved Wiki dry-calculator source as an independent numerical reference; UI tails explicitly include the observed count.

## Storage and safety

Filepath-managed data is under RuneLite's plugin directory, `plugin-data/loot-luck/`: hashed profile files in `profiles-wiki-v1/` and public rate responses in `wiki-cache/`. Snapshots are versioned, validated and atomically installed where supported; last-good backups recover corrupted primary snapshots. Unrecoverable profiles pause tracking instead of overwriting history. Session reset never resets lifetime data.

No historical core Loot Tracker import, hosted service, account uploads, reflection, game-input automation or RuneLite public API modifications are included. Queued immutable observations are serialized on RuneLite's shared background executor and retain their captured account identity across plugin/profile changes.

See [THIRD_PARTY.md](THIRD_PARTY.md) for licenses and network disclosures. This is a local development build, not a Plugin Hub submission or an assertion of Hub approval.

Expanded rate breakdowns group rows by their published table name. Columns show
published drop quantity/range, rarity, and recorded occurrences assigned to that row.
Expanded quantities, occurrences and GE/HA totals use exact comma-separated
numbers beside their labels, aligned right. Values move onto their own line only
when the full label/value pair would not fit. Table occurrence counts use the
same fallback. Summary values remain compact.
Compact quantities and money support K, M, B and T, always rounded down without
decimals. Source headers show a single-line name with ellipsis overflow, followed
immediately by the compact occurrence count. The GE value stays on the right.
The expanded-item information button opens a scrollable HTML popover, with gold
headings, bold field labels and published-rate colors instead of a text editor.
All grid item styles show the yellow abbreviated quantity at the top left,
painted above the icon. The selected count style still applies to list rows.
New notifications can be assigned by a unique quantity-range match when the
reported amount and published roll count are both one. Overlapping matches,
batches, missing roll/quantity metadata and multiple rolls remain under Unknown
table. This is descriptive row matching, not certified attempt statistics;
older ambiguous history is not reinterpreted. Historical rate rows stay separate.
Guaranteed published rates display Always. A numeric rate with only a Never
alternate displays its numeric rarity; the restriction remains in its tooltip.

The bottom History and data section provides separate Reset session, Export data,
Import data and Delete data buttons for the selected RuneScape profile. Delete is
red; reset/import/delete require confirmation. Exports contain lifetime observations, retained
collection history and recorded-rate snapshots in JSON, with a hashed profile key;
they do not include credentials, plugin settings or the Wiki cache. Import replaces
lifetime data (no merge), requires a matching profile, and resets the session. The
configured history limit applies to imported history. Existing observations are
saved in a separate `<profile-hash>.before-import.json` export before replacement.
Delete resets lifetime and session data and removes the active/recovery snapshots;
exports, including the pre-import export, are kept. These operations require
confirmation where data is replaced or removed and run on the storage worker.

Source storage now uses schema 3: one source family per loot type and source name,
with original NPC variants/world contexts nested in `variants`. Item observations,
recorded rates and statistical segments remain on those nested records. The sidebar
combines family totals and validated overall expectations across contexts. The
All items view and raw history still count each collection once. Compatibility
migration was intentionally omitted for this private test build: old snapshots and
old exports cannot load. The new ledger namespace starts fresh automatically;
old files are not erased. Current exports/imports use the grouped format.
