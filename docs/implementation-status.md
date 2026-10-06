# Local implementation status

Updated 6 October 2026 on `codex/wiki-rate-audit`.

The full Wiki export contains **39,786 rows / 2,244 source pages**. A frozen 150-page sample preserves source-page revisions, wikitext and rendered drop tables. Independent page comparison covers 4,571 rows; 542 sampled rows remain unmatched and are listed in `tools/wiki-audit/not-compared.json`. No rate conflicts were found among the compared rows. Full catalogue parsing and identity coverage are checked separately. Neither result establishes complete gameplay coverage.

Generic item matching uses Wiki item IDs and game-name aliases. Generic source matching uses unambiguous Wiki NPC IDs, preserving the original event name. Explicit Wiki NPC/level evidence selects variant tables without inspecting received loot. Unresolved or overlapping context retains candidates rather than selecting a guessed table.

Candidate presence models cover selected Cow, Chicken, Giant rat, Vorkath and Zulrah items. They require per-account/source gameplay approval bound to the reviewed rate/page/model contract. Fixed independent rolls and explicitly reviewed mutually exclusive outcomes are supported; variable rolls, batches, special worlds and unresolved conditions are excluded. Published rates and per-attempt presence probabilities remain separate.

Continuous recorded dry streaks stop at coverage interruptions. Overall expected drops aggregate validated segments; binomial tails are shown only when probabilities agree. Quantities remain separate from one-success-per-item-present-event accounting. The ledger starts fresh under `profiles-wiki-v1`, with older files preserved and no migration.

**Automated verification: 82 Java tests and 7 Python audit-reader tests passed.** Coverage includes full-catalogue parsing, independently preserved rendered rates, same-name NPC owners, Giant rat/Goblin/Dark wizard conditions, changed-evidence invalidation, anchored quest-item IDs, noted forms, discrete quantities, stable dry probabilities, Wiki calculator recurrence comparisons, multiple rolls, mutually exclusive outcomes, approval gating, coverage interruptions, mixed-probability overall luck, persistence, provider caching, death-context ambiguity and combined/standalone UI projections.

Combined source headers remain the default, with item rows directly underneath and no condition subheaders. Settings can show condition variants as separate top-level headers. Cosmetic NPCs sharing the same verified conditions combine. Family totals, All items and collection history retain single counting. Unknown conditions stay explicitly unresolved, and combined display identities cannot accidentally select a single member's table. The private recorded-data audit resolves all 183 saved item contexts, verifies 271 page-rate rows and leaves one unspecified Dark wizard potion row unverified; see [recorded-loot-audit.md](recorded-loot-audit.md).

Runtime: RuneLite **1.13.1**, Gradle wrapper **8.10**, JDK **17**, Java output **11**. No game input was automated. No source has received user gameplay confirmation in this task; implementation validation is pending the checklist in [gameplay-validation.md](gameplay-validation.md). No changes have been pushed or published.

Runtime Wiki refresh covers the structured export. Bundled identity/page evidence needs explicit maintenance refresh, and prose-only Wiki changes are not detected at runtime. The independent audit exposes missing comparisons rather than certifying them. Casket, salvage and other conditional adapters remain future work where an exact attempt model cannot yet be established.
