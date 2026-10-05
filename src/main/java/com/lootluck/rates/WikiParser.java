package com.lootluck.rates;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Shared parser for generator and runtime. Preserve mechanics instead of flattening conditions. */
public final class WikiParser
{
	private WikiParser() { }
	public static List<RateRow> parse(Gson gson, String body, String expectedOwner, String fetchedAt)
	{
		JsonObject root = gson.fromJson(body, JsonObject.class);
		if (root == null || !root.has("bucket") || !root.get("bucket").isJsonArray()) { throw new IllegalArgumentException("Missing Bucket rows"); }
		JsonArray bucket = root.getAsJsonArray("bucket");
		if (bucket.size() == 0 || bucket.size() > 10000) { throw new IllegalArgumentException("Missing or oversized source table"); }
		List<RateRow> rows = new ArrayList<>();
		for (JsonElement entry : bucket)
		{
			JsonObject raw = entry.getAsJsonObject();
			if (!expectedOwner.equals(text(raw, "page_name"))) { throw new IllegalArgumentException("Wrong Wiki owner"); }
			JsonObject drop = gson.fromJson(text(raw, "drop_json"), JsonObject.class);
			if (drop == null || !drop.has("Rarity")) { throw new IllegalArgumentException("Malformed drop row"); }
			RateRow row = new RateRow(); row.owner = expectedOwner; row.variant = text(raw,"page_name_sub");
			row.itemName = text(raw,"item_name"); row.rawRate = text(drop,"Rarity"); row.quantity = text(drop,"Drop Quantity");
			if (row.itemName.isBlank() || row.rawRate.isBlank()) { throw new IllegalArgumentException("Empty identity/rate"); }
			for (java.util.Map.Entry<String,JsonElement> field : drop.entrySet()) { row.mechanics.put(field.getKey(), field.getValue().isJsonPrimitive() ? field.getValue().getAsString() : field.getValue().toString()); }
			row.mechanics.put("Rare drop table", text(raw,"rare_drop_table"));
			row.conditions = String.join(" ", text(drop,"Rarity Notes"), text(drop,"Name Notes"), text(drop,"Alt Rarity"), text(drop,"Alt Rarity Dash")).trim();
			row.approximate = "true".equalsIgnoreCase(text(drop,"Approx")); row.fetchedAt = fetchedAt;
			row.url = "https://oldschool.runescape.wiki/w/" + expectedOwner.replace(' ', '_');
			row.hash = hash(entry.toString()); rows.add(row);
		}
		Instant.parse(fetchedAt); return rows;
	}
	private static String text(JsonObject object, String key) { JsonElement e = object.get(key); return e == null || e.isJsonNull() ? "" : e.getAsString(); }
	public static String hash(String value)
	{
		try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(); for (byte b : bytes) { out.append(String.format("%02x", b)); } return out.toString(); }
		catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
	}
}
