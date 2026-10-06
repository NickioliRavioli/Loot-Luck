package com.lootluck.tracking;

import com.lootluck.model.Observation;
import com.lootluck.model.ObservationContext;
import com.lootluck.rates.ItemRateMatcher;
import com.lootluck.rates.RateRow;
import com.lootluck.rates.TableResolver;
import com.lootluck.rates.WikiParser;
import com.lootluck.stats.Statistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Presence per completion, distinct from quantity and the published per-roll rate. */
public final class WikiLuckModel
{
	private WikiLuckModel() { }
	public static AttemptAdapter.Assessment assess(Observation observation, List<RateRow> allRows, Set<String> eligibleItems)
	{ return assess(observation,allRows,eligibleItems,Set.of()); }
	public static AttemptAdapter.Assessment assess(Observation observation, List<RateRow> allRows, Set<String> eligibleItems, Set<String> exclusiveItems)
	{
		if (observation.reportedAmount != 1) { return AttemptAdapter.Assessment.unsupported("Batched or unknown attempt boundary"); }
		if (!ObservationContext.from(observation.source).standardWorld) { return AttemptAdapter.Assessment.unsupported("Special or unknown world probability modifiers"); }
		TableResolver.Resolution table = TableResolver.resolve(observation.source, allRows);
		if (!table.resolved) { return AttemptAdapter.Assessment.unsupported(table.reason); }
		Map<String, List<RateRow>> items = new LinkedHashMap<>();
		for (RateRow row : table.rows)
		{
			if (eligibleItems.contains(ItemRateMatcher.normalize(row.itemName)))
			{ items.computeIfAbsent(ItemRateMatcher.normalize(row.itemName), k -> new java.util.ArrayList<>()).add(row); }
		}
		Map<Integer, Double> probabilities = new LinkedHashMap<>();
		StringBuilder evidence = new StringBuilder();
		for (List<RateRow> rows : items.values())
		{
			// Multiple rows need an explicitly reviewed exclusivity contract; never blindly sum them.
			boolean exclusive=rows.size()>1&&exclusiveItems.contains(ItemRateMatcher.normalize(rows.get(0).itemName));
			if (rows.size() != 1 && !exclusive) { continue; }
			RateRow row = rows.get(0);
			Double q = row.publishedProbability();
			if(exclusive)
			{
				if(rows.stream().anyMatch(r->r.publishedProbability()==null||r.approximate||r.modelConditionsUnresolved()||!"1".equals(r.mechanics.get("Rolls"))||!r.itemIds.equals(row.itemIds))) { continue; }
				q=rows.stream().mapToDouble(RateRow::publishedProbability).sum();if(q>1) { continue; }
			}
			if (q == null || row.approximate || row.modelConditionsUnresolved() || row.itemIds.isEmpty() ||
				ItemRateMatcher.notedRow(row) || !row.mechanics.containsKey("Wiki page revision")) { continue; }
			int rolls;
			try { rolls = Integer.parseInt(row.mechanics.getOrDefault("Rolls", "")); }
			catch (NumberFormatException ex) { continue; }
			if (rolls < 1 || rolls > 100) { continue; }
			double p = Statistics.multipleRollPresence(q, rolls);
			for (int id : row.itemIds) { probabilities.put(id, p); }
			for(RateRow member:rows) { evidence.append(member.hash).append('|').append(member.mechanics.get("Wiki page evidence hash")).append('\n'); }
		}
		if (probabilities.isEmpty()) { return AttemptAdapter.Assessment.unsupported("No exact item-presence model for this table"); }
		// Retain original game IDs for observed notes without treating stack quantity as successes.
		observation.originalIds.forEach((original, canonical) -> { if (probabilities.containsKey(canonical)) { probabilities.put(original, probabilities.get(canonical)); } });
		return AttemptAdapter.Assessment.certified("wiki-presence-v1", WikiParser.hash(evidence.toString()), probabilities);
	}
}
