# Local implementation status

Update, 4 October 2026: the complete structured Wiki export is bundled (**39,786 rows / 2,244 owners**), including Small salvage. Opt-in whole-catalogue refresh stages paginated results, retains last-good data, and supplements exact-name source lookups. Schema 2 stores immutable published-rate history for new raw observations; older aggregates retain their counts without invented rate history. **22 tests pass** following this update. No source probability model has been certified. See README for refresh costs and coverage limitations.

Runtime: RuneLite **1.13.1**, Gradle wrapper **8.10**, JDK **17.0.17**, Java output **11**. RuneLite version is pinned in `build.gradle`; use `recordRuntime` to verify a build.

Implemented: native expandable Clean list; raw processed-event tracking for non-player sources; profile-specific lifetime snapshots and session aggregates; exact-fraction Wiki catalogue; opt-in serialized refresh; provenance/tooltips; GE/HA; test-only fixture launcher; guarded statistics and source-model interface.

Automated coverage: **19 passing tests** for numerical golden examples and boundary cases; immutable observations/repeats/empty/batch/split aggregation; rate segmentation; noted/unnoted identity retention; storage isolation and corrupt recovery; complete audited source parsing; unknown/variant handling; opt-in HTTP/coalescing/cache/schema failures; native inline expansion and standard-width layout. The fixture client started successfully and RuneLite logged `FixturePlugin is now running`; this establishes client loading, not gameplay correctness.

Production certification: **none**. No exact production luck calculation is enabled. The fixture is development-only and cannot write account data. User gameplay validation, narrow adapters where evidence calls for them, exact mechanics certification and Hub review remain outstanding.

Production startup checked on 3 October 2026: RuneLite logged `LootLuckPlugin is now running` and completed client initialization. The local production client was left open for user review. A nonfatal RuneLite-core `ReflectUtil` debug message (`NoSuchFieldException: root`) occurs on JDK 17 during annotation-cache cleanup; plugin startup succeeds. This is not a gameplay-validation result.
