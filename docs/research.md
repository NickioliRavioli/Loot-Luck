# RuneLite loot luck / drop rate tracker — technical research

Research date: **2 October 2026, Australia/Brisbane**. This is a source audit and design recommendation, not a plugin implementation or an assurance of Plugin Hub approval.

## Findings and recommendation

**Product scope clarified:** this is a general drop-rate tracker for **all items with a drop rate**, across salvage, ordinary monsters, bosses, pickpocketing, containers, clues, minigames, raids, skilling and other probabilistic reward sources. It is not restricted to collection-log items, rare items, or Cotton seeds. Salvage is an initial validation case, not the product's boundary. Guaranteed outcomes also belong in the catalogue with an appropriate label. Coverage must distinguish **rate data available**, **observations available**, and **per-attempt probability model validated**; those capabilities do not always arrive together.

The idea is feasible for sources whose observable loot events correspond to individual, eligible attempts. **Use RuneLite's processed `LootReceived` event for the first implementation, preserve quantity separately from item-present events, and obtain structured drop rows from the OSRS Wiki Bucket API.** Bundle a generated fallback for explicitly supported sources; offer opt-in, cached, source-scoped Wiki refreshes. Do not start with a universal drop-table interpreter.

Large salvage is a useful initial validation case: current RuneLite has a dedicated salvage detection branch and a `testLargeSalvage` test. The Wiki lists Cotton seed, item **31545**, quantity **1–5**, rate **5/2,218**, `Rolls=1`, and no alternative rate or rarity condition in that row. That is **1 in 443.6**, not exactly 1 in 443. The follow-up investigation found that the main-table fractions sum to one while separate pre-roll rewards also have nonzero rates. **The published row is verified; its unconditional per-opening interpretation is not yet established.** Both roll semantics and one-opening-per-event behavior must be validated before enabling an exact Cotton luck statistic. [RL-salvage] [RL-salvage-test] [W-large] [W-cotton] [W-drop-table]

The principal limitation is the denominator: `LootReceived` is a loot notification, not a universal attempt-completed event. Empty outcomes can disappear before publication, and some notifications aggregate several attempts. **A present item in a batch does not reveal how many openings contained it.** Suppress per-attempt probability statistics where that information is unavailable. [RL-emit] [RL-widget] [RL-batches] [RL-manager]

## Evidence and reproducibility

The official Plugin Hub tree was resolved to `7cb0ee81560bf9dee06ff9b8e376626bc6943ce3`. Each plugin archive was inspected at the commit in its Hub descriptor, not simply its default branch. Default-branch heads were queried separately; they are recorded below. RuneLite was inspected at **`d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22`**. All 1,889 extracted Java/POM/license files in that RuneLite archive were available for cross-repository searches. These are inspected repository revisions, not a claim that every installed client uses the same code.

Source links below use immutable commits and actual source line numbers. Wiki article revisions and numerical verification are also recorded in [research-evidence.json](research-evidence.json). Wiki module output depends on transcluded modules: an article revision alone does not freeze every generated table; retain response provenance/content hashes as well.

**Verified** means read in implementation or obtained from a primary API response. **Inference/recommendation** means a proposed consequence or design choice. **Unverified** means gameplay behavior, release compatibility, or review approval still needs testing. The cloned projects were not built or launched, and no account, player log, or third-party player lookup was requested.

## 1. Existing plugin comparison

All fourteen inspected plugin repositories contain a BSD 2-Clause `LICENSE`. This covers their code; it does not automatically relicense underlying Wiki content. Repository names below link to the inspected commits. The [Hub directory at the audited commit][H-markers] supplies identity and status.

| Plugin / Hub ID | Inspected Hub commit | Default-branch head at research time | Actual drop-rate route | Fit / principal limitation |
|---|---|---|---|---|
| [Droppy][P-droppy] / `droppy` | `e8077217b7a55a5aa0071fdb556fcaba41c79b74` | Same | Bundled `drops.json`; developer Java scraper queries Wiki wikitext | Collection-log-focused; 101 bundled entries, no salvage entry; separate third-party dry-stat service |
| [Drop Rate][P-drop-rate-properties] / `drop-rate-properties` | `d370c9ad1085e9ef0040053dfa5c5e9e436990cc` | Same | Bundled JSON; Python Bucket crawler plus supplemental tables | Useful variant/condition/RDT handling; main generator intentionally filters source types and common drops |
| [Drop Rate Display][P-drop-rate-display] / `drop-rate-display` | `74f57566e28369f36b6ddd4514478d342a1775da` | Same | Bundled `drop_rates.json`, generated from Bucket | Broad coverage including Large salvage; generator discards important condition/roll metadata |
| [Loot Tracker Extended][P-loot-tracker-extended] / `loot-tracker-extended` | `8fdc35bcc1a040e43b7c15a3501e61b9ba978817` | Same | Opt-in live Wiki HTML via `Special:Lookup`, memory/disk caches | Closest processed-event/panel precedent; aggregate items remain quantity totals |
| [Drop Finder][P-dropfinder] / `dropfinder` | `05587f41bb032b418911acff4341cf9e5c1b096a` | Same | Runtime MediaWiki parse of `{{Drop sources|item}}`, then HTML parsing | Reverse item-to-source lookup; not a loot-event statistics tracker |
| [Collection Log Luck][P-collection-log-luck] / `collection-log-luck` | `b3cb882a5c675f34a27aaed748f2ac9030cf54e2` | `a2c8086d572c5015eedba6b71bbbb4a30790b213` | Hardcoded Java probability/mechanics registry | Rich probability models, but large manual maintenance surface and collection-log quantities |
| [Loot Lookup][P-loot-lookup] / `loot-lookup` | `58812e18d0f955d597c4894ece0ae23aff1a3a74` | Same | Live Wiki HTML, NPC ID/name `Special:Lookup` | Mapping/parser precedent; UI display parsing is not sufficient for probability inference |
| [Clog Drop Rates][P-drop-rates-clog] / `drop-rates-clog` | `eb1d63c072fe037500c1e7e78440b8ae949957d2` | `719c30f702b8978d16ce8a0a15a5b101c6ad3ab0` | Generated JSON fetched from GitHub `data` branch; bundled fallback | Strong automatic-update precedent; collection-log item coverage rather than general loot |
| [Empty Loot Tracker][P-empty-loot-tracker] / `empty-loot-tracker` | `73d420714e0842507dc43aa79be18d380d188258` | Same | No expected-rate provider in inspected tracker; empirical counts | Already separates quantity and item-present kills; adds heuristic kill/empty-outcome detection |
| [Loot Table Viewer][P-loot-table-viewer] / `loot-table-viewer` | `5f840d4f6789ffb471bcaffe19d598940381ae81` | Same | Live MediaWiki search + raw wikitext parse | Received-loot display with cached lookups; raw template recognition can miss transclusions/skill templates |
| [Loot Ledger][P-loot-ledger] / `loot-ledger` | `216c42d94f797c6101f5b30860e2821674727c13` | Same | Live Wiki HTML, `Special:Lookup`, query/opensearch helpers | Useful ID/cache lookup precedent; not the proposed per-attempt luck model |
| [CSV Lifetime Drop Ledger][P-csv-lifetime-drop-ledger] / `csv-lifetime-drop-ledger` | `7628bf8e586841af12c04c940118386c22553f27` | Same | No expected-rate retrieval in inspected handler | Logs quantities from `ServerNpcLoot`; does not retain successful event counts there |
| [Detailed Boss Drop Rates][P-detailed-boss-drop-rates] / `detailed-boss-drop-rates` | `4aae479112ea3ebd16f66dc597d07839e4b3fddd` | `8fcfeee8d810c427b4f9632ccce26d8d2832112a` | Bundled `drop-data.json` | Curated boss mechanics reference; not an automatic general-purpose rate backend |
| [Salvage Sack][P-salvage-sack] / `salvage-sack` | `69ad7087b8831a2b524ee50b793f8b44e2a80e7d` | Same | Bundled/user-edited `drop_rates.json`; direct sorting-chat parser | Closest salvage precedent; separate quantity/occurrences, but chat-message attempt semantics still need validation |

**Droppy identity is confirmed by `plugins/droppy`: `bckpockets/droppy`, not an unrelated file-sharing/package project.** Its Hub descriptor warns about sending RSN and IP to a third-party server. Collection Log Luck's descriptor similarly warns about username/IP. The Collection Log Luck warning remains in its descriptor even though its remote lookup is disabled in the inspected code. A player-upload service is unnecessary for this plugin. [H-droppy] [H-cll]

Related-plugin search initially covered Hub filenames containing drop, loot, luck, and collection. The follow-up added salvage and found `salvage-sack`, which was missed by the initial filename search and is now deeply inspected below. Additional manifests included `nex-droprate-calculator`, `tob-drop-chance`, skilling loot trackers, and notifier/export tools. They were not deeply audited: activity-specific calculators, detection extensions, and notifications are less useful as general data/event backends. `loot-table` points to `Adam-/runelite-plugins` at `3452f9dc16c3193f606b669e4d2489a2af56e79a` but is explicitly disabled as unmaintained. This search is not proof that no other plugin overlaps the idea. [H-markers] [H-old-table]

## 2. Repository and source references

The comparison links identify the exact inspected trees. The following implementation traces cite the fetching, parsing, mapping, data, and update paths rather than relying on README descriptions. Full descriptor text, repository identities, commit pairs, and license metadata are in the evidence file. Where an upstream head differs, conclusions below refer to the **Hub-pinned version**; newer upstream changes were not substituted into the audit.

## 3. Exact retrieval implementations and mapping

### Droppy

- **Runtime:** `WikiDropFetcher` constructor calls `loadBundledData`; it reads class-relative `com/droppy/drops.json` using Gson. `getDropData` resolves normalized source names, explicit aliases, and singular/plural fallbacks. There is no runtime Wiki HTTP call in this class. Aliases include Barrows and several minigame reward names; Phosani's Nightmare is collapsed to Nightmare, which illustrates why aliases need semantic review. [D-fetch]
- **Developer generation:** `src/test/java/com/droppy/WikiScraper.java`, invoked by Gradle `updateDrops`, iterates a predefined collection-log page list and ownership map. `fetchWikiText` calls `https://oldschool.runescape.wiki/api.php?action=parse&page=…&prop=wikitext&format=json&redirects=1`. It scans inline DropsLine templates with brace-aware parameter splitting, filters to collection-log items, resolves item IDs from `https://prices.runescape.wiki/api/v1/osrs/mapping`, and writes the resource JSON. Missing main-page drops trigger a limited subpage fallback, not a full template/module evaluator. [D-build] [D-generate] [D-text] [D-parse] [D-ids]
- **Limitations:** the persisted drop model has item name, numeric probability, item ID, and display rarity; not full quantities, conditions, table hierarchy, or roll counts. Guaranteed rates are excluded by the parser. `deduplicateDrops` keeps the first entry for duplicate names, which can erase distinct source conditions. No scheduled generation workflow was found in the pinned archive. Regeneration and a Hub update are necessary to ship revised bundled data. [D-parse] [D-dedupe]
- **Unrelated runtime service:** `DroppyApiClient.submitDry` POSTs `{name,response}` to `/dry`, and `lookupDry` GETs `/dry?name=…`; the configured default base is `https://droppy.ark-dlns-smith.workers.dev`. This exchanges player dry-stat text, not the bundled expected-rate dataset. `DroppyPlugin` uses legacy NPC events for NPC KC and processed events for other sources; the latter increments once per notification rather than using batch `amount`. [D-service] [D-config] [D-events]

### Drop Rate / Pluckss

- **Runtime:** `DropRatePlugin.startUp` loads `/droprates_clean.json`, `/npc_versions.json`, `/minigame_droprates.json`, `/special_droprates.json`, `/clue_droprates.json`, `/rare_drop_table.json`, `/rdt_quantities.json`, and `/drop_metadata.json`. Primary/inverted name maps, NPC-ID versions, source aliases, contextual rules, and item overrides serve lookups; expected rates are not fetched live in the Java plugin. [DR-load] [DR-match] [DR-base]
- **Generation:** `tools/crawl_bucket.py` uses `action=bucket` at the Wiki API for `dropsline`, `drop_table_sources`, `infobox_monster`, and `infobox_npc`, plus `https://static.runelite.net/cache/item/names.json` for game item names. Commands support fetch, generate, diff, and check, with disk-cached responses and delayed pagination. It generates four principal data resources; supplemental minigame/clue/special/condition resources are not generated by this crawler. [DR-crawler]
- **Mapping/conditions:** NPC versions associate Wiki subpages/anchors and combat levels with infobox NPC IDs. Runtime context rules explicitly distinguish Slayer task, Ring of Wealth, and Legends' Quest. RDT quantity ranges disambiguate overlapping direct/table drops only where quantities differ. The crawler uses Wiki-expanded RDT rates and derives specific quest/ring variants with curated constants; blindly multiplying those already-expanded rates again would be wrong. [DR-versions] [DR-context] [DR-rdt] [DR-quantity]
- **Coverage/update limits:** `build_primary` keeps eligible NPC pages and combat/thieving/hunter rows, selectively reward rows, excludes Coins and Deadman pages, and filters denominators below 50. Sailing salvage is not a principal-crawler source. It preserves `Rolls` in formatted strings, but this is display metadata, not proof of a per-event probability model. `.github/workflows/drop-data.yml` checks freshness weekly and creates/updates a drift issue; it **does not automatically publish new plugin data**. Fixes require generation, commit, and a Hub revision update. [DR-primary] [DR-update]

