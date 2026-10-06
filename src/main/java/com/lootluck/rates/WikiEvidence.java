package com.lootluck.rates;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Bundled revision-backed page metadata supplements gaps in the structured export. */
public final class WikiEvidence
{
	private final JsonObject pages;
	public WikiEvidence(Gson gson)
	{
		try (Reader reader = new InputStreamReader(java.util.Objects.requireNonNull(
			getClass().getResourceAsStream("/rates/page-evidence.json")), StandardCharsets.UTF_8))
		{ pages = gson.fromJson(reader, JsonObject.class); }
		catch (java.io.IOException ex) { throw new IllegalStateException("Cannot read Wiki evidence", ex); }
	}
	public void apply(List<RateRow> rows)
	{
		if (rows.isEmpty()) { return; }
		JsonObject sourcePage = pages.getAsJsonObject(rows.get(0).owner);
		if (sourcePage == null || !sourcePage.has("exportHash") || !fingerprint(rows).equals(sourcePage.get("exportHash").getAsString())) { return; }
		for (RateRow row : rows)
		{
			if (!pages.has(row.owner)) { continue; }
			JsonObject page = pages.getAsJsonObject(row.owner);
			JsonObject levels = page.getAsJsonObject("tableLevels");
			if (levels.has(row.variant))
			{
				StringBuilder value = new StringBuilder();
				for (JsonElement level : levels.getAsJsonArray(row.variant)) { if (value.length() > 0) { value.append(','); } value.append(level.getAsInt()); }
				row.mechanics.put("Table combat levels", value.toString());
			}
			JsonObject ids = page.getAsJsonObject("tableNpcIds");
			if (ids != null && ids.has(row.variant))
			{
				StringBuilder value = new StringBuilder();
				for (JsonElement id : ids.getAsJsonArray(row.variant)) { if (value.length() > 0) { value.append(','); } value.append(id.getAsInt()); }
				row.mechanics.put("Table NPC IDs", value.toString());
			}
			row.mechanics.put("Wiki page revision", page.get("revision").getAsString());
			row.mechanics.put("Wiki page evidence hash", page.get("sha256").getAsString());
			if (page.has("corrections")) { for (JsonElement element : page.getAsJsonArray("corrections"))
			{
				JsonObject correction = element.getAsJsonObject();
				if (row.variant.equals(correction.get("table").getAsString()) && row.itemName.equals(correction.get("item").getAsString()) &&
					row.quantity.equals(correction.get("quantity").getAsString()) && row.rawRate.equals(correction.get("before").getAsString()))
				{
					row.mechanics.put("Structured export rate", row.rawRate);
					row.rawRate = correction.get("after").getAsString();
					row.hash = WikiParser.hash(row.hash + "|page=" + page.get("revision").getAsString() + "|rate=" + row.rawRate);
				}
			} }
			for (JsonElement element : page.getAsJsonArray("rows"))
			{
				JsonObject expected = element.getAsJsonObject();
				if (!row.variant.equals(expected.get("table").getAsString()) ||
					!ItemRateMatcher.lookupName(row.itemName).equals(ItemRateMatcher.lookupName(expected.get("item").getAsString())) ||
					!row.rawRate.equals(expected.get("rate").getAsString()) || !row.quantity.equals(expected.get("quantity").getAsString())) { continue; }
				String notes = expected.get("notes").getAsString().trim();
				if (!notes.isEmpty()) { row.mechanics.put("Source page conditions", notes); }
			}
		}
	}
	public static String fingerprint(List<RateRow> rows)
	{
		return WikiParser.hash(rows.stream().map(row -> String.join("\u001f", row.variant, row.itemName,
			row.mechanics.getOrDefault("Structured export rate", row.rawRate), row.quantity, row.conditions, row.mechanics.getOrDefault("Rolls", "")))
			.sorted().collect(java.util.stream.Collectors.joining("\n")));
	}
}
