package com.lootluck;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(LootLuckConfig.GROUP)
public interface LootLuckConfig extends Config
{
	String GROUP="loot-luck";
	@ConfigItem(keyName="groupByDropTable",name="Group by drop table",description="Keep items from the same published table together without table headers. Sorting applies within each table.")
	default boolean groupByDropTable() { return false; }
	@ConfigItem(keyName="wikiRefresh",name="Allow Wiki refresh",description="Refresh the full public drop catalogue and source tables from the OSRS Wiki. Sends IP and game-content queries; never account names or loot history.",warning="This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
	default boolean wikiRefresh() { return false; }
	@ConfigItem(keyName="includeUnobtained",name="Include unobtained",description="Include known source-table items with no observations. Always enabled for driest sorting.")
	default boolean includeUnobtained() { return false; }
	@ConfigItem(keyName="showAllRates",name="Show all known rates",description="Show known published rate alternatives beneath item names. Full table and recorded-rate breakdowns remain in expanded details.")
	default boolean showAllRates() { return false; }
	@ConfigItem(keyName="showWikiLinks",name="Show source Wiki links",description="Show a Wiki button in source headers. Clicking opens the source page in your browser.")
	default boolean showWikiLinks() { return true; }
	@ConfigItem(keyName="iconQuantities",name="Quantity on icons (trial)",description="Use native game quantity text on icons and show occurrences on the right. Disable to restore right-aligned quantities. Game colors vary by stack size.")
	default boolean iconQuantities() { return true; }
	@ConfigItem(keyName="stackCounts",name="Quantity and occurrences on right (trial)",description="Yellow quantity on the right, with occurrences below when quantity exceeds occurrences. Overrides Quantity on icons; disable to restore that setting.")
	default boolean stackCounts() { return true; }
	@net.runelite.client.config.Range(min=0)
	@ConfigItem(keyName="historyLimit",name="Collection history limit",description="Maximum retained loot notifications per account and session. 0 means unlimited. Lowering this prunes oldest history; lifetime totals stay intact. Unlimited uses increasing disk and memory.")
	default int historyLimit() { return 10000; }
	@ConfigItem(keyName="luckColors",name="Luck colors",description="Color actual/expected ratios. Below 0.9x red; 0.9–1.1x neutral; above 1.1x green. Not statistical significance.")
	default boolean luckColors() { return true; }
	@ConfigItem(keyName="showGe",name="Show GE values",description="Current GE totals in source headers and expanded items.")
	default boolean showGe() { return true; }
	@ConfigItem(keyName="showHa",name="Show HA values",description="High Alchemy totals in expanded items, excluding rune costs.")
	default boolean showHa() { return false; }
}