### Drop Rate Display

- **Runtime:** `DropRateDataStore.load/loadFromReader/getSource` reads `/drop_rates.json`, keyed by source page then item name. Aliases translate Barrows, raid chest names, Tempoross, Wintertodt, and GOTR. Fallback strips `#variant`; item names are also indexed case-insensitively. `SourceDropTable.getDrop(name,quantity)` selects a quantity variant. The model contains NPC IDs, but the inspected ground-loot path actually looks up by NPC name. [DD-store] [DD-source] [DD-ground]
- **Generation:** `scripts/generate_drop_rates.py` queries `dropsline` and `infobox_monster` through the Bucket API, with targeted/full scans, pagination, one-second gaps, and retry backoff. It commits a compact JSON snapshot. The inspected snapshot declares version **2026-07-08** and **2,090 sources**, including Large salvage/Cotton seed at `5/2,218`, quantity `1-5`. [DD-generator] [DD-data]
- **Important loss of semantics:** `parse_drop_row` retains name, rarity, and quantity, but drops `Rolls`, approximation, rarity notes, alternative rarity, and table conditions. Rows are grouped by `page_name`; `page_name_sub`/RDT flags are selected but not used to preserve contexts. For an identical item and quantity, **first rate wins**. Picking a rate based on an observed stack is appropriate for some display use cases, but does not yield the unconditional probability of seeing that item in an attempt. No scheduled generator workflow was found in the pinned archive. [DD-generator] [DD-entry]

### Loot Tracker Extended

- **Runtime:** `WikiDropRateService.lookup/lookupVariants` resolves source identity and asynchronously fetches `https://oldschool.runescape.wiki/w/Special:Lookup?type=npc&name=…[&id=…]`. This is **rendered HTML scraping**, not Bucket/MediaWiki JSON. `WikiDropTableParser` uses Jsoup on headings and `table.item-drops`, retaining quantity/rarity strings and table/context labels. [LTE-service] [LTE-http] [LTE-url] [LTE-parser]
- **Mapping:** `WikiSourceResolver` loads `/wiki-source-overrides.json`; it resolves source type/name/NPC ID, clue names to reward caskets, and ID-specific variant ownership. Examples include Barrows → Chest (Barrows), Dusk → Grotesque Guardians, and Wilderness Slayer Cave variants. `lookupVariants` can merge candidate tables for old name-grouped records. `WikiDropTable.getTooltipLines` deliberately shows a multiple-rates notice when ambiguity persists instead of collapsing everything to one number. [LTE-resolver] [LTE-overrides] [LTE-tooltip]
- **Cache/update behavior:** explicit network enablement, two concurrent requests, 64 pending requests, 256 memory entries, 1,024 disk entries, 4 MiB response bound, in-flight coalescing, cancellation generation, and versioned hashed cache files. Disk cache reads update access time; the inspected code has **no age-based freshness expiry**. A live provider can therefore remain stale until clearing/eviction, despite fetching from the Wiki originally. [LTE-service] [LTE-disk]
- **Counting precedent:** `SearchableLootTrackerPlugin.onLootReceived` forwards to `LootDataService.add`, which sums `event.amount`, extracts NPC identity where possible, adds item quantities, and keeps session detail. Its quantity aggregates do not establish historical per-item occurrence counts. Its one-time import of built-in history cannot recover information already discarded by core aggregation. [LTE-events] [LTE-count] [LTE-import]

### Drop Finder

- `WikiDropClient.fetchSources` sends a runtime POST to `https://oldschool.runescape.wiki/api.php` with `action=parse`, `prop=text`, `contentmodel=wikitext`, and a batch of `{{Drop sources|item name}}` templates separated by markers. The JSON envelope contains rendered HTML; `splitAndParse/parseTable` extracts source name, combat level, quantity, and `data-drop-fraction`. It also queries `prop=pageimages` for icons. [DF-fetch] [DF-parse]
- It caches item lookups, including empty results, in concurrent memory maps; a request batches at most 30 missing items. No disk cache or TTL was found in the client. Repeated lookups are cached until the instance is recreated. Mapping runs **item name → Wiki template → source-name rows**; it does not use NPC IDs or record attempts. The fraction parser accepts simple fractions/Always, not general multi-roll expressions or structured conditions. [DF-fetch] [DF-parse]

### Collection Log Luck

- Expected rates and mechanics are hardcoded in `luck/LogItemInfo.java` through item IDs/names and `RollInfo(LogItemSourceInfo, probability, rollsPerKc)`. `LogItemSourceInfo` names collection-log KC labels, not a generic RuneLite loot-source resolver. Item aliases and configuration correct special cases. This is **manually maintained Java data**, not live Wiki rates or an automatically generated JSON backend. [CL-registry] [CL-sources] [CL-trials]
- The retained `CollectionLogLuckApiClient.getCollectionLog` defines `https://api.collectionlog.net/collectionlog/user/{username}` for player observations, but **the call is commented out** in the Hub-pinned `CollectionLogLuckPlugin.fetchCollectionLog`. Lines 407–409 instead complete an empty collection log because the service was disabled. Its future/cache machinery therefore does not demonstrate active network retrieval in this version; rates remain hardcoded. This is a material limitation of the inspected implementation. [CL-api] [CL-fetch]
- Models include binomial, Poisson-binomial, fixed/variable stack sums, guaranteed-once, dupe protection, hidden shards, and pity cases. `BinomialDrop` uses Apache Commons Math and insists on equal roll probabilities across sources. Its success count starts from collection-log **quantity**, with subclass corrections; this is a different observable from our item-present events. It often estimates rolls from KC/configured averages, which should not be copied as exact event probability. [CL-binomial] [CL-trials] [CL-registry]
- Updates require code changes and a new Hub revision. Historical-rate and average raid-point configuration illustrates the maintenance required when attempting to model all activities. Reuse the idea of explicit unsupported mechanics and specialized distributions, not a huge copied registry or its combined “luck” score.

## 4. Additional implementations and reusable patterns

- **Loot Lookup:** `osrswiki/WikiScraper.getDropsByMonster` fetches live `Special:Lookup` HTML; NPC interaction supplies IDs, with curated page rewrites for Dusk/Dawn, Moons, and Awakened bosses. Jsoup parses heading/table sections. `parseRow` strips a leading `2 ×`/`3 ×` and uses the first alternative rarity; that loses semantics needed for our calculations. No persistent rate snapshot/cache was found in the scraper or inspected panel. Its daily `wiki-structure-check.yml` tests parser compatibility; it is not data generation. [LL-fetch] [LL-map] [LL-row] [LL-ci]
- **Clog Drop Rates:** `DropRatesClogPlugin` loads bundled item-ID-keyed resources and then asynchronously GETs five datasets from `https://raw.githubusercontent.com/pairofcrocs/drop-rates-clog/data/`. It does **not call the Wiki at runtime**. `scripts/scrape.py` generates rates through item-filtered Bucket queries, raw wikitext fallbacks, roll/approximation preservation, and special Slayer alternative-rate detection. The weekly workflow publishes data to the separate branch and refreshes the bundled fallback, with merge-previous and sanity checks. `gen_clog_items.py` obtains collection-log IDs from OpenRS2 game-cache groups; this supplies IDs, not drop probabilities. Remote data refresh does not require a plugin code release, whereas new executable logic still does. [CLOG-runtime] [CLOG-scrape] [CLOG-slayer] [CLOG-update] [CLOG-ids]
- **Empty Loot Tracker:** `recordLoot/mergeItems` maintains `quantity` and `dropCount` separately, incrementing the latter once per item-present kill. It combines NPC death/damage heuristics with `ServerNpcLoot`/`NpcLootReceived`, pending kills, late-loot reconciliation, and fingerprint deduplication. This already solves a substantial part of the observation problem for NPCs. Its displayed rate is empirical; no Wiki expected-rate provider was found in the inspected tracker. Those heuristics are not proof that all empty kills are correctly attributable, and this approach is much more detection code than our preferred salvage-first design. [EMPTY-count] [EMPTY-merge] [EMPTY-ui]
- **Loot Table Viewer:** `OsrsWikiClient.findPage/searchForTitle/loadWikitext` uses MediaWiki search, then `action=parse&prop=wikitext`. The search fallback may choose the first result; `WikiDropParser` primarily recognizes inline DropsLine/DropLine/Clue templates, not a full template evaluator. `LootTableViewerPlugin` listens to processed loot, caches/coalesces lookups, and records received rows. Copy neither arbitrary search-result matching nor template-only parsing into an authoritative probability backend. [LTV-client] [LTV-parser] [LTV-events]
- **Loot Ledger:** `DropFetcher.buildWikiUrl/fetchHtml` uses live HTML via `Special:Lookup` with ID/name and background workers; query/opensearch helpers resolve titles. Useful lookup concepts, but not selected as the statistics backend. [LEDGER-fetch]
- **CSV Lifetime Drop Ledger:** `onServerNpcLoot` loops through stacks and adds item quantity to its ledger, then saves asynchronously; that handler does not preserve item-present event counts or source attempts. [CSV-events]
- **Detailed Boss Drop Rates:** `DropDataService` loads class-relative `drop-data.json` and indexes configured NPC names. Curated data can describe mechanics that simple fractions miss, but no automated generator/scheduled refresh was found in the pinned archive. Useful as a design reference, not a low-maintenance universal dataset. [BOSS-data]


### Salvage Sack — additional Hub-verified implementation

The official `plugins/salvage-sack` descriptor identifies `BugcatcherBill/SalvageSack`, pinned to **`69ad7087b8831a2b524ee50b793f8b44e2a80e7d`**, matching its upstream HEAD at follow-up time. Its inspected LICENSE is BSD 2-Clause. [H-sack] [SACK-license]

- **Detection:** `SalvageSackPlugin.onChatMessage` accepts GAMEMESSAGE/SPAM, strips tags, and matches `You sort through the (.+?) salvage and find: (\d+) x (.+?)\.`. One matching message increments `totalLoots`, resolves one item name and calls `recordLoot` with the reported quantity. It does not consume processed `LootReceived` or reconcile inventory/ground/cargo containers. This avoids dependence on a subsequent inventory delta, but relies on chat messages being complete and one-per-opening. The first matching item/message cannot establish coverage of multi-item, pet, overflow, or duplicate-message outcomes. The quantity grammar also accepts unformatted digits only. These are code limitations/validation targets, not proven gameplay failures. [SACK-pattern] [SACK-chat]
- **Counters:** `SalvageItem.recordDrop` increments `dropCount` by one and adds the full stack to `totalQuantity`. This correctly separates occurrences from quantity for an individual parsed reward. The denominator remains parsed messages, so a correct item counter does not by itself prove correct opening counts. [SACK-item]
- **Rates and updates:** `DropRateManager` loads the bundled resource, then local `drop_rates.json` overrides; version migration compares v1 defaults with the new bundle to preserve custom values. Name keys and `(unf)` aliases map rates by shipwreck type. The bundle declares `lastUpdated=2026-09-14`, includes Cotton at rounded `0.00225428`, and has no structured roll/conditional provenance. No automatic Wiki generator or scheduled rate-refresh workflow was found in the pinned archive. Unknown rates return zero, which our design must replace with an explicit unknown status. [SACK-rates] [SACK-rate-map] [SACK-data]
- **Identity:** known IDs plus cached ItemManager name resolution are used; unresolved names can become a positive name hash rather than a genuine game item ID. Keep unresolved identities separate in our design. The inspected logic tests include a copied regular expression and helper/data cases; they do not provide a live server/client event trace. [SACK-identity] [SACK-tests]

This is a strong salvage UI/counter precedent, not proof of the game's roll algorithm or complete detection. A broader plugin should reuse concepts and attributed BSD code only where useful, while preserving generic source identities, exact rational rates, and explicit coverage.

The useful combination is **core event reuse + Empty Loot Tracker's separate counters + Wiki Bucket data + explicit unsupported cases + bounded asynchronous caching**. No inspected implementation establishes this entire combination across every requested source.

## 5. RuneLite Loot Tracker and `LootReceived`

### Event contract and publication

