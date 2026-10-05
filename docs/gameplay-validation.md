# Gameplay validation before source certification

Automated tests validate bookkeeping and presentation. Only user-driven gameplay can confirm the actual detection path; never automate RuneScape input. Public submission and exact production statistics remain gated.

## First local review

1. Launch with `.\gradlew.bat run`, log in using the official RuneLite development-client/Jagex-account instructions, and enable Loot Luck plus core Loot Tracker.
2. Obtain loot from Large salvage, an ordinary monster and a boss. Confirm source headings, real icons, reciprocal rates, quantities and dates. Unknown sources should still appear with unavailable rates.
3. Open multiple item rows, collapse them, collapse a source and use keyboard activation. Only one item's inline details should be visible. GE/HA and luck-color settings should update immediately.
4. Switch Session/Lifetime, reset Session and restart. Lifetime counts must persist. Switching accounts must show isolated data; resetting core Loot Tracker must not reset Loot Luck.
5. Keep Wiki refresh off and verify observation tracking continues offline. Enable it after reading the disclosure, then request Refresh. Failure must preserve previously available rates and observations.

## Salvage denominator and mechanics

Record the number of deliberate input sorts and compare source notification count, reported amount and individual item-present outcomes. Exercise consecutive openings, existing inventory stacks, full inventory, ground/cargo overflow and more than one reward in an opening. Record differences; notification count is not assumed to equal openings.

Separately establish the main-table and pre-roll gate relationship from primary mechanics evidence. The Cotton row is 5/2,218, but its unconditional per-opening meaning is unresolved. Do not enable a model by copying the fixture probability.

## Monsters and bosses

For Goblin, record variants/NPC identity and empty outcomes. For Vorkath and Zulrah, establish one completion boundary including late unique/clue rewards, contested kills and missing notifications. Preserve per-row rolls; only use `1-(1-q)^r` when independence and the relevant opportunities are established.

If a narrow adapter is necessary, give it a reviewed completion identity/eligibility contract and captured tests. Never deduplicate by loot contents, infer successful rolls from quantity, or repost synthetic loot into RuneLite.

## Certification checklist

- Exact source/context/variant and attempt unit established.
- Empty, split and batch outcomes reconciled against manual inputs.
- Item-present probability, gates, conditional state and roll independence established.
- Mapping/probability changes create new segments; observation history remains untouched.
- Adapter/model version, rate hash and validation evidence recorded in the registry.
- A model mismatch or coverage gap suppresses exact streak/probability results.

Until these checks pass, keep the source observable with its probability statistics unavailable. A locally running client or screenshot does not replace these checks.

## Data settings controls

- Export the current account, collect more loot, then import the export. Confirm
  lifetime totals return to the export and the session is empty.
- Cancel each chooser and confirmation: observations must remain unchanged.
- Reject an export from another account and a malformed JSON file without changes.
- Switch accounts while a chooser is open: the pending action must not touch either
  account. Retry explicitly for the newly selected account.
- Export, delete, restart, and confirm old data is not restored by the recovery
  snapshot. Import the saved export to restore the test observations.
- Check that the history limit applies on import, while lifetime totals remain.

## Grouped source storage

- Start fresh with Data settings > Delete data after upgrading to schema 3.
- Loot several cosmetic cow variants. Confirm one Cow header and one top-level
  Cow entry in the export, with variants nested underneath and correct totals.
- Loot goblins in multiple locations. Confirm one Goblin header while distinct
  contexts/recorded rate tables remain preserved in the export.
- Compare By source, All items and Recent collections for double counting.
- Export/import and restart: family totals, histories and nested contexts must
  survive. Mixed variant/world contexts must not gain a shared certified statistic.
