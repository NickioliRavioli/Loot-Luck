package com.lootluck.rates;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Public Wiki infobox IDs and game names; no fuzzy names or guessed IDs. */
public final class WikiItemIndex
{
	private final JsonObject index;
	public WikiItemIndex(Gson gson)
	{
		try (Reader reader = new InputStreamReader(java.util.Objects.requireNonNull(
			getClass().getResourceAsStream("/rates/item-identities.json")), StandardCharsets.UTF_8))
		{ index = gson.fromJson(reader, JsonObject.class); }
		catch (java.io.IOException ex) { throw new IllegalStateException("Cannot read Wiki item identities", ex); }
	}
	public void apply(List<RateRow> rows)
	{
		for (RateRow row : rows)
		{
			JsonObject item = index.getAsJsonObject(ItemRateMatcher.normalize(row.itemName));
			if (item == null) { continue; }
			row.itemIds.clear();
			for (JsonElement id : item.getAsJsonArray("ids")) { row.itemIds.add(id.getAsInt()); }
			row.gameName = item.has("name") ? item.get("name").getAsString() : null;
		}
	}
}