`LootReceived` is `net.runelite.client.plugins.loottracker.LootReceived`, not an event in `net.runelite.api.events`. Its fields are source `name`, `combatLevel`, `LootRecordType type`, `Collection<ItemStack> items`, `int amount`, and untyped `Object metadata`. There is no UUID, guaranteed attempt boundary, timestamp, or typed probability context. [RL-event]

Across the inspected RuneLite Java tree, the only construction of `new LootReceived` is `LootTrackerPlugin.addLoot`. All its detection branches converge there. `addLoot` creates a queued HTTP `LootRecord`, schedules panel entries for nonempty items, and publishes the event **even if its input items are empty**. However, some upstream branches skip empty outcomes before reaching it. [RL-emit]

The collection is not promised immutable. In particular, `LootManager.processScriptLoot` posts its `scriptItems` and then clears that list. **Copy item IDs/quantities and any needed primitive context synchronously in the event subscriber before queuing persistence, UI, or network work.** [RL-manager]

### Where the loot comes from

| Path | Identification and boundary | Consequence for statistics |
|---|---|---|
| NPC loot | `LootManager` groups in-game `LOOTTRACKER_ADD_LOOT` script rows by event ID and publishes `ServerNpcLoot`; core tracker uses NPC composition/name/ID | Current tracker consumes server events, not legacy `NpcLootReceived`; no-item kills are not generally published |
| Legacy NPC ground detection | `LootManager` still emits `NpcLootReceived` for nonempty ground/delayed loot | Do not count this alongside processed NPC loot; can double-count the same kill |
| Player loot | `PlayerLootReceived`, with LMS/Soul Wars exclusions | Exclude from Wiki lookup and RNG comparisons; names identify players |
| Reward interfaces | Barrows, CoX, ToB, ToA, Lunar Chest, Colosseum, Miscellania, Trawler, Drift Net | Container contents form an event; empty containers return early; repeated UI opening is guarded for some sources, not a universal guarantee |
| Chat + inventory delta | Chests, salvage, successful pickpockets, clues, reward-cart/pool sources | A recognized message arms one pending inventory snapshot; timeout/order can affect attribution |
| Menu/object/item handling | Shade chests, nests, sacks, packs and other special cases | Some record combined ground/inventory gains; some count removed input items and emit a batch amount |
| Other plugin contribution | `PluginLootReceived` forwarding; built-in Motherlode Mine contributes gem/ore notifications | Contributions do not necessarily represent one eligible attempt; source plugin settings affect coverage |

Implementation evidence: [RL-manager] [RL-npc] [RL-widget] [RL-chat] [RL-batches] [RL-inventory] [RL-motherlode]. Core pickpocket handling suppresses same-tick server NPC loot so the processed notification is not duplicated; failed pickpockets are not successful loot rolls. The denominator for a pickpocket drop rate is normally **successful pickpockets**, not every click. [RL-chat]

NPC metadata is usually an integer NPC ID; special cases use maps, and seasonal worlds use a package-private `NpcMetadata`. ToA interface loot carries `{raidLevel,teamSize,raidDamage}` as an integer array; CoX's inspected interface path does not attach personal points. This is not a complete typed context for every conditional rate. [RL-npc] [RL-widget]

### Dependency and persistence limitations

The processed event is published by an **enabled/running Loot Tracker plugin**. Subscribing to the class does not run its detection when that plugin is disabled. `PluginManager` registers/unregisters active plugins with the event bus. Do not assume `@PluginDependency` automatically enables it: current dependency instantiation requires an exposed public module, and the inspected Loot Tracker does not define one. Use a checked runtime prerequisite and a clear “Enable Loot Tracker to record” status instead of changing the user's setting silently. [RL-manager-plugin] [RL-dependency]

Core `LootTrackerRecord` and `ConfigLoot` aggregate item quantity and source count, not item-present counts. `combine` also skips empty drops for local saved aggregates. Existing aggregate history cannot recover seven occurrences from 22 seeds. Start our occurrence history from plugin activation; any later import must label occurrence counts as unknown. [RL-record] [RL-config] [RL-save]

**Revision-specific forwarding defect:** `onPluginLootReceived` passes `event.getItems()` as metadata and does not forward `amount`/`metadata` from `PluginLootReceived`. The result defaults to amount 1. This is observable source behavior at the audited commit, not a general API guarantee; recheck the release used for implementation. It rules out trusting arbitrary plugin-origin events without source validation. [RL-forward] [RL-plugin-event]

## 6. Large salvage / Cotton seed feasibility

The intended unit is **one Large salvage item sorted**, not one attempt to harvest salvage from a shipwreck. Large salvage item ID is **32853**. `SALVAGE_PATTERN` recognizes the sort message and derives `Large salvage`; `collectInvItems` records the next inventory delta with `amount=1`. The upstream test models one input item replaced by four steel nails and checks the source/type/items. [RL-salvage] [RL-inventory] [RL-salvage-test] [W-large]

Direct primary API responses obtained during research:

- Large salvage article revision **15357637**; Cotton seed article revision **15226139**.
- `dropsline` queried by `page_name='Large salvage'` returns a Cotton seed row with `Rarity='5/2,218'`, `Quantity Low=1`, `Quantity High=5`, `Rolls=1`, `Approx=false`, `Drop type='sailing'`, and empty `Alt Rarity`/`Rarity Notes`.
- The article's inline `DropsLineSkill` also lists `5/2218`, quantity `1-5`, under Seeds and frags. Cotton is not labeled tertiary or collection-log-only. Pre-roll uniques and tertiary Soup are separate table sections with different mechanics/conditions. [W-large] [W-cotton] [W-api]

`Template:DropsLineSkill` invokes `Module:DropsLine`. The module emits the structured `dropsline` row, including rarity, alternative rarity, notes, quantity bounds, roll count, approximation, drop type and variant/source fields. This confirms Bucket data is generated from the Wiki's own drop-rendering implementation, not a separately maintained vendor feed. `Module:RareDropLines` additionally computes expanded nested table rates. [W-skill-template] [W-drop-module] [W-rdt-module]

### Follow-up: what the loot table actually establishes

An exact-rational check of the article's **19 main-table rows** (Shipbuilding, Seeds and frags, Jewellery, Other) gives a sum of **2,218/2,218 = 1**. Cotton contributes five of those weights. Separate pre-roll rows list the amulet at 1/500, facility bottle at approximately 1/1,000, and broken compass at 1/3,000; the amulet row suppresses duplicates when already owned. Consequently, the numerical rows alone cannot establish a universal, condition-independent probability for an arbitrary sort. [W-large]

The Wiki's general Drop table documentation says pre-rolls *typically* precede the main table, which is reached when those rolls fail. It also explains that tertiary drops are independent of the main drop. Neither description proves this specific salvage implementation. Both of these hypotheses fit the inspected numerical rows: (a) the main reward always happens and pre-roll rewards accompany it, giving Cotton `p=5/2218`; (b) successful pre-roll rewards replace the main reward, giving Cotton `p=P(reach main table | context)*5/2218`. The fact that a Bucket row reports `Rolls=1` means one row-level opportunity; it does not specify this parent gate. [W-drop-table] [W-drop-module]

For illustration **only**, treating all three pre-roll rates as sequential independent gates would give survival `(1-1/500)*(1-1/1000)*(1-1/3000)=0.996669666`, and Cotton about `0.00224677562` per sort. This is **not a verified replacement rate**: the bottle rate is approximate, independence/order are unproved, and amulet ownership may change eligibility. Do not ship this multiplier or claim either hypothesis is established. Exact inputs and the weight-sum check are recorded in the evidence JSON.

Jagex's 3 December 2025 Sailing blog directly publishes the Large salvage amulet rate and salvaging-unique rates, but does not publish the Cotton/main-table gating algorithm in the inspected passage. Its old Soup rates describe a different historical mechanic; the current Large salvage article records sorting Soup added in May 2026. A primary source can be historically correct yet inappropriate for today's probability model. [J-sailing-rates] [W-large]

**Revised recommendation:** display `5/2218` as **Published Wiki rate — opening basis awaiting validation**, with quantities and observed occurrences. Keep the numerical example in section 11 as a conditional calculation. Enable exact opening-level expectations/tails only after confirming whether special rewards replace/augment the main reward and capturing any relevant ownership conditions. Live traces can establish whether rewards co-occur, but finite sampling alone cannot prove an exact probability. Prefer an explicit primary mechanics explanation or independently audited mechanics model before declaring the gate resolved.

### Follow-up: event reliability from the actual callback lifecycle

The salvage branch does not parse the reward list or removed salvage count. It arms one inventory callback. `onInvChange` stores **one shared** inventory ID, multiset snapshot, callback and timeout; another trigger replaces those fields. `onItemContainerChanged` takes the next matching inventory delta, calls the callback, then resets it. `collectInvItems` ignores the ground and removed-item collections, and `addLoot` supplies default `amount=1`. The timeout is ten server ticks (approximately six seconds). The original salvage introduction and subsequent refactor both use inventory-only collection; the latter is a refactor, not an overflow/denominator fix. [RL-salvage] [RL-arm] [RL-collect] [RL-inventory] [RL-timeout] [RL-timeout-constant] [RL-salvage-intro] [RL-salvage-refactor]

| Scenario | What the inspected code establishes | Validation consequence |
|---|---|---|
| Normal sort, chat before corresponding inventory update | Positive multiset delta becomes one notification | Existing upstream test covers one salvage → four nails only |
| Cotton already in inventory | A later increase of that stack is detected by multiset quantity difference | Validate real event ordering and combined-container updates |
| Two sort messages before one inventory update | Second trigger overwrites the single pending callback; no queue or consumed-input counter | Combined rewards could be represented as one attempt; actual server sequencing is unverified |
| Reward update before sort message | New snapshot may already contain the reward | Static code does not guarantee recovery of that reward |
| Unrelated inventory change while armed | First matching container update consumes callback, without verifying one salvage was sorted | Test item dropping/picking up, alching, interruptions and mixed actions |
| Ground-only/overflow outcome | Ground items are computed by the container handler but discarded by the salvage collector | Quantity/presence could be incomplete even if a notification is posted |
| No matching inventory update | Callback expires; no salvage event is synthesized | Cannot treat a missing notification as a known empty opening |
| Cargo/boat sorting | Trigger is the same chat expression; snapshot is the player's inventory, not cargo | Cargo removal or Sailing XP cannot silently become an independent count without validation |

These are demonstrated **structural limitations and conditional counterexamples**, not claims that each event ordering occurs during normal gameplay. No live game client/account was exercised. Wiki pages verify sorting at both port stations and boat facilities, and crew/cargo workflows; they do not document RuneLite callback ordering. [W-station] [W-boatstation] [W-salvaging]

### Required empirical validation and narrow-adapter decision

Record a diagnostic trace of chat type/text/time, client/server tick, inventory/cargo deltas where available, relevant ground spawns, source metadata and emitted `LootReceived` amount/items. Keep diagnostic logs local and bounded. Reconcile manually confirmed sorts with events, not only quantities. Use ordinary public events and APIs, without packet inspection or server code claims.

Test port and boat stations; inventory-only and cargo-supplied salvage; existing Cotton stacks; full inventory and any allowed overflow; rapid continuous sorting; interruption with unrelated inventory actions; another event-triggering reward while a callback is pending; relog/hop/disable; and rare main/pre-roll/tertiary reward combinations. For each scenario record completed sorts, notifications, represented amount, Cotton-present **openings**, Cotton quantity, and unmatched/ambiguous records. Require exact reconciliation of a controlled trace before advertising trustworthy attempts; mark conditions that cannot be exercised as unvalidated. Rare-roll co-occurrence may require observed diagnostic traces rather than an achievable short test session.

Prefer the core processed stream when the gate passes. If it fails, propose an upstream narrow salvage fix or a source-specific adapter that correlates completion messages with rewards and input consumption. A boat sort may consume cargo rather than an inventory item, so inventory removal is not automatically a complete denominator. Do not double-count an adapter and core events, merge arbitrary identical rewards, or promote Salvage Sack's message regex to guaranteed one-opening semantics without the same tests.

## 7. Problems and edge cases

