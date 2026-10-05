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
- Same-named variants share one header within the same source type and world context. Display totals and rate history combine; persisted variant records remain separate. Different published rate variants remain labeled, and certified statistics are not mixed across variants.
- Item rows show the real ItemManager sprite, name, rarity-colored reciprocal rate and `xquantity`.
- **Quantity on icons (trial)** is enabled for review: native game sprites draw stack quantity text with the game's own abbreviations/colors; the right-hand **x** number shows item-present occurrences. Disable it to restore right-hand quantities. Zero-observation sprites omit the overlay. Game quantity rendering uses a 32-bit amount; larger lifetime totals remain exact in the tooltip but the sprite amount is capped at the game maximum.
- **Quantity and occurrences on right (trial)** is the latest review default. It overrides the icon-overlay trial: yellow stack-formatted quantity appears on the right, with a muted occurrence count below only when quantity exceeds occurrences. Disable this to return to the icon trial; disable both to use the original quantity-only layout. Exact quantities remain in tooltips.
- Noted and unnoted items now retain separate game IDs and rows; noted rows are labeled **(noted)**. Published table rows are matched to their noted status, so Brutus's guaranteed unnoted steak is not mixed with the noted-steak reward. Older normalized aggregates are preserved; if they included both forms, their quantities cannot be split retrospectively.
- Click a row to expand detailed stats in place; click again to collapse. One row opens at a time. Item hover shows only the rate and last-received date; the expanded **i** button opens scrollable rate history, conditions, provenance and model information.
- Choose Lifetime/Session and most-recent, rarity or driest ordering. Driest includes known unobtained items and ranks only certified event models.
- The view selector provides **By source**, **Recent collections** and **All items**. Recent collections shows individual received notifications, newest first, in pages of 50; identical genuine rewards remain separate. All items totals quantities and item-present notifications across sources, with recent/occurrence/quantity sorting and expandable source breakdowns. These two observation views omit rates and luck statistics.
- **Collection history limit** defaults to **10,000 notifications per account/session**. Set **0 for unlimited**. Reducing it prunes the oldest collection records without changing aggregate totals or rate-version history. Unlimited has no event-count cap but increases memory, disk and snapshot-writing costs. No earlier event history is reconstructed from existing aggregates; new history begins with received notifications. Session reset clears session history only. An empty record is labeled **No items reported**, without claiming a validated empty attempt.
- Configuration provides optional Wiki refresh, unobtained items, luck colors, GE and HA values. Refresh is disabled by default; no account names or loot history are transmitted.
- **Show all known rates** defaults off. Enable it to show each distinct current published rate beneath an item name. Expanded details list published and historical tables plus recorded quantities where a snapshot identified one row; unresolved observations remain under Unknown table. This does not certify NPC-to-table mapping or combine different luck models.
- HA totals exclude rune costs. GE totals use current RuneLite prices, not historical sale proceeds.

## Development fixture client

```powershell
.\gradlew.bat fixture --args="--developer-mode --debug"
```

This launcher loads **the test-source FixturePlugin**, not the production Loot Luck plugin. It also demonstrates synthetic Vorkath/Zulrah models and an unobtained item. The salvage fixture yields 1,000 attempts, seven Cotton occurrences and 22 total seeds. Models are explicitly labeled synthetic; they are not certifications of real gameplay. The fixture has no loot subscriptions, profile access, networking or storage. RuneLite itself still loads its ordinary configuration/plugins. Item icons become available when its game cache has loaded. Many full-table untradeables remain unresolved in the fixture because it never scans account/game definitions.

For deterministic native Swing layout inspection, `PanelTest` creates `build/ui-check.png`. That preview checks UI layout, not gameplay behavior.

## Rate data

The bundled catalogue retains **39,786 rows across 2,244 Wiki source pages**, exported from the public `dropsline` Bucket table on 4 October 2026. This includes Small salvage, other salvage tiers, monsters, bosses and reward tables. Coverage is of the exported structured table; some game rates remain unpublished and some RuneLite source names need Wiki aliases. Conditions, variants and rolls remain intact. A published rate is not automatically an event-level probability.

Preserved primary responses are in `tools/wiki-inputs/`; the four original audit fixtures remain alongside `full-catalogue.json`. The generator and runtime share their parser. To fetch and generate a new full snapshot:

