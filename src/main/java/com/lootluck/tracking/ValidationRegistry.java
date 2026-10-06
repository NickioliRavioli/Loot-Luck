package com.lootluck.tracking;

import com.lootluck.model.Observation;

/** Reviewed Wiki models remain gated until the user confirms real tracking for that source. */
public final class ValidationRegistry implements AttemptAdapter
{
	private final java.util.Map<String, Model> models;
	private static final class Model
	{
		java.util.Set<String> items,exclusiveItems;
	}
	public ValidationRegistry() { models=java.util.Map.of(); }
	public ValidationRegistry(com.google.gson.Gson gson)
	{
		try(java.io.Reader reader=new java.io.InputStreamReader(java.util.Objects.requireNonNull(getClass().getResourceAsStream("/rates/source-models.json")),java.nio.charset.StandardCharsets.UTF_8))
		{ models=gson.fromJson(reader,new com.google.gson.reflect.TypeToken<java.util.Map<String,Model>>(){}.getType()); }
		catch(java.io.IOException ex) { throw new IllegalStateException("Cannot read reviewed source models",ex); }
	}
	public boolean candidate(String owner) { return models.containsKey(owner); }
	public String contract(com.lootluck.model.SourceIdentity source, java.util.List<com.lootluck.rates.RateRow> rows)
	{
		String owner = com.lootluck.rates.RewardSources.wikiOwner(source);
		if (!"NPC".equals(source.type) || !candidate(owner) || rows.isEmpty() || !rows.get(0).mechanics.containsKey("Wiki page revision")) { return ""; }
		Model model=models.get(owner);
		return com.lootluck.rates.WikiParser.hash("server-npc-loot-1.13.1|wiki-presence-v1|" + owner + "|" + com.lootluck.rates.WikiEvidence.fingerprint(rows) + "|" + rows.get(0).mechanics.get("Wiki page evidence hash") + "|" + new java.util.TreeSet<>(model.items) + "|" + new java.util.TreeSet<>(model.exclusiveItems));
	}
	public Assessment assess(Observation observation, java.util.List<com.lootluck.rates.RateRow> rows, String approvedContract)
	{
		String required = contract(observation.source, rows);
		if (required.isEmpty() || !required.equals(approvedContract)) { return Assessment.unsupported("Source tracking requires gameplay validation, or its Wiki evidence changed"); }
		Model model=models.get(com.lootluck.rates.RewardSources.wikiOwner(observation.source));
		return WikiLuckModel.assess(observation, rows, model.items, model.exclusiveItems);
	}
	@Override public Assessment assess(Observation observation)
	{
		return Assessment.unsupported(observation.reportedAmount != 1 ? "Batched or unknown attempt boundary" :
			"Attempt boundary and event probability require validation for this source/context");
	}
}