| Case | What can be known | Required treatment |
|---|---|---|
| One event, item stack 4 | Quantity 4; item present once | Increment quantity by 4 and occurrence by 1; do not infer four rolls |
| Same canonical item in several stacks | Sum of quantities; still present once | Group before incrementing occurrences |
| Event `amount>1` | Number of represented attempts may be known; per-attempt successes usually are not | Preserve batch quantity and item-present notification separately; exclude batch attempts from exact occurrence-rate subset |
| No item at all | A published empty single-attempt event can count; missing event cannot | Require denominator coverage per source; no death=attempt assumption |
| Two legitimate identical drops in one tick | They may be two real events | Do not deduplicate processed events by source/items/tick alone |
| Item listed at several quantities/rates | Could be mutually exclusive alternatives, conditions, or independent tables | Resolve table semantics before summing; do not select aggregate probability based on the observed reward |
| Guaranteed item | `p=1` if guaranteed in each eligible attempt | No meaningful rarity tail when all observed; absence flags detection/eligibility problems |
| Nested/RDT/shared unique table | Rate may already include access/table weights | Preserve provenance; multiply conditional probabilities only when rates are not already expanded |
| Tertiary drops | May be separate independent opportunities, sometimes eligibility-limited | Keep each item's event probability; do not force probabilities across all items to sum to 1 |
| Slayer/quest/equipment conditions | May change probability while source name stays the same | Capture known context at event time; unknown context means no exact probability comparison |
| NPC variants | Same name can refer to several tables | Keep NPC ID/variant in source identity; name-only history is uncertain |
| Clues and raids | Rolls, points, eligible uniques, team size and reward rules can vary | Raw tracking allowed; no fixed `1/N` tail unless event-level probability is justified |
| Hidden shards, pity, dupe protection | Trials can be state-dependent/correlated | Specialized model needed; outside MVP |
| Rates change / item introduced later | Current rate does not describe all history | Preserve data version/segments; never silently apply new rates to old lifetime data |
| Leagues, DMM, beta, tournaments | Modifiers/world rules differ | No standard-rate comparison; MVP excludes these worlds from eligible statistics |
| Collection log / old Loot Tracker import | Quantities/KC may survive, event presence does not | Never manufacture occurrence counts from aggregate quantity |

### Follow-up: empty kills and split boss rewards

Empty Loot Tracker's `isLikelyOurKill` is a recent-positive-own-hitsplat test, not verified loot entitlement. After a configurable delay it commits a pending NPC death as empty; game-state transitions clear transient attribution. Server loot lacks an NPC index in its handler, so `matchPendingKill` falls back to the most recent uncounted death with the same name. Same-tick loot fingerprints may suppress notifications when an index is absent. These choices can confuse contested kills, multiple identical NPCs, delayed drops and shared boss rewards; no inspected test/live trace establishes universal attribution accuracy. Its counter separation is reusable, its denominator must be validated source by source. [EMPTY-credit] [EMPTY-match] [EMPTY-delay]

RuneLite issue **19307** reports Phosani's Nightmare uniques and elite clue boxes being counted as extra kills; proposed PR **20336** remained open/unmerged when checked. Those reports are evidence of a detection risk, not proof that every boss/current client is affected. The inspected `onServerNpcLoot` forwards each notification without a kill-attempt ID, so `amount=1` alone is insufficient to certify one kill. Boss adapters need to correlate known completion/kill boundaries and merge later rewards into that attempt, incrementing each item's presence once while adding all quantities. Use public, source-specific signals after verification; do not use a generic time-window/fingerprint merge. [RL-npc] [RL-boss-issue] [RL-boss-pr]

## 8. Recommended drop-rate data architecture

### Structured Wiki provider with a generated offline fallback

1. **Primary input:** Wiki `action=bucket` JSON, querying supported owner pages in `dropsline`; join `infobox_monster`/item metadata where IDs are needed. This API was exercised successfully for Large salvage. It exposes more useful semantics than simple HTML rarity cells, and avoids writing a template evaluator. API extension documentation is available at [Bucket documentation][W-bucket] and [API help][W-api-help]. It is not a contractual, immutable schema.
2. **Initial coverage registry:** explicitly register `EVENT:Large salvage → Large salvage`, with Cotton ID 31545 and validated opening-level semantics. Automatically retrieve its published rows/rates; manually maintain only source aliases and eligibility/model decisions, not the numerical drop table. Add other sources after testing their denominator and mechanics.
3. **Bundled fallback:** a development-time generator uses the same row parser as runtime, stores supported rate records and attribution/provenance, and checks them into resources. A scheduled CI job can regenerate/diff supported pages and prepare a reviewable data change; bundled updates still ship through a Hub revision update. Do not run data generation as a custom standard-build step.
4. **Optional live refresh:** default off, with a configuration disclosure explaining that the Wiki receives IP plus game-content source/item queries, and no RSN/account/loot history. Fetch only explicitly resolved game-content sources encountered/selected; never send PLAYER names. One request at a time, at least one second between requests, coalesced lookups, a bounded queue and response size, timeouts, and retry/backoff for transient failures/429s. Cancel callbacks on plugin stop/profile changes.
5. **Cache:** versioned, bounded local data cache; seven-day freshness target, last-success timestamp and provenance hash. Refresh on demand when stale or on explicit user refresh, not per drop. Offline/errors keep last-good/bundled data with its date visible. Malformed or incompatible rows disable derived statistics for that source/item rather than becoming zero probabilities.
6. **Unknown/complex rows:** retain raw rarity and conditions for explanation/Wiki links; only produce numeric event-level probabilities for validated models. Keep approximation flags. Do not turn “Rare”, “Varies”, “Never under this condition”, multiple alternatives, or template text into an invented probability.

This hybrid avoids maintaining a separate hosted service and makes numerical rate updates mostly automatic. A GitHub-hosted generated dataset, as used by Clog Drop Rates, is a viable later distribution option if coverage grows, but adds another service, publication pipeline, and disclosure. Direct Wiki networking is **not inherently forbidden** by current Hub guidance; reviewed plugins already use it. Exact permission/disclosure review remains a submission-time question. [CLOG-runtime] [H-information]

### All-source coverage and model registry

The provider must enumerate **all rows for a resolved source**, including common, guaranteed and not-yet-obtained items. Do not inherit Droppy's collection-log filter or Drop Rate's denominator-below-50/source-type filters. Source/item search and item → source lookup should expose the wider catalogue, including rate-only sources whose tracking adapters are not ready. An unresolved table remains visible with a reason; no row is silently dropped merely because it cannot supply a binomial probability.

Separate a generic rate catalogue from a small, extensible **attempt/model registry**. The catalogue retains original source/item IDs and names, owner page, variant/section, raw fraction, row-level rolls, quantities, approximation and full conditions. The registry declares attempt unit, detector/correlation strategy, empty-outcome and batch behavior, eligibility context, rate basis and validated probability model. A rate may be per roll, per kill, per opening, per point or conditional on a table gate. Store that basis explicitly. One item's probability model can be supported while another item's model from the same source remains conditional or stateful.

| Source family | Intended item coverage | Model/detection work required |
|---|---|---|
| Salvage | Every salvage tier and loot-table item | Sort boundary, main/pre-roll/tertiary gates, ownership and cargo/overflow |
| Ordinary monsters | All common, rare, guaranteed and tertiary items | NPC variants, player loot entitlement and empty/omitted outcomes |
| Bosses | Main-table, unique, pet and clue items | Per-kill boundaries, split rewards, known multiple rolls and encounter conditions |
| Pickpocketing / skilling | All probabilistic output items | Successful action vs failed action, level/equipment/quest context and containers |
| Caskets / crates / sacks / nests | All contained item tables | Single/batched openings, roll counts, replacement/dupe protection |
| Clues / raids / minigames | All reward items | Reward rolls, tier/points/team/personal contribution and stateful mechanics |
| Other rate-bearing rewards | Searchable rate rows and source identity | A new source-family adapter/model where existing contracts are insufficient |

An all-item catalogue is the target; universal exact luck cannot be promised merely because an item has a published rate. Show independent statuses such as **rate available**, **tracking available**, **attempts verified**, **conditional**, **approximate**, or **tracking not yet supported**. This keeps the product broad without turning incomplete evidence into statistics.

### Follow-up: representative Wiki schema checks

Additional successful primary Bucket queries returned **69 Goblin rows**, **75 Zulrah rows**, and **90 Vorkath rows**. Goblin rows preserve `#Drop table 1`/`#Drop table 2`; flattening them by owner/item would lose variant distinctions. Zulrah fang rows report `1/1024` with `Rolls=2`, while guaranteed scales report `Always` with `Rolls=1`. Vorkath distinguishes `#Post-quest`, with two-roll main rows and single-roll guaranteed rows. These are verified data examples, not independent proof of each game's random implementation. Preserve rolls separately and do not convert all rows using one source-wide multiplier. For two independent fang opportunities, **if confirmed**, presence would be `1-(1-1/1024)^2=2047/1048576`, not exactly `2/1024`. [W-goblin-bucket] [W-zulrah-bucket] [W-vorkath-bucket]

A query for owner `Supply crate` returned **zero rows**. That result alone cannot tell whether a source was renamed, data moved, an alias is wrong, or coverage is absent. It is not a zero drop probability. Resolve the actual owner through controlled aliases/primary page evidence and show an unavailable-data status until resolved. Response queries/hashes are retained in the evidence file. [W-crate-bucket]

### Minimum provider interface and provenance

Conceptual `RateProvider.resolve(sourceIdentity,itemIdentity,context)` returns either a supported opening-level probability or an explicit status such as unknown, ambiguous, conditional-context-missing, or unsupported-mechanics. A result includes raw rational rate, roll count/basis, conditions, quantity bounds, approximation, owner page/variant, fetched date, parser schema version and content hash. Keep observations independent of the provider.

`page_name_sub`, item name, notes and `Rolls` must survive parsing. The `drop_json` field is itself encoded JSON in the tested response. Item IDs do not appear in every drop row: use supported item mappings, item-infobox data, and canonical IDs rather than assuming globally unique display names. Correctly distinguish item `Rare drop table` rows from already-expanded source rates. A raw Wiki row is useful data, **not a universal probability model**.

## 9. Recommended RuneLite event architecture

`enabled RuneLite Loot Tracker → LootReceived → synchronous immutable observation → source eligibility adapter → independent statistics/persistence → Swing panel`

- Use **`LootReceived` as the primary loot-observation stream**. Source-specific completion/correlation signals may establish or repair attempt boundaries after validation. Do not also count `ServerNpcLoot`/`NpcLootReceived`, and do not repost observations into core Loot Tracker.
- Copy/normalize positive quantities and source/context synchronously. Preserve original IDs alongside explicitly approved canonical item IDs (including noted/unnoted equivalence where applicable). Increment event presence once per canonical item.
- Preserve both `notificationCount` and `reportedAmount=sum(amount)` (a client-reported amount, not automatically verified attempts); only observations certified by a source-specific boundary/eligibility contract supply the exact statistical subset; `amount=1` alone is not certification. For a batch, keep quantity and presence-in-batch but do not guess successful opening counts. The eligible denominator must exclude the same batches excluded from the numerator.
- For validated empty single-attempt notifications, increment eligible attempts and no item occurrences. For a source that can omit attempts entirely, display only observed-notification statistics until an independent reliable denominator exists.
- Show the Loot Tracker prerequisite state. Keep our own history and configuration; resetting core Loot Tracker must not reset ours, and vice versa. No automatic enabling, reflection into private core internals, or dependency annotation used merely to force events.
- Retain provenance/source quality flags. Unknown metadata, unsupported worlds or lost context must not default to normal-mode rates. NPCs can use public integer/map metadata; seasonal package-private metadata need not be reflected into for MVP.
- Source-specific adapters are an extension point, not an initial reimplementation of all detection. Salvage has potential inventory/callback gaps documented in section 6; add an adapter only when validated gameplay traces demonstrate the need. Avoid generic fingerprint deduplication: the processed event has no durable identity and two identical genuine rewards must survive.

## 10. Proposed persistence model

This is a recommended model to review, not a frozen implementation schema.

| Concept | Proposed contents |
|---|---|
| Account scope | RuneScape profile key; separate game mode/world eligibility; no display-name-based file paths |
| Source identity | Loot type, original source name, resolved owner/variant and NPC ID where available; mapping version |
| Observation coverage | First/last seen, notification count, represented amount, verified attempt count, adapter/model version, correlation quality, excluded batch/unknown-context counts |
| Item observations | Item ID, original ID variants, total quantity, item-present notification count, eligible item-present attempt count, eligible quantity, last occurrence and current observed dry streak |
| Rate segment | Source/item/context, published row fraction and separately derived event probability, gate/roll basis, mechanics confidence, model/data/schema hash, observed segment boundaries, eligible attempts and successes |
| Session | Start time plus the same counters, reset independently; lifetime persists |
| Provenance | Source URL/page revision when available, fetched timestamp, approximation/conditions and support status |

Use 64-bit counters, versioned local JSON, and a plugin-owned directory beneath `RuneLite.RUNELITE_DIR`, namespaced by a hashed profile key. A serialized background writer installs complete snapshots via temporary file and atomic move where available; preserve last-good recovery data. Capture profile key/generation with each observation so pending writes cannot cross accounts. Keep small UI configuration in ConfigManager rather than storing an unbounded event log there.

Keep complete aggregates plus rate/context segments; a bounded diagnostic recent-event history is optional. Segmentation allows rate updates without rewriting past observations. The default probability detail uses a known homogeneous segment; lifetime quantity/occurrences stay available across segments. Combined expected count can later be `sum(n_j p_j)`, but a pooled exact tail needs a Poisson-binomial/convolution model. Unknown-rate periods remain raw observations rather than silently gaining an asserted historical rate.

