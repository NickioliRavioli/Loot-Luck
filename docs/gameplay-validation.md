# Gameplay validation before source certification

Automated tests validate bookkeeping and presentation. Only user-driven gameplay can confirm the actual detection path; never automate RuneScape input. Public submission and exact production statistics remain gated.

## First local review

1. Launch with `.\gradlew.bat run`, log in using [RuneLite's Using Jagex Accounts instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts), and enable Loot Luck plus core Loot Tracker. The new `profiles-wiki-v1` namespace starts empty; older files are preserved.
2. Obtain loot from Large salvage, an ordinary monster and a boss. Confirm source headings, real icons, reciprocal rates, quantities and dates. Unknown sources should still appear with unavailable rates.
3. Open multiple item rows, collapse them, collapse a source and use keyboard activation. Only one item's inline details should be visible. GE/HA and luck-color settings should update immediately.
4. Switch Session/Lifetime, reset Session and restart. Lifetime counts must persist. Switching accounts must show isolated data; resetting core Loot Tracker must not reset Loot Luck.
5. Keep Wiki refresh off and verify observation tracking continues offline. Enable it after reading the disclosure, then request Refresh. Failure must preserve previously available rates and observations.

## Value-based background effects

- Under Settings → Background effects, select a colour-based effect such as Rarity fade. Compare Rarity, Value per item and Total collected value in list/grid views, All items and Recent collections. Total value uses the quantity in that view, so a collection and a lifetime aggregate can have different colours. Drop-rate text must retain its rarity colour.
- Change Ground Items' price thresholds, tier colours and value calculation (GE, HA or Highest); Loot Luck value effects must update without restarting. Nonpositive thresholds are disabled and a value must exceed the threshold, matching Ground Items. Check a coin stack and noted loot. Hide GE/HA values; background colours must still work.
- Unobtained items and Failed pickpocket rows must have no loot background effect. Restart and confirm the selected colour mode persists. Existing configurations must default to Rarity.

## Pickpocket messages, Rogue rewards and failures

RuneLite 1.13.1 core Loot Tracker identifies pickpocket success through its chat message and emits `PICKPOCKET` loot separately from `NPC` kill loot. Loot Luck retains that event classification and labels pickpocket source cards accordingly. The ordinary attempt/success/failure wording is recognised by [Pickpocket Helper](https://github.com/pajlads/runelite-pickpocket-helper/blob/15840d787ca06b705677465b01f19a8d4b11a8b7/src/main/java/com/pickpockethelper/utility/MessagePattern.java); this does not certify every NPC or special/blocked attempt path. Failure tracking counts each explicit failure message, without requiring a preceding attempt message and without counting the subsequent stun again.

Rogue activation messages are attached to matching successful pickpocket loot, with reward processing deferred until the tick's messages have arrived. Outfit pieces are captured at the success message. Ordinary doubled loot matches the published base quantity while retaining actual received units and one occurrence. Scroll/clue boxes and one coin pouch plus its bonus coins are treated separately. Missing context, interrupted sequences and multiple successes in one tick preserve uncertainty. Historical rate snapshots are not rewritten; published rarity is unchanged. Gloves of silence and a buff banner are outside this change.

- At a Master Farmer, compare several normal and Rogue-doubled seed rewards with actual inventory gains and the published table. Use a partial outfit so both outcomes occur. Check repeated identical rewards, full/no outfit and equipment changes between attempts.
- Compare explicit failure messages with the **Failed pickpocket** row. One failure followed by a stun must add exactly one occurrence. Reach, full-inventory and full-pouch warnings must not add failures. The row should show no quantity, price or rate in list/grid views, expanded details, recent collections and All items, under every count style.
- Toggle **Include failed pickpockets in occurrences** under Settings → Item counts. Enabled (default), the pickpocket source total includes successes plus recorded failures; disabled, it includes successful loot notifications only. The failure row and real item counts must stay unchanged in Session and Lifetime. Restart and confirm the setting persists.
- Pickpocket and kill the same NPC type where practical. The sources should remain separate and only pickpocket rewards should receive Rogue quantity treatment. Check H.A.M. Man/Woman message aliases against the H.A.M. Member source.
- Exercise a pouch reward, its extra coins and a clue/scroll box if available. They must not be blindly halved. Compare any rapid/blackjack sequence; ambiguous event batches must not acquire a guessed doubling result.
- Restart, export/import and switch accounts; verify failures and real loot stay separate and persist. Reset Session; Lifetime failures must remain. Try filtered game chat and confirm the failure and doubling messages still reach tracking.

## Maze random event inventory rewards

The passive Maze adapter records inventory gains after opening the Maze's closed chest or touching its Strange shrine. The panel keeps **Chest (Maze)** and **Maze reward** separate, with Wiki owners Chest (Maze) and Strange shrine respectively. These are recorded collections, not certified attempts; luck/expected/dry statistics stay unavailable. Rewards sent directly to the bank or dropped on the floor are not captured by this adapter; a partially delivered shrine reward records only its inventory portion.

- Leave inventory space, open several chests, and continue any reward dialogue. Compare each item and quantity with Chest (Maze); existing stacks and consecutive identical rewards must increment once per collection.
- Touch the shrine and confirm the teleport preserves one Maze reward collection containing all inventory-delivered items, including noted ore and repeated rolls combined into one stack. Compare the Wiki links with the two respective reward tables.
- Search an empty chest, cancel a distant object click by walking elsewhere, or leave without finishing: no invented reward should appear. Let a selection expire or log out while it is pending; unrelated later inventory gains must not be recorded as Maze loot.
- Restart the plugin and compare Session/Lifetime and recent collections. The recorded loot must persist under the correct account and source.

## Salvage denominator and mechanics

Record the number of deliberate input sorts and compare source notification count, reported amount and individual item-present outcomes. Exercise consecutive openings, existing inventory stacks, full inventory, ground/cargo overflow and more than one reward in an opening. Record differences; notification count is not assumed to equal openings.

Separately establish the main-table and pre-roll gate relationship from primary mechanics evidence. The Cotton row is 5/2,218, but its unconditional per-opening meaning is unresolved. Do not enable a model by copying the fixture probability.

## Monsters and bosses

Check Giant rat levels 3/6 versus level 26: observations should select their respective source-page tables. Level-2 Goblin tables should resolve through NPC IDs even at identical combat levels. Missing/overlapping context must retain candidates and suppress exact luck. If convenient, compare Guard (Shayzien) and Guard (Auburnvale): their original event names remain Guard but Wiki links/source families must differ. Compare the source's **W** page and recorded provenance with the actual source page.

Test a game name differing from its Wiki page title, a scroll box/casket and Brutus's noted/unnoted steak. Rates, icons and quantities must remain associated with the correct item and noted status. Qualitative Wiki rarities should show their wording without invented numeric probabilities.

For Goblin, record variants/NPC identity and empty outcomes. For Vorkath and Zulrah, establish one completion boundary including late unique/clue rewards, contested kills and missing notifications. Preserve per-row rolls; only use `1-(1-q)^r` when independence and the relevant opportunities are established.

If a narrow adapter is necessary, give it a reviewed completion identity/eligibility contract and captured tests. Never deduplicate by loot contents, infer successful rolls from quantity, or repost synthetic loot into RuneLite.

## Candidate models and the validation button

Before using the sidebar validation action, manually reconcile deliberate kills with complete notifications. Include consecutive identical rewards, existing inventory stacks, empty outcomes where possible, contested kills, late rewards and interruptions. One eligible kill must produce one complete notification. If that cannot be established, leave the source unapproved and report the difference.

Approval applies to this account/source and the current reviewed rate/page/model contract. It affects future observations only. Exact luck stays unavailable before approval, and changed evidence requires approval again. Reviewed item scopes are:

| Source | Items |
| --- | --- |
| Cow | Bones, cowhide, raw beef |
| Chicken | Bones, raw chicken, feathers |
| Giant rat | Bones, raw rat meat |
| Vorkath | Dragonbone necklace, draconic visage, skeletal visage, jar of decay |
| Zulrah | Tanzanite fang, magic fang, serpentine visage, uncut onyx, jar of swamp |

For Chicken, compare a manually counted run with the notification count. After approval, feathers should have a 3/4 per-kill presence chance from two mutually exclusive Wiki outcomes. Several feathers in a stack count as one success; quantity is separate.

For Zulrah, the published fang rate is 1/1,024 per roll. Two independent rolls give per-kill presence `1-(1023/1024)^2 = 2047/1048576`. Expanded details should show this separately from the Wiki rate. Two of the same item from one kill still count as one presence success. Confirm both reward rolls and late rewards arrive in one eligible notification before approval.

For Vorkath, reconcile uncontested completions and late rewards against complete notifications. Heads, pets, clues and other conditional outcomes are outside the reviewed item scope. Do not approve if actual completion boundaries disagree with the event contract.

## Dry streaks and overall luck

1. After approval, record failures then a success. Dry count increases once per eligible kill and resets on presence, regardless of quantity. Before the first success it covers recorded tracking only.
2. Restart, log out, hop or disable core Loot Tracker. The active streak should stop; lifetime overall validated counts and expected drops should remain.
3. Reset Session and switch Session/Lifetime. Session clears while lifetime persists. Account switching isolates data and approvals.
4. Distinct validated probability segments contribute to overall expectation and A/E. A combined binomial tail must remain unavailable when probabilities differ. Dry streak uses only its continuous segment.
5. Batches, unsupported conditions and special worlds retain observations without gaining exact probability results.

Caskets, reward pools and salvage remain in the rate corpus, but variable rolls/gates and missing eligibility context currently prevent exact production luck for them. Report the source page, event type/name/metadata, table, item ID/name, expected rate and displayed result for discrepancies. Runtime refresh covers structured rates; page prose and identity exports require the development refresh in README.

## Certification checklist

- Exact source/context/variant and attempt unit established.
- Empty, split and batch outcomes reconciled against manual inputs.
- Item-present probability, gates, conditional state and roll independence established.
- Mapping/probability changes create new segments; observation history remains untouched.
- Adapter/model version, rate hash and validation evidence recorded in the registry.
- A model mismatch or coverage gap suppresses exact streak/probability results.

Until these checks pass, keep the source observable with its probability statistics unavailable. A locally running client or screenshot does not replace these checks.

## Data settings controls

- Open settings and check the section titles, wrapped hints and preview controls at the normal sidebar width. Obvious action buttons should have no extra hint paragraphs.
- Check all three item-count previews: compact rows, no effects or selection dot, the same 12,500 units/seven occurrences, and no clipped leading icon digit. Select each style and compare it with live list, grid, All items and recent collection views. Quantities should be yellow with no x; occurrences should use x. Abbreviations round down (12,500 to 12K); full counts stay exact and Wiki rates retain their precision. New settings default to quantity-on-icon while existing saved styles remain selected.
- In grid view, switch through all three count styles. Every obtained item should keep the same yellow quantity at the top-left corner, above the icon; nothing should appear along the bottom or be hidden behind the sprite. Check quantities 1, 108 and 12,500, an asynchronously loaded icon, selection and an unobtained item (no zero count). List rows should still follow their selected style.
- Expand an item in list and grid views. Quantity, occurrences, GE total and HA total should show exact comma-separated numbers; monetary totals include gp without k/M abbreviations. Field names sit beside their right-aligned values, including the trillion-GP example. A label/value pair should move onto two lines only if it would otherwise clip. Table counts use the same fallback below the published quantity/rate or Unknown table heading. Automated fixtures also check trillion-gp totals and maximum signed 64-bit counts at the normal sidebar width; confirm ordinary data remains easy to read in-game.
- Check the example source headers: billion/trillion GE totals should use B/T, and large notification counts should abbreviate too. Source names may use two lines; names longer than that should end in an ellipsis. Counts, GE values and the Wiki button should remain visible without overlapping the name. Short names should stay on one line. Collapse and expand both short and wrapped headers in list and grid views.
- Click the information button in expanded list/grid details. It should open an HTML popover with gold item/section headings, bold field labels and published rates in their table colors, without a text caret or editable area. Scroll a long recorded history, dismiss by clicking outside, and reopen it. Dry/luck statistics should remain hidden. Check an unknown-rate item too.
- Scroll down through settings: Back to loot should remain in the fixed top header and return to the loot view. Reset session should have an inline hint explaining that only session totals and collections are cleared, with lifetime data preserved.
- With Allow Wiki refresh off, check its explanation and disabled Refresh button. With it on, check the explanation, existing third-party confirmation and enabled Refresh button. Switching it off must retain saved rates and recorded loot.
- At the bottom, History and data should contain the history limit, Reset session and separate Export data, Import data and red Delete data buttons. Cancel Reset session, Import and Delete confirmations; no counts or history should change. Confirm Reset session clears only the session, preserving lifetime data. If an account changes during a confirmation, the queued action must not affect either account.

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

- Confirm the new ledger starts fresh automatically; old files should be left intact.
- Loot several cosmetic cow variants. Confirm one Cow header and one top-level
  Cow entry in the export, with variants nested underneath and correct totals.
- Loot goblins in multiple locations. Confirm one Goblin header while distinct
  contexts/recorded rate tables remain preserved in the export.
- Compare By source, All items and Recent collections for double counting.
- Export/import and restart: family totals, histories and nested contexts must
  survive. Overall validated expectations can aggregate, but incompatible probabilities must not gain a shared binomial tail or continuous streak.

Confirm the gameplay results to the developer before considering this validation complete. The source validation button records that you performed its boundary checks.

## Condition variants and corrected rates

- While luck UI is temporarily hidden, confirm list and grid details show quantities, occurrences, values and Wiki rate breakdowns without attempts, per-attempt chance, expected drops, A/E, dry streaks or probability tails. Check the information popup also omits statistics, validation buttons are absent, and the sort menu omits Driest first. Luck-model validation checks elsewhere in this document resume after the UI is re-enabled.
- In grid view, select items in different rows, including a partial final row. Details should open directly under the selected row and move when selection changes; later rows should move down. Confirm the complete selected border remains visible with a gap before details, clicking the selected item closes them, and large source grids still scroll normally.

- Restart into the development build; an already running JVM cannot load the new code.
- During Rag and Bone Man II, collect an unpolished Zombie bone and check its published rate is 1/4. Rat bone should remain 1/4. Historical unknown-at-collection entries should remain unchanged.
- Kill low and high Dark wizards. Expand the main header and confirm items appear directly underneath without subheaders. Check the Earth rune quantity/table alternatives in item details, including 36 at 1/32. Energy potion(2) may legitimately remain unknown.
- Toggle Show variants separately and confirm Low level and High level appear as top-level headers. Compare source totals, All items and Recent collections before/after; quantities and notification counts must not change. Return to the combined view and confirm the main header collapses and expands its item rows.
- Compare Giant rat levels 3/6 versus 26 and Goblin's verified Drop Table 1/2. Cosmetic variants with the same conditions should share a group.
- If testing Ghosts, compare Taverley and ordinary Wilderness kills, including empty outcomes. Newly captured contexts should distinguish known outside/Wilderness locations; old missing context must remain Unknown location. Moving after death must not rewrite the kill location. Wilderness-only unobtained loot should be absent from known outside groups. Ghost exact-luck statistics remain unavailable.
- Restart and export/import: preserved variant observations must keep their counts. Instances or ambiguous deaths should remain unresolved instead of selecting a guessed location.
