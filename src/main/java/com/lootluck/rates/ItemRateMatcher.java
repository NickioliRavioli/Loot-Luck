package com.lootluck.rates;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Noted items are separate game identities; never select a row using aggregate quantity. */
public final class ItemRateMatcher
{
	private ItemRateMatcher() { }
	/** Lookup aliases only: never rewrite stored identities or reward-casket names. */
	public static String lookupName(String name)
	{
		String normalized=normalize(name);
		if(normalized.matches("scroll box \\((beginner|easy|medium|hard|elite|master)\\)")) { return "clue scroll"+normalized.substring("scroll box".length()); }
		return normalized;
	}
	public static String normalize(String name)
	{
		if (name == null) { return ""; }
		return java.text.Normalizer.normalize(net.runelite.client.util.Text.removeTags(name), java.text.Normalizer.Form.NFC)
			.replace("&nbsp;", " ").replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")
			.replace('\u00a0', ' ').replace('\u2019', '\'').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}
	/** ID evidence takes precedence; name fallback uses Wiki infobox game names. */
	public static List<RateRow> item(int id, int canonicalId, String name, com.lootluck.model.SourceIdentity source, List<RateRow> rows)
	{
		List<RateRow> byId = rows.stream().filter(row -> row.itemIds.contains(canonicalId) || row.itemIds.contains(id)).collect(Collectors.toList());
		if (!byId.isEmpty()) { return match(name, byId, false); }
		String key = lookupName(baseName(name), source);
		return match(name, rows.stream().filter(row -> (!row.itemName.contains("#") || row.itemIds.isEmpty()) &&
			(key.equals(lookupName(row.itemName)) || (row.gameName != null && key.equals(lookupName(row.gameName))))).collect(Collectors.toList()), false);
	}
	public static String lookupName(String name,com.lootluck.model.SourceIdentity source)
	{
		String normalized=lookupName(name);
		if("EVENT".equals(source.type)&&"Reward pool".equalsIgnoreCase(RewardSources.wikiOwner(source))&&"casket".equals(normalized)) { return "casket (reward pool)"; }
		return normalized;
	}
	public static boolean notedName(String name) { return name.toLowerCase(Locale.ROOT).endsWith(" (noted)"); }
	public static String baseName(String name) { return notedName(name)?name.substring(0,name.length()-8):name; }
	public static boolean notedRow(RateRow row) { String quantity=row.quantity==null?"":row.quantity.toLowerCase(Locale.ROOT);return quantity.contains("noted")&&!quantity.contains("unnoted"); }
	public static List<RateRow> match(String name,List<RateRow> rows,boolean ambiguousLegacy)
	{
		if(rows==null) { return List.of(); }
		if(ambiguousLegacy) { return rows; }
		boolean noted=notedName(name);return rows.stream().filter(row->notedRow(row)==noted).collect(Collectors.toList());
	}
	/** Match a notification quantity only when it can represent one row/roll. */
	public static List<RateRow> matchCollection(String name,List<RateRow> rows,long quantity,long amount)
	{
		List<RateRow> candidates=match(name,rows,false);
		if(amount!=1||quantity<=0||candidates.stream().anyMatch(row->!"1".equals(row.mechanics.get("Rolls")))) { return List.of(); }
		return candidates.stream().filter(row->{
			try {
				String low=row.mechanics.get("Quantity Low"),high=row.mechanics.get("Quantity High");
				if(low!=null&&high!=null) { return quantity>=Long.parseLong(low)&&quantity<=Long.parseLong(high); }
				String text=row.quantity==null?"":row.quantity.replace(" (noted)","").trim();
				String[] bounds=text.split("[-–—]");
				return bounds.length==1?quantity==Long.parseLong(bounds[0].trim()):bounds.length==2&&quantity>=Long.parseLong(bounds[0].trim())&&quantity<=Long.parseLong(bounds[1].trim());
			} catch(NumberFormatException ex) { return false; }
		}).collect(Collectors.toList());
	}
}