```powershell
python tools/fetch_catalogue.py
.\gradlew.bat generateRates
```

The complete export supplies its fetched timestamp. These explicit tasks do not run during standard builds. Rows preserve owner/variant, fraction, quantities, conditions/rolls, provenance and hashes. Unresolved item IDs retain a named placeholder instead of a guessed sprite.

Optional runtime refresh updates the full catalogue with throttled, paginated Wiki queries. Seven-day freshness is checked on startup, enabling networking and loot receipt; Refresh forces an update. Complete results are staged and cached before publication. Malformed, incomplete or unexpectedly shrunken results retain last-good data. Source lookups share the same single worker, one-second request spacing, coalescing, response limits, timeouts and backoff. Failures have a 15-minute automatic retry cooldown. Full refresh can take minutes. There is no hosted update service: opted-in clients query the Wiki directly, which uses more requests than downloading a hosted generated snapshot. Offline startup uses bundled/last-good data. Unmatched names retain observations with unavailable rates.

### Historical published rates

New observations retain immutable rate versions per item, with quantities, item-present notification counts and first/last timestamps. The expanded item's **i** button shows current published data and recorded history. Snapshots preserve fractions, conditions, variants, rolls, provenance and fetch time: the rate known at collection, not proof of the game's effective probability on that date. Unknown-at-collection observations stay unknown after later refreshes.

Existing schema-1 files migrate to schema 2 without changing counters or inventing older rate history. Published-rate history remains aggregated by version; the new collection history separately preserves raw notifications under the configured limit. Exact probability statistics still require source certification.

## Important current limitation

**No production source is certified for exact probability statistics yet.** `ValidationRegistry` deliberately returns unsupported for every event. Real loot quantities, notification occurrences, last-received dates, values and published rates work independently of certification. Attempts, A/E, dry probability and binomial tails remain `—` until gameplay boundary evidence and source mechanics are reviewed.

This is intentional: `amount=1` does not establish an eligible independent attempt. Large salvage pre-roll/main-table mechanics, missing empty NPC outcomes, boss split rewards and batch semantics still need validation. See [gameplay-validation.md](docs/gameplay-validation.md).

## Storage and safety

Filepath-managed data is under RuneLite's plugin directory, `plugin-data/loot-luck/`: hashed profile files in `profiles/` and public rate responses in `wiki-cache/`. Snapshots are versioned, validated and atomically installed where supported; last-good backups recover corrupted primary snapshots. Unrecoverable profiles pause tracking instead of overwriting history. Session reset never resets lifetime data.

No historical core Loot Tracker import, hosted service, account uploads, reflection, game-input automation or RuneLite public API modifications are included. Queued immutable observations are serialized on RuneLite's shared background executor and retain their captured account identity across plugin/profile changes.

See [THIRD_PARTY.md](THIRD_PARTY.md) for licenses and network disclosures. This is a local development build, not a Plugin Hub submission or an assertion of Hub approval.

Expanded rate breakdowns group rows by their published table name. Columns show
published drop quantity/range, rarity, and collected units assigned to that row.
New notifications can be assigned by a unique quantity-range match when the
reported amount and published roll count are both one. Overlapping matches,
batches, missing roll/quantity metadata and multiple rolls remain under Unknown
table. This is descriptive row matching, not certified attempt statistics;
older ambiguous history is not reinterpreted. Historical rate rows stay separate.
Guaranteed published rates display Always. A numeric rate with only a Never
alternate displays its numeric rarity; the restriction remains in its tooltip.

The sidebar's Data settings menu provides Export data, Import data and Delete data
for the selected RuneScape profile. Exports contain lifetime observations, retained
collection history and recorded-rate snapshots in JSON, with a hashed profile key;
they do not include credentials, plugin settings or the Wiki cache. Import replaces
lifetime data (no merge), requires a matching profile, and resets the session. The
configured history limit applies to imported history. Existing observations are
saved in a separate `<profile-hash>.before-import.json` export before replacement.
Delete resets lifetime and session data and removes the active/recovery snapshots;
exports, including the pre-import export, are kept. These operations require
confirmation where data is replaced or removed and run on the storage worker.