A dry streak must be tracked from known eligible attempts since the last item occurrence; `lifetime attempts - lifetime occurrences` is not the streak. Label the streak as observed since tracking began if its start is unknown. Batches/context gaps break an exact streak; do not interpolate through them.

Do not import core aggregate history into exact occurrence statistics. If historical import is later offered, store quantity/source-count baselines with unknown occurrence counts and show their coverage separately.

## 11. Proposed statistics and independent numerical checks

Let `n` be **eligible individual attempts**, `k` the number containing this item, `Q` total quantity in those attempts, and `p` the item-present probability per eligible attempt. For independent trials with constant `p`:

```text
X ~ Binomial(n, p)
expected occurrences = n*p
observed occurrence rate = k/n
observed "1 in" = n/k, when k>0
actual / expected = k/(n*p), when n*p>0
above-rate inclusive tail = P(X >= k) = 1 - CDF(k-1)
below-rate inclusive tail = P(X <= k) = CDF(k)
zero-occurrence probability over n attempts = (1-p)^n
```

Choose the explanatory upper tail when above expected and lower tail when below expected; name the event explicitly. These descriptive tails are not the probability that a player is lucky, that the Wiki is wrong, or that the next attempt will succeed. Choosing the direction after observation does not create a calibrated two-sided significance test. Multiple-item browsing/stopping also invalidates a naive “significant luck” interpretation.

### The requested example

**Interpretation after follow-up:** the arithmetic below is independently verified for the stated probability input. The `5/2218` row is a verified published Wiki fraction, but calling it unconditional per-opening probability requires the unresolved salvage mechanics gate in section 6. These numbers are not yet a validated claim about every real Large salvage opening.

Seven item-present attempts give stacks `5,2,4,3,1,4,3`, so **Q=22, k=7, n=1,000**. Observed rate is `7/1000`, or **1 in 142.8571**. All remaining 993 attempts still belong in the denominator.

| Expected-rate input | Expected occurrences | Actual/expected | P(X ≥ 7) | P(X=0) over 1,000 attempts |
|---|---:|---:|---:|---:|
| Illustrative `1/443` | 2.2573363431 | 3.1010× | 0.00843115059153 = **0.843115%** | 10.436218% |
| Published Wiki fraction `5/2218` (opening basis unresolved) | 2.2542831380 | 3.1052× | 0.00837305118144 = **0.837305%** | 10.468202% |

Detail text to enable only after the opening-probability model is validated: **“7 occurrences from 1,000 tracked openings; 22 seeds. 3.11× expected. Chance of 7 or more occurrences in 1,000 openings at the published rate: 0.8373%.”** This calculation is conditional on the rate/eligibility/independence model.

Independent checks used an 80-digit Decimal PMF recurrence summed directly over `k..n`, and a separate floating-point log-gamma binomial sum. Results agreed within `1e-12`; the Decimal PMFs summed to 1 within `1e-70`. Exact inputs/results are in the evidence file. No normal/Poisson approximation was used for the reported tails.

### Roll counts, quantity and conditional probabilities

- With `r` independent identical opportunities of per-roll probability `q` **within one opening**, the item-present probability is `p=1-(1-q)^r`, not `r*q`. Expected successful rolls are `r*q`, a different quantity. With differing independent opportunities use `1-product(1-q_j)`. Do not apply this conversion when the Wiki rate already describes the whole opening.
- If `r` varies, use actual event contexts or a justified distribution: `p=1-E[(1-q)^R]`. Replacing `R` with its average is generally not exact. If openings have differing probabilities, their total event-presence count is Poisson-binomial, not ordinary binomial with an averaged probability.
- Mutually exclusive rows for the same item may sum only after confirming they describe disjoint outcomes of the same eligible attempt. Independent tables need a union calculation; gated tables need conditional weighting. Quantity variants alone do not tell us which situation applies.
- Do not calculate expected quantity from a range alone. `1–5` does not prove a uniform stack distribution. If a conditional mean stack size is established separately, `E[Q]=n*p*E[S|item present]` for a single-stack model; compound/multiple-roll quantities need more detail.

### UI and numerical behavior

- **Chosen list interaction (3 October 2026):** use the Clean list design with inline item expansion. Clicking an item reveals its detailed counters and probability statistics directly beneath that row; clicking it again collapses the details. Open one item at a time to retain density. Remove the separate bottom detail card. Keep explanatory text in metric tooltips and show model-unavailable states alongside the stats.
- **Rate-first design direction (3 October 2026):** show the rarity-colored published rate beneath each item title, with total quantity right-aligned as `x300`. Source headers contain source name, tracked source count and optional total GE value; tooltips clarify whether that count is validated eligible attempts or only recorded outcomes. Present positive numeric rates as reciprocal `1/x`, preserving the exact fraction in provenance/tooltips and using one decimal only when necessary (for example, `5/2218 → 1/443.6`). A guaranteed rate may be displayed as `1/1` with a Guaranteed tooltip; unknown/conditional rates require explicit labels rather than invented denominators. Keep precision configuration out of the initial settings.
- **Driest within source:** this is an optional sort of the existing source view, not a separate product mode. Include all known rate-table rows, including never-observed items, when chosen; otherwise default to collected items. Rank validated independent, constant-probability event models by decreasing `-d*log1p(-p)` (equivalently increasing zero-occurrence probability), where `d` counts eligible attempts since last occurrence, or since tracking began for never-observed items. This compares dryness relative to rarity rather than raw streak length or lifetime A/E. Keep source order stable, place unrankable rows after ranked rows, and explain unavailable models. Include conditional/unknown rows without an unjustified rank. A full source table improves this sort but does not imply universal tracking support. Demo prototypes use shortened tables and illustrative boss models.
- **Design addition (3 October 2026):** include actual/expected occurrence ratio, optional luck colors, and dry analysis in both dense list and compact grid views. Luck colors encode above/below expected occurrence counts and remain distinct from Wiki rarity colors; they do not imply statistical significance. Use a neutral band configurable around 1×, with explicit numeric ratios and accessible labels. Dry analysis includes observed eligible attempts since the last occurrence (or since tracking began if never observed), and `P(no occurrence in d eligible attempts)=(1-p)^d` only for a validated independent, constant-probability event model. Put metric definitions, rate basis, model limitations and probability explanations in tooltips; retain counts and unavailable-state indicators visibly. Quantity, item-present events and underlying successful rolls remain separate. These are requested product features, not implemented plugin behavior.
- Source cards show tracked attempt coverage and eligible/excluded counts. Rows show Quantity, Occurrences, Published rate and rate basis. Show Observed per-attempt rate, Expected occurrences and Actual/expected only for a validated attempt/probability model; otherwise explain the unavailable field. Item detail adds the inclusive tail, rate date/source, conditions and observed dry streak.
- To inspect zero-occurrence probability, users need to select a supported table item **even if never received**; include supported undropped items or a pin/search control. A received-items-only panel cannot answer “how dry am I for an item I have never seen?”
- `n=0`: no observed rate/ratio/tail; show no eligible attempts. `k=0,n>0`: observed rate 0 and “not observed,” not a finite `1/N`. `p=0` with a positive observation and `p=1` with a missing expected item are model/detection mismatches. Reject `k>n`, invalid probabilities, and malformed counts.
- Discrete percentiles have ties. Prefer explicit tail wording; if shown, label both `P(X<k)` and `P(X≤k)` or their interval. At `5/2218,k=7,n=1000`, those are **99.162695%** and **99.773949%**. Do not present an unlabeled single “luck percentage.”
- Compute a survival function directly or use stable log-space/incomplete-beta routines; `1-CDF` can lose tiny tails through cancellation. Use `log1p(-p)` for dryness and `-expm1(r*log1p(-q))` for roll-to-event conversion. A production implementation needs boundary and large-`n` tests; the small research recurrence is an independent reference, not a proposed unbounded per-frame algorithm.
- Select a numerical implementation after checking RuneLite's resolved dependencies. Collection Log Luck's Commons Math usage is a precedent, not proof it is already transitive in the current template. Avoid adding a library until that check is made; a new dependency requires Hub verification.

## 12. Current Plugin Hub considerations

The official [Hub README][H-readme], [review policy][H-review], [network-warning guidance][H-information], and [rejected features list][H-rejected] were checked. These public pages do not enumerate every reviewer-specific permission; current acceptance of another plugin is not blanket approval for ours.

- **Submission/build:** use the official plugin template/tooling, a public repository, correct `runelite-plugin.properties`, and a descriptor containing repository plus a full commit SHA. Submit/update through a Hub PR and pass its checks. The README currently recommends Java 11 and `latest.release` RuneLite dependency configuration; use the current template's actual build settings when scaffolding. Standard build replaces the project's build/settings files, so custom data generation belongs outside that build. [H-readme]
- **Network:** third-party communication requires disclosure at installation or at the enabling configuration setting, describing sent data. Direct Wiki/API and GitHub JSON fetches are present in audited Hub plugins; there is no documented blanket prohibition on live Wiki rates. Make Wiki refresh opt-in and avoid player identifiers, arbitrary configured hosts, unbounded requests, or network work on client/Swing threads. Whether our exact disclosure and request behavior satisfy reviewers is unverified. [H-information] [LTE-service] [CLOG-runtime]
- **Dependencies/security:** non-transitive dependencies require cryptographic verification in the Hub verification template and maintainer review. Reflection/native code are restricted. Use injected RuneLite Gson/OkHttp and normal APIs; no dynamic code download/evaluation, native helper, or reflective access to core internals. A Bucket query is a fixed Wiki read query, not downloaded executable plugin code. [H-readme] [H-review]
- **Resources/storage:** bundled resources are inside the deployed JAR; read with `getResourceAsStream`. Local persistence under a plugin-owned RuneLite directory is supported by inspected implementations, but no comprehensive official storage quota/guarantee was found in these policy pages. Keep paths profile-safe and storage bounded; do not modify other plugins' history. [H-readme] [LTE-disk]
- **Licensing:** all audited plugin code licenses are BSD 2-Clause; preserve notices if reusing code. OSRS Wiki currently identifies **CC BY-NC-SA 3.0**, with additional RuneScape-specific terms at Weird Gloop's licensing page. Do not label copied/generated Wiki content BSD merely because plugin code is BSD. Keep data attribution, source/revision links, applicable notices and a separate data-license statement. Review the exact redistribution obligations for the generated dataset before publishing; this source audit is not a legal determination about whether particular facts/collections are copyrightable. [W-license]
- **Allowed behavior:** passive observation, local history and explanatory statistics are the intended behavior. No automation, game actions, hidden encounter-state exploitation or packet manipulation is needed. The review policy focuses on security/rule compliance and explicitly does not verify statistical correctness. Our probability/detection validation remains our responsibility. [H-review] [H-rejected]

**Early finding:** live Wiki access itself is not the blocker. Ambiguous probability semantics, reliable attempt boundaries, version compatibility, and correct network/data licensing disclosures are the work to resolve before submission.

## 13. Recommended MVP and acceptance tests

### MVP and broader delivery scope

The user-confirmed product goal is **all items with a drop rate across all source families**. A staged rollout is a validation strategy, not permission to deliver a permanently salvage-only or collection-log-only plugin.

Build a separate Loot Tracker-style sidebar panel using processed events, local profile-specific persistence and session/lifetime views. Count raw quantity and item-present notifications for non-player supported source categories. Use **Large salvage / Cotton seed on normal worlds** as the first complete counter/probability validation case, after both the event and roll-mechanics gates. Include general source and item identity from the start. Use generated Wiki fallback data plus optional live refresh; show unsupported/ambiguous rate status for other rows.

Include ordinary-monster and boss sources in the planned first broad release, with validated representative attempt/model contracts; extend the same catalogue to other source families incrementally. Add exact NPC statistics only after confirming empty-outcome coverage, variants and split-reward behavior. Do not promise NPC “kills” from observed nonempty loot notifications alone. Clues, raids, batch rewards, stateful uniques and detailed equipment/Slayer rules remain in the product scope; their exact statistics require dedicated later validation/models. Historical imports, account uploads and a hosted rate service remain optional and unnecessary for the first release. Raw observations can remain visible where their meaning is clearly labeled.

### Validation scenarios for implementation

1. **Separate counters:** 1,000 individual attempts with the seven stacks above → attempts 1,000, quantity 22, occurrences 7. Duplicate item stacks inside one attempt still add one occurrence.
2. **Zero/empty:** empty eligible events increment attempts; an undropped selected item has zero occurrences and valid dry probability. Missing notifications must not be represented as tracked attempts.
3. **Batch:** `amount=5` with Cotton x8 → represented attempts 5, one item-present batch notification, quantity 8; exact successful openings unknown. Exclude those five attempts and their quantity/presence from individual-attempt comparisons.
4. **Legitimate repetition and mutation:** two identical real events survive; post-callback clearing of the original items list cannot alter our saved observation. UI ignore/reset settings do not silently erase our own data.
5. **Mapping/context:** exact salvage name/ID; noted/unnoted item policy; same-named NPC variants; missing metadata; normal versus seasonal worlds. Unresolved source/condition produces no invented numeric rate.
6. **Probability:** independently cross-check upper/lower inclusive tails; `k=0`, `k=n`, `p=0`, `p=1`, `n=0`; invalid `k>n`; very small probabilities/large counts; `1-(1-q)^r` versus incorrect `r*q`; varying-rate inputs rejected by the binomial model.
7. **Lifecycle/storage:** plugin disable/re-enable, client restart, logout/profile switch during pending writes, session reset, corrupted snapshot recovery, batch counters, and rate change segments. No cross-profile writes or manufactured imported occurrences.
8. **Data/network:** bundled-only startup, opt-in refresh, API error/schema change, malformed rarity, offline/429/timeouts, response limits, coalescing, TTL and cancellation. Last-good provenance remains visible and observation tracking continues.
9. **Live salvage:** reconcile input sorts and emitted observations across the conditions in section 6, including the initial Cotton stack case. Static source/test coverage is not a substitute for this check.
10. **Broad coverage:** common/rare/guaranteed and undropped items appear; salvage, monster and boss sources use distinct attempt units; Goblin variants survive, Zulrah/Vorkath rolls are preserved, and an empty/renamed-owner query produces unknown rather than `p=0`.
11. **Split rewards/empty kills:** one boss completion with main loot plus a later unique/clue gives one attempt and the correct union of item presences; two identical legitimate kills survive. Contested kills or unidentified rewards remain unverified.
12. **Mechanics gate:** conditional table rates cannot become unconditional opening probabilities; ownership/context changes create a new segment; approximate inputs stay labeled.
13. **Hub readiness:** compile/run against the template's resolved current release, run meaningful unit/integration checks and Hub build verification, review licenses/disclosures, then submit only the reviewed design.

### Research completion and remaining gates

All six requested plugins have implementation traces, pinned revisions and verified licenses; eight additional relevant implementations were inspected, including the follow-up Salvage Sack audit. RuneLite publication paths, dependency behavior, aggregate-history loss, batching and salvage detection were traced. The primary Cotton rate and numerical example were independently checked. Recommendations cover the requested thirteen topics.

Remaining gates are **live gameplay denominator validation and salvage pre-roll/main-table semantics**, compatibility with the release used to scaffold, production numerical/dependency selection, data-redistribution attribution, and the actual Hub review. No plugin was scaffolded or implemented. Architecture review should precede that phase.

### Follow-up research status

The follow-up completed static callback/denominator analysis, primary table-weight and representative schema checks, dedicated Salvage Sack inspection, Empty Loot Tracker attribution analysis, and existing-plugin integration assessment. No live gameplay validation was performed, and the salvage gating mechanic remains unresolved rather than guessed.

For integration, retain a standalone general tracker with its own observation/persistence/model layers and reuse core events. Loot Tracker Extended is a useful panel/cache precedent, but its aggregate service does not supply all required occurrence/attempt information; direct dependence on its private implementation would create version coupling. A narrowly scoped upstream core detection correction is preferable if a confirmed gap affects everyone. Shared, attributed data-generation/parser work with existing maintainers is a possible later collaboration; no contact, fork, API change or contribution was made during this phase.

## Source index

Links below are generated from inspected local source files; line ranges were checked against the pinned archives. Wiki article/module links specify revisions where available. Hub policy Wiki pages are mutable and should be rechecked before submission.


### Inspected implementation anchors

- [D-fetch] — `src/main/java/com/droppy/WikiDropFetcher.java`, lines 21–167.
- [D-build] — `build.gradle`, lines 43–48.
- [D-generate] — `src/test/java/com/droppy/WikiScraper.java`, lines 198–352.
- [D-text] — `src/test/java/com/droppy/WikiScraper.java`, lines 372–416.
- [D-parse] — `src/test/java/com/droppy/WikiScraper.java`, lines 572–717.
- [D-ids] — `src/test/java/com/droppy/WikiScraper.java`, lines 769–827.
- [D-dedupe] — `src/test/java/com/droppy/WikiScraper.java`, lines 443–455.
- [D-service] — `src/main/java/com/droppy/DroppyApiClient.java`, lines 22–72.
- [D-config] — `src/main/java/com/droppy/DroppyConfig.java`, lines 52–64.
- [D-events] — `src/main/java/com/droppy/DroppyPlugin.java`, lines 219–255.
- [DR-load] — `src/main/java/com/pluckss/droprate/DropRatePlugin.java`, lines 230–250.
- [DR-match] — `src/main/java/com/pluckss/droprate/DropRatePlugin.java`, lines 1485–1567.
- [DR-base] — `src/main/java/com/pluckss/droprate/DropRatePlugin.java`, lines 1887–1944.
- [DR-context] — `src/main/java/com/pluckss/droprate/DropRatePlugin.java`, lines 1485–1567.
- [DR-quantity] — `src/main/java/com/pluckss/droprate/DropRatePlugin.java`, lines 1730–1761.
- [DR-crawler] — `tools/crawl_bucket.py`, lines 38–169.
- [DR-primary] — `tools/crawl_bucket.py`, lines 250–291.
- [DR-rdt] — `tools/crawl_bucket.py`, lines 298–393.
- [DR-versions] — `tools/crawl_bucket.py`, lines 451–529.
- [DR-update] — `.github/workflows/drop-data.yml`, lines 1–69.
- [DD-store] — `src/main/java/com/dropratedisplay/DropRateDataStore.java`, lines 29–156.
- [DD-source] — `src/main/java/com/dropratedisplay/SourceDropTable.java`, lines 18–105.
- [DD-ground] — `src/main/java/com/dropratedisplay/DropRateDisplayPlugin.java`, lines 184–226.
- [DD-generator] — `scripts/generate_drop_rates.py`, lines 35–301.
- [DD-data] — `src/main/resources/drop_rates.json`, lines 1–1.
- [DD-entry] — `src/main/java/com/dropratedisplay/DropRateEntry.java`, lines 41–103.
- [LTE-service] — `src/main/java/com/searchableloottracker/wiki/WikiDropRateService.java`, lines 35–175.
- [LTE-http] — `src/main/java/com/searchableloottracker/wiki/WikiDropRateService.java`, lines 446–530.
- [LTE-url] — `src/main/java/com/searchableloottracker/wiki/WikiDropRateService.java`, lines 565–574.
- [LTE-parser] — `src/main/java/com/searchableloottracker/wiki/WikiDropTableParser.java`, lines 46–106.
- [LTE-resolver] — `src/main/java/com/searchableloottracker/wiki/WikiSourceResolver.java`, lines 23–85.
- [LTE-overrides] — `src/main/resources/wiki-source-overrides.json`, lines 1–143.
- [LTE-tooltip] — `src/main/java/com/searchableloottracker/wiki/WikiDropTable.java`, lines 40–75.
- [LTE-disk] — `src/main/java/com/searchableloottracker/wiki/WikiDropTableDiskCache.java`, lines 20–177.
- [LTE-events] — `src/main/java/com/searchableloottracker/SearchableLootTrackerPlugin.java`, lines 82–129.
- [LTE-count] — `src/main/java/com/searchableloottracker/LootDataService.java`, lines 177–235.
- [LTE-import] — `src/main/java/com/searchableloottracker/LootDataService.java`, lines 135–175.
- [DF-fetch] — `src/main/java/com/mrmain21/dropfinder/WikiDropClient.java`, lines 57–179.
- [DF-parse] — `src/main/java/com/mrmain21/dropfinder/WikiDropClient.java`, lines 344–435.
- [CL-registry] — `src/main/java/com/peanubnutter/collectionlogluck/luck/LogItemInfo.java`, lines 1–210.
- [CL-sources] — `src/main/java/com/peanubnutter/collectionlogluck/luck/LogItemSourceInfo.java`, lines 4–114.
- [CL-trials] — `src/main/java/com/peanubnutter/collectionlogluck/luck/drop/AbstractDrop.java`, lines 81–142.
- [CL-api] — `src/main/java/com/peanubnutter/collectionlogluck/util/CollectionLogLuckApiClient.java`, lines 17–60.
- [CL-fetch] — `src/main/java/com/peanubnutter/collectionlogluck/CollectionLogLuckPlugin.java`, lines 392–450.
- [CL-binomial] — `src/main/java/com/peanubnutter/collectionlogluck/luck/drop/BinomialDrop.java`, lines 12–72.
- [LL-fetch] — `src/main/java/com/lootlookup/osrswiki/WikiScraper.java`, lines 21–175.
- [LL-map] — `src/main/java/com/lootlookup/osrswiki/WikiScraper.java`, lines 410–485.
- [LL-row] — `src/main/java/com/lootlookup/osrswiki/WikiScraper.java`, lines 330–398.
- [LL-ci] — `.github/workflows/wiki-structure-check.yml`, lines 1–35.
- [CLOG-runtime] — `src/main/java/com/dropratesclog/DropRatesClogPlugin.java`, lines 58–244.
- [CLOG-scrape] — `scripts/scrape.py`, lines 63–197.
- [CLOG-slayer] — `scripts/scrape.py`, lines 290–324.
- [CLOG-update] — `.github/workflows/update-drop-rates.yml`, lines 1–130.
- [CLOG-ids] — `scripts/gen_clog_items.py`, lines 208–287.
- [EMPTY-count] — `src/main/java/com/loottracker/EmptyDropLootTrackerPlugin.java`, lines 234–338.
- [EMPTY-merge] — `src/main/java/com/loottracker/EmptyDropLootTrackerPlugin.java`, lines 343–393.
- [EMPTY-ui] — `src/main/java/com/loottracker/EmptyDropLootSourceBox.java`, lines 175–220.
- [LTV-client] — `src/main/java/com/loottableviewer/OsrsWikiClient.java`, lines 24–183.
- [LTV-parser] — `src/main/java/com/loottableviewer/WikiDropParser.java`, lines 17–182.
- [LTV-events] — `src/main/java/com/loottableviewer/LootTableViewerPlugin.java`, lines 181–325.
- [LEDGER-fetch] — `src/main/java/com/lootledger/drops/DropFetcher.java`, lines 384–518.
- [CSV-events] — `src/main/java/com/taizur/lootlogger/TaizursLootLoggerPlugin.java`, lines 84–109.
- [BOSS-data] — `src/main/java/com/customplugins/droprates/DropDataService.java`, lines 24–74.
- [RL-event] — `runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootReceived.java`, lines 33–46.
- [RL-emit] — `runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java`, lines 694–716.
- [RL-npc] — `runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java`, lines 740–801.
- [RL-forward] — `runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java`, lines 804–808.
- [RL-widget] — `runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java`, lines 832–960.
- [RL-chat] — `runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java`, lines 1015–1294.
- [RL-salvage] — `runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java`, lines 1064–1070.
- [RL-batches] — `runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java`, lines 1295–1315.
- [RL-inventory] — `runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java`, lines 1318–1355.
- [RL-save] — `runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java`, lines 1537–1555.
- [RL-record] — `runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerRecord.java`, lines 34–97.
- [RL-config] — `runelite-client/src/main/java/net/runelite/client/plugins/loottracker/ConfigLoot.java`, lines 25–77.
- [RL-plugin-event] — `runelite-client/src/main/java/net/runelite/client/plugins/loottracker/PluginLootReceived.java`, lines 36–57.
- [RL-salvage-test] — `runelite-client/src/test/java/net/runelite/client/plugins/loottracker/LootTrackerPluginTest.java`, lines 655–670.
- [RL-manager] — `runelite-client/src/main/java/net/runelite/client/game/LootManager.java`, lines 282–348.
- [RL-manager-plugin] — `runelite-client/src/main/java/net/runelite/client/plugins/PluginManager.java`, lines 403–478.
- [RL-dependency] — `runelite-client/src/main/java/net/runelite/client/plugins/PluginManager.java`, lines 539–555.
- [RL-motherlode] — `runelite-client/src/main/java/net/runelite/client/plugins/motherlode/MotherlodePlugin.java`, lines 223–379.

### Primary data and review documents

- [H-markers]
- [H-droppy]
- [H-cll]
- [H-old-table]
- [H-readme]
- [H-review]
- [H-information]
- [H-rejected]
- [W-large]
- [W-cotton]
- [W-api]
- [W-api-help]
- [W-bucket]
- [W-license]
- [W-drop-module]
- [W-rdt-module]
- [W-skill-template]

[D-fetch]: https://github.com/bckpockets/droppy/blob/e8077217b7a55a5aa0071fdb556fcaba41c79b74/src/main/java/com/droppy/WikiDropFetcher.java#L21-L167
[D-build]: https://github.com/bckpockets/droppy/blob/e8077217b7a55a5aa0071fdb556fcaba41c79b74/build.gradle#L43-L48
[D-generate]: https://github.com/bckpockets/droppy/blob/e8077217b7a55a5aa0071fdb556fcaba41c79b74/src/test/java/com/droppy/WikiScraper.java#L198-L352
[D-text]: https://github.com/bckpockets/droppy/blob/e8077217b7a55a5aa0071fdb556fcaba41c79b74/src/test/java/com/droppy/WikiScraper.java#L372-L416
[D-parse]: https://github.com/bckpockets/droppy/blob/e8077217b7a55a5aa0071fdb556fcaba41c79b74/src/test/java/com/droppy/WikiScraper.java#L572-L717
[D-ids]: https://github.com/bckpockets/droppy/blob/e8077217b7a55a5aa0071fdb556fcaba41c79b74/src/test/java/com/droppy/WikiScraper.java#L769-L827
[D-dedupe]: https://github.com/bckpockets/droppy/blob/e8077217b7a55a5aa0071fdb556fcaba41c79b74/src/test/java/com/droppy/WikiScraper.java#L443-L455
[D-service]: https://github.com/bckpockets/droppy/blob/e8077217b7a55a5aa0071fdb556fcaba41c79b74/src/main/java/com/droppy/DroppyApiClient.java#L22-L72
[D-config]: https://github.com/bckpockets/droppy/blob/e8077217b7a55a5aa0071fdb556fcaba41c79b74/src/main/java/com/droppy/DroppyConfig.java#L52-L64
[D-events]: https://github.com/bckpockets/droppy/blob/e8077217b7a55a5aa0071fdb556fcaba41c79b74/src/main/java/com/droppy/DroppyPlugin.java#L219-L255
[DR-load]: https://github.com/Pluckss/DropRatePluginFinal/blob/d370c9ad1085e9ef0040053dfa5c5e9e436990cc/src/main/java/com/pluckss/droprate/DropRatePlugin.java#L230-L250
[DR-match]: https://github.com/Pluckss/DropRatePluginFinal/blob/d370c9ad1085e9ef0040053dfa5c5e9e436990cc/src/main/java/com/pluckss/droprate/DropRatePlugin.java#L1485-L1567
[DR-base]: https://github.com/Pluckss/DropRatePluginFinal/blob/d370c9ad1085e9ef0040053dfa5c5e9e436990cc/src/main/java/com/pluckss/droprate/DropRatePlugin.java#L1887-L1944
[DR-context]: https://github.com/Pluckss/DropRatePluginFinal/blob/d370c9ad1085e9ef0040053dfa5c5e9e436990cc/src/main/java/com/pluckss/droprate/DropRatePlugin.java#L1485-L1567
[DR-quantity]: https://github.com/Pluckss/DropRatePluginFinal/blob/d370c9ad1085e9ef0040053dfa5c5e9e436990cc/src/main/java/com/pluckss/droprate/DropRatePlugin.java#L1730-L1761
[DR-crawler]: https://github.com/Pluckss/DropRatePluginFinal/blob/d370c9ad1085e9ef0040053dfa5c5e9e436990cc/tools/crawl_bucket.py#L38-L169
[DR-primary]: https://github.com/Pluckss/DropRatePluginFinal/blob/d370c9ad1085e9ef0040053dfa5c5e9e436990cc/tools/crawl_bucket.py#L250-L291
[DR-rdt]: https://github.com/Pluckss/DropRatePluginFinal/blob/d370c9ad1085e9ef0040053dfa5c5e9e436990cc/tools/crawl_bucket.py#L298-L393
[DR-versions]: https://github.com/Pluckss/DropRatePluginFinal/blob/d370c9ad1085e9ef0040053dfa5c5e9e436990cc/tools/crawl_bucket.py#L451-L529
[DR-update]: https://github.com/Pluckss/DropRatePluginFinal/blob/d370c9ad1085e9ef0040053dfa5c5e9e436990cc/.github/workflows/drop-data.yml#L1-L69
[DD-store]: https://github.com/Frailrain/drop-rate-display/blob/74f57566e28369f36b6ddd4514478d342a1775da/src/main/java/com/dropratedisplay/DropRateDataStore.java#L29-L156
[DD-source]: https://github.com/Frailrain/drop-rate-display/blob/74f57566e28369f36b6ddd4514478d342a1775da/src/main/java/com/dropratedisplay/SourceDropTable.java#L18-L105
[DD-ground]: https://github.com/Frailrain/drop-rate-display/blob/74f57566e28369f36b6ddd4514478d342a1775da/src/main/java/com/dropratedisplay/DropRateDisplayPlugin.java#L184-L226
[DD-generator]: https://github.com/Frailrain/drop-rate-display/blob/74f57566e28369f36b6ddd4514478d342a1775da/scripts/generate_drop_rates.py#L35-L301
[DD-data]: https://github.com/Frailrain/drop-rate-display/blob/74f57566e28369f36b6ddd4514478d342a1775da/src/main/resources/drop_rates.json#L1-L1
[DD-entry]: https://github.com/Frailrain/drop-rate-display/blob/74f57566e28369f36b6ddd4514478d342a1775da/src/main/java/com/dropratedisplay/DropRateEntry.java#L41-L103
[LTE-service]: https://github.com/Suffdev/loot-tracker-extended/blob/8fdc35bcc1a040e43b7c15a3501e61b9ba978817/src/main/java/com/searchableloottracker/wiki/WikiDropRateService.java#L35-L175
[LTE-http]: https://github.com/Suffdev/loot-tracker-extended/blob/8fdc35bcc1a040e43b7c15a3501e61b9ba978817/src/main/java/com/searchableloottracker/wiki/WikiDropRateService.java#L446-L530
[LTE-url]: https://github.com/Suffdev/loot-tracker-extended/blob/8fdc35bcc1a040e43b7c15a3501e61b9ba978817/src/main/java/com/searchableloottracker/wiki/WikiDropRateService.java#L565-L574
[LTE-parser]: https://github.com/Suffdev/loot-tracker-extended/blob/8fdc35bcc1a040e43b7c15a3501e61b9ba978817/src/main/java/com/searchableloottracker/wiki/WikiDropTableParser.java#L46-L106
[LTE-resolver]: https://github.com/Suffdev/loot-tracker-extended/blob/8fdc35bcc1a040e43b7c15a3501e61b9ba978817/src/main/java/com/searchableloottracker/wiki/WikiSourceResolver.java#L23-L85
[LTE-overrides]: https://github.com/Suffdev/loot-tracker-extended/blob/8fdc35bcc1a040e43b7c15a3501e61b9ba978817/src/main/resources/wiki-source-overrides.json#L1-L143
[LTE-tooltip]: https://github.com/Suffdev/loot-tracker-extended/blob/8fdc35bcc1a040e43b7c15a3501e61b9ba978817/src/main/java/com/searchableloottracker/wiki/WikiDropTable.java#L40-L75
[LTE-disk]: https://github.com/Suffdev/loot-tracker-extended/blob/8fdc35bcc1a040e43b7c15a3501e61b9ba978817/src/main/java/com/searchableloottracker/wiki/WikiDropTableDiskCache.java#L20-L177
[LTE-events]: https://github.com/Suffdev/loot-tracker-extended/blob/8fdc35bcc1a040e43b7c15a3501e61b9ba978817/src/main/java/com/searchableloottracker/SearchableLootTrackerPlugin.java#L82-L129
[LTE-count]: https://github.com/Suffdev/loot-tracker-extended/blob/8fdc35bcc1a040e43b7c15a3501e61b9ba978817/src/main/java/com/searchableloottracker/LootDataService.java#L177-L235
[LTE-import]: https://github.com/Suffdev/loot-tracker-extended/blob/8fdc35bcc1a040e43b7c15a3501e61b9ba978817/src/main/java/com/searchableloottracker/LootDataService.java#L135-L175
[DF-fetch]: https://github.com/tgoney51/dropfinder/blob/05587f41bb032b418911acff4341cf9e5c1b096a/src/main/java/com/mrmain21/dropfinder/WikiDropClient.java#L57-L179
[DF-parse]: https://github.com/tgoney51/dropfinder/blob/05587f41bb032b418911acff4341cf9e5c1b096a/src/main/java/com/mrmain21/dropfinder/WikiDropClient.java#L344-L435
[CL-registry]: https://github.com/peanubnutter/collection-log-luck/blob/b3cb882a5c675f34a27aaed748f2ac9030cf54e2/src/main/java/com/peanubnutter/collectionlogluck/luck/LogItemInfo.java#L1-L210
[CL-sources]: https://github.com/peanubnutter/collection-log-luck/blob/b3cb882a5c675f34a27aaed748f2ac9030cf54e2/src/main/java/com/peanubnutter/collectionlogluck/luck/LogItemSourceInfo.java#L4-L114
[CL-trials]: https://github.com/peanubnutter/collection-log-luck/blob/b3cb882a5c675f34a27aaed748f2ac9030cf54e2/src/main/java/com/peanubnutter/collectionlogluck/luck/drop/AbstractDrop.java#L81-L142
[CL-api]: https://github.com/peanubnutter/collection-log-luck/blob/b3cb882a5c675f34a27aaed748f2ac9030cf54e2/src/main/java/com/peanubnutter/collectionlogluck/util/CollectionLogLuckApiClient.java#L17-L60
[CL-fetch]: https://github.com/peanubnutter/collection-log-luck/blob/b3cb882a5c675f34a27aaed748f2ac9030cf54e2/src/main/java/com/peanubnutter/collectionlogluck/CollectionLogLuckPlugin.java#L392-L450
[CL-binomial]: https://github.com/peanubnutter/collection-log-luck/blob/b3cb882a5c675f34a27aaed748f2ac9030cf54e2/src/main/java/com/peanubnutter/collectionlogluck/luck/drop/BinomialDrop.java#L12-L72
[LL-fetch]: https://github.com/donth77/loot-lookup-plugin/blob/58812e18d0f955d597c4894ece0ae23aff1a3a74/src/main/java/com/lootlookup/osrswiki/WikiScraper.java#L21-L175
[LL-map]: https://github.com/donth77/loot-lookup-plugin/blob/58812e18d0f955d597c4894ece0ae23aff1a3a74/src/main/java/com/lootlookup/osrswiki/WikiScraper.java#L410-L485
[LL-row]: https://github.com/donth77/loot-lookup-plugin/blob/58812e18d0f955d597c4894ece0ae23aff1a3a74/src/main/java/com/lootlookup/osrswiki/WikiScraper.java#L330-L398
[LL-ci]: https://github.com/donth77/loot-lookup-plugin/blob/58812e18d0f955d597c4894ece0ae23aff1a3a74/.github/workflows/wiki-structure-check.yml#L1-L35
[CLOG-runtime]: https://github.com/pairofcrocs/drop-rates-clog/blob/eb1d63c072fe037500c1e7e78440b8ae949957d2/src/main/java/com/dropratesclog/DropRatesClogPlugin.java#L58-L244
[CLOG-scrape]: https://github.com/pairofcrocs/drop-rates-clog/blob/eb1d63c072fe037500c1e7e78440b8ae949957d2/scripts/scrape.py#L63-L197
[CLOG-slayer]: https://github.com/pairofcrocs/drop-rates-clog/blob/eb1d63c072fe037500c1e7e78440b8ae949957d2/scripts/scrape.py#L290-L324
[CLOG-update]: https://github.com/pairofcrocs/drop-rates-clog/blob/eb1d63c072fe037500c1e7e78440b8ae949957d2/.github/workflows/update-drop-rates.yml#L1-L130
[CLOG-ids]: https://github.com/pairofcrocs/drop-rates-clog/blob/eb1d63c072fe037500c1e7e78440b8ae949957d2/scripts/gen_clog_items.py#L208-L287
[EMPTY-count]: https://github.com/DylanHolmesDH/EmptyLootTracker/blob/73d420714e0842507dc43aa79be18d380d188258/src/main/java/com/loottracker/EmptyDropLootTrackerPlugin.java#L234-L338
[EMPTY-merge]: https://github.com/DylanHolmesDH/EmptyLootTracker/blob/73d420714e0842507dc43aa79be18d380d188258/src/main/java/com/loottracker/EmptyDropLootTrackerPlugin.java#L343-L393
[EMPTY-ui]: https://github.com/DylanHolmesDH/EmptyLootTracker/blob/73d420714e0842507dc43aa79be18d380d188258/src/main/java/com/loottracker/EmptyDropLootSourceBox.java#L175-L220
[LTV-client]: https://github.com/Oufqt/Loot-Table-Viewer/blob/5f840d4f6789ffb471bcaffe19d598940381ae81/src/main/java/com/loottableviewer/OsrsWikiClient.java#L24-L183
[LTV-parser]: https://github.com/Oufqt/Loot-Table-Viewer/blob/5f840d4f6789ffb471bcaffe19d598940381ae81/src/main/java/com/loottableviewer/WikiDropParser.java#L17-L182
[LTV-events]: https://github.com/Oufqt/Loot-Table-Viewer/blob/5f840d4f6789ffb471bcaffe19d598940381ae81/src/main/java/com/loottableviewer/LootTableViewerPlugin.java#L181-L325
[LEDGER-fetch]: https://github.com/ChunkyAtlas/loot-ledger/blob/216c42d94f797c6101f5b30860e2821674727c13/src/main/java/com/lootledger/drops/DropFetcher.java#L384-L518
[CSV-events]: https://github.com/Taizur/TaizursLootLogger/blob/7628bf8e586841af12c04c940118386c22553f27/src/main/java/com/taizur/lootlogger/TaizursLootLoggerPlugin.java#L84-L109
[BOSS-data]: https://github.com/ImNotNamed/Detailed-Boss-Drop-Rates/blob/4aae479112ea3ebd16f66dc597d07839e4b3fddd/src/main/java/com/customplugins/droprates/DropDataService.java#L24-L74
[RL-event]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootReceived.java#L33-L46
[RL-emit]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java#L694-L716
[RL-npc]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java#L740-L801
[RL-forward]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java#L804-L808
[RL-widget]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java#L832-L960
[RL-chat]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java#L1015-L1294
[RL-salvage]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java#L1064-L1070
[RL-batches]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java#L1295-L1315
[RL-inventory]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java#L1318-L1355
[RL-save]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java#L1537-L1555
[RL-record]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerRecord.java#L34-L97
[RL-config]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/ConfigLoot.java#L25-L77
[RL-plugin-event]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/PluginLootReceived.java#L36-L57
[RL-salvage-test]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/test/java/net/runelite/client/plugins/loottracker/LootTrackerPluginTest.java#L655-L670
[RL-manager]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/game/LootManager.java#L282-L348
[RL-manager-plugin]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/PluginManager.java#L403-L478
[RL-dependency]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/PluginManager.java#L539-L555
[RL-motherlode]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/motherlode/MotherlodePlugin.java#L223-L379
[P-droppy]: https://github.com/bckpockets/droppy/tree/e8077217b7a55a5aa0071fdb556fcaba41c79b74
[P-drop-rate-properties]: https://github.com/Pluckss/DropRatePluginFinal/tree/d370c9ad1085e9ef0040053dfa5c5e9e436990cc
[P-drop-rate-display]: https://github.com/Frailrain/drop-rate-display/tree/74f57566e28369f36b6ddd4514478d342a1775da
[P-loot-tracker-extended]: https://github.com/Suffdev/loot-tracker-extended/tree/8fdc35bcc1a040e43b7c15a3501e61b9ba978817
[P-dropfinder]: https://github.com/tgoney51/dropfinder/tree/05587f41bb032b418911acff4341cf9e5c1b096a
[P-collection-log-luck]: https://github.com/peanubnutter/collection-log-luck/tree/b3cb882a5c675f34a27aaed748f2ac9030cf54e2
[P-loot-lookup]: https://github.com/donth77/loot-lookup-plugin/tree/58812e18d0f955d597c4894ece0ae23aff1a3a74
[P-drop-rates-clog]: https://github.com/pairofcrocs/drop-rates-clog/tree/eb1d63c072fe037500c1e7e78440b8ae949957d2
[P-empty-loot-tracker]: https://github.com/DylanHolmesDH/EmptyLootTracker/tree/73d420714e0842507dc43aa79be18d380d188258
[P-loot-table-viewer]: https://github.com/Oufqt/Loot-Table-Viewer/tree/5f840d4f6789ffb471bcaffe19d598940381ae81
[P-loot-ledger]: https://github.com/ChunkyAtlas/loot-ledger/tree/216c42d94f797c6101f5b30860e2821674727c13
[P-csv-lifetime-drop-ledger]: https://github.com/Taizur/TaizursLootLogger/tree/7628bf8e586841af12c04c940118386c22553f27
[P-detailed-boss-drop-rates]: https://github.com/ImNotNamed/Detailed-Boss-Drop-Rates/tree/4aae479112ea3ebd16f66dc597d07839e4b3fddd
[H-markers]: https://github.com/runelite/plugin-hub/tree/7cb0ee81560bf9dee06ff9b8e376626bc6943ce3/plugins
[H-droppy]: https://github.com/runelite/plugin-hub/blob/7cb0ee81560bf9dee06ff9b8e376626bc6943ce3/plugins/droppy
[H-cll]: https://github.com/runelite/plugin-hub/blob/7cb0ee81560bf9dee06ff9b8e376626bc6943ce3/plugins/collection-log-luck
[H-old-table]: https://github.com/runelite/plugin-hub/blob/7cb0ee81560bf9dee06ff9b8e376626bc6943ce3/plugins/loot-table
[H-readme]: https://github.com/runelite/plugin-hub/blob/7cb0ee81560bf9dee06ff9b8e376626bc6943ce3/README.md
[H-review]: https://github.com/runelite/runelite/wiki/Plugin-Hub-Review
[H-information]: https://github.com/runelite/runelite/wiki/Information-about-the-Plugin-Hub
[H-rejected]: https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features
[W-large]: https://oldschool.runescape.wiki/w/Large_salvage?oldid=15357637
[W-cotton]: https://oldschool.runescape.wiki/w/Cotton_seed?oldid=15226139
[W-api]: https://oldschool.runescape.wiki/api.php?action=parse&page=Large%20salvage&prop=wikitext&format=json
[W-api-help]: https://oldschool.runescape.wiki/api.php?action=help&modules=bucket
[W-bucket]: https://meta.weirdgloop.org/w/Extension:Bucket
[W-license]: https://meta.weirdgloop.org/w/Licensing
[W-drop-module]: https://oldschool.runescape.wiki/w/Module:DropsLine?oldid=15358914
[W-rdt-module]: https://oldschool.runescape.wiki/w/Module:RareDropLines?oldid=15343549
[W-skill-template]: https://oldschool.runescape.wiki/w/Template:DropsLineSkill?oldid=15325754

### Follow-up evidence anchors

- [RL-arm]
- [RL-collect]
- [RL-timeout]
- [SACK-pattern]
- [SACK-chat]
- [SACK-item]
- [SACK-rates]
- [SACK-rate-map]
- [SACK-data]
- [SACK-identity]
- [SACK-tests]
- [SACK-license]
- [EMPTY-credit]
- [EMPTY-match]
- [EMPTY-delay]
- [H-sack]
- [W-drop-table]
- [W-station]
- [W-boatstation]
- [W-salvaging]
- [J-sailing-rates]
- [RL-salvage-intro]
- [RL-salvage-refactor]
- [RL-boss-issue]
- [RL-boss-pr]
- [W-goblin-bucket]
- [W-zulrah-bucket]
- [W-vorkath-bucket]
- [W-crate-bucket]

[RL-arm]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java#L1622-L1645
[RL-collect]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java#L1581-L1619
[RL-timeout]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java#L718-L737
[SACK-pattern]: https://github.com/BugcatcherBill/SalvageSack/blob/69ad7087b8831a2b524ee50b793f8b44e2a80e7d/src/main/java/com/salvagesack/SalvageSackPlugin.java#L40-L51
[SACK-chat]: https://github.com/BugcatcherBill/SalvageSack/blob/69ad7087b8831a2b524ee50b793f8b44e2a80e7d/src/main/java/com/salvagesack/SalvageSackPlugin.java#L384-L435
[SACK-item]: https://github.com/BugcatcherBill/SalvageSack/blob/69ad7087b8831a2b524ee50b793f8b44e2a80e7d/src/main/java/com/salvagesack/SalvageItem.java#L39-L82
[SACK-rates]: https://github.com/BugcatcherBill/SalvageSack/blob/69ad7087b8831a2b524ee50b793f8b44e2a80e7d/src/main/java/com/salvagesack/DropRateManager.java#L29-L134
[SACK-rate-map]: https://github.com/BugcatcherBill/SalvageSack/blob/69ad7087b8831a2b524ee50b793f8b44e2a80e7d/src/main/java/com/salvagesack/DropRateManager.java#L334-L438
[SACK-data]: https://github.com/BugcatcherBill/SalvageSack/blob/69ad7087b8831a2b524ee50b793f8b44e2a80e7d/src/main/resources/drop_rates.json#L95-L129
[SACK-identity]: https://github.com/BugcatcherBill/SalvageSack/blob/69ad7087b8831a2b524ee50b793f8b44e2a80e7d/src/main/java/com/salvagesack/SalvageSackPlugin.java#L437-L489
[SACK-tests]: https://github.com/BugcatcherBill/SalvageSack/blob/69ad7087b8831a2b524ee50b793f8b44e2a80e7d/src/test/java/com/salvagesack/SalvageSackPluginLogicTest.java#L14-L90
[SACK-license]: https://github.com/BugcatcherBill/SalvageSack/blob/69ad7087b8831a2b524ee50b793f8b44e2a80e7d/LICENSE#L1-L25
[EMPTY-credit]: https://github.com/DylanHolmesDH/EmptyLootTracker/blob/73d420714e0842507dc43aa79be18d380d188258/src/main/java/com/loottracker/EmptyDropLootTrackerPlugin.java#L173-L231
[EMPTY-match]: https://github.com/DylanHolmesDH/EmptyLootTracker/blob/73d420714e0842507dc43aa79be18d380d188258/src/main/java/com/loottracker/EmptyDropLootTrackerPlugin.java#L409-L480
[EMPTY-delay]: https://github.com/DylanHolmesDH/EmptyLootTracker/blob/73d420714e0842507dc43aa79be18d380d188258/src/main/java/com/loottracker/EmptyDropLootTrackerPlugin.java#L133-L161
[H-sack]: https://github.com/runelite/plugin-hub/blob/7cb0ee81560bf9dee06ff9b8e376626bc6943ce3/plugins/salvage-sack
[P-salvage-sack]: https://github.com/BugcatcherBill/SalvageSack/tree/69ad7087b8831a2b524ee50b793f8b44e2a80e7d
[W-drop-table]: https://oldschool.runescape.wiki/w/Drop_table?oldid=15263067
[W-station]: https://oldschool.runescape.wiki/w/Salvaging_station?oldid=15329526
[W-boatstation]: https://oldschool.runescape.wiki/w/Salvaging_station_(facility)?oldid=15226147
[W-salvaging]: https://oldschool.runescape.wiki/w/Shipwreck_salvaging?oldid=15355548
[J-sailing-rates]: https://secure.runescape.com/m=news/sailing-xp-review--further-fixes?oldschool=1
[RL-salvage-intro]: https://github.com/runelite/runelite/commit/5b5c0a03a34ff062db59b105b82797f6d4bdbb0e
[RL-salvage-refactor]: https://github.com/runelite/runelite/commit/ad593f28eaaf601d3a025e74c89b8367d1d5d17f
[RL-boss-issue]: https://github.com/runelite/runelite/issues/19307
[RL-boss-pr]: https://github.com/runelite/runelite/pull/20336
[W-goblin-bucket]: https://oldschool.runescape.wiki/api.php?action=bucket&format=json&query=bucket%28%27dropsline%27%29.select%28%27page_name%27%2C%27page_name_sub%27%2C%27item_name%27%2C%27drop_json%27%2C%27rare_drop_table%27%29.where%28%27page_name%27%2C%27Goblin%27%29.limit%28200%29.run%28%29
[W-zulrah-bucket]: https://oldschool.runescape.wiki/api.php?action=bucket&format=json&query=bucket%28%27dropsline%27%29.select%28%27page_name%27%2C%27page_name_sub%27%2C%27item_name%27%2C%27drop_json%27%2C%27rare_drop_table%27%29.where%28%27page_name%27%2C%27Zulrah%27%29.limit%28200%29.run%28%29
[W-vorkath-bucket]: https://oldschool.runescape.wiki/api.php?action=bucket&format=json&query=bucket%28%27dropsline%27%29.select%28%27page_name%27%2C%27page_name_sub%27%2C%27item_name%27%2C%27drop_json%27%2C%27rare_drop_table%27%29.where%28%27page_name%27%2C%27Vorkath%27%29.limit%28200%29.run%28%29
[W-crate-bucket]: https://oldschool.runescape.wiki/api.php?action=bucket&format=json&query=bucket%28%27dropsline%27%29.select%28%27page_name%27%2C%27page_name_sub%27%2C%27item_name%27%2C%27drop_json%27%2C%27rare_drop_table%27%29.where%28%27page_name%27%2C%27Supply+crate%27%29.limit%28200%29.run%28%29

[RL-timeout-constant]: https://github.com/runelite/runelite/blob/d8e7d1e5f34e2899eda3d7cf4cd9661ae2206f22/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java#L130-L140
