package com.lootluck.rates;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Noted items are separate game identities; never select a row using aggregate quantity. */
public final class ItemRateMatcher
{
	private ItemRateMatcher() { }
	private static final java.util.regex.Pattern SPACES=java.util.regex.Pattern.compile("\\s+"),SCROLL_BOX=java.util.regex.Pattern.compile("scroll box \\((beginner|easy|medium|hard|elite|master)\\)");
	private static final java.util.Map<String,String> NORMALIZED=new java.util.LinkedHashMap<String,String>(256,.75f,true) {
		@Override protected boolean removeEldestEntry(java.util.Map.Entry<String,String> entry) { return size()>4096; }
	};
	/** Lookup aliases only: never rewrite stored identities or reward-casket names. */
	public static String lookupName(String name)
	{
		String normalized=normalize(name);
		if(SCROLL_BOX.matcher(normalized).matches()) { return "clue scroll"+normalized.substring("scroll box".length()); }
		return normalized;
	}
	public static String normalize(String name)
	{
		if (name == null) { return ""; }
		if(name.length()<=512) { synchronized(NORMALIZED) { String cached=NORMALIZED.get(name);if(cached!=null) { return cached; } } }
		String normalized=java.text.Normalizer.normalize(net.runelite.client.util.Text.removeTags(name), java.text.Normalizer.Form.NFC)
			.replace("&nbsp;", " ").replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")
			.replace('\u00a0', ' ').replace('\u2019', '\'').trim();
		normalized=SPACES.matcher(normalized).replaceAll(" ").toLowerCase(Locale.ROOT);
		if(name.length()<=512) { synchronized(NORMALIZED) { NORMALIZED.put(name,normalized); } }return normalized;
	}
	/** Built once for a resolved table; preserves row order and ID-first matching. */
	public static final class Index
	{
		private final List<RateRow> rows;
		private final java.util.Map<Integer,List<Integer>> ids=new java.util.HashMap<>();
		private final java.util.Map<String,List<Integer>> names=new java.util.HashMap<>();
		public Index(List<RateRow> rows)
		{
			this.rows=rows;
			for(int i=0;i<rows.size();i++)
			{
				RateRow row=rows.get(i);for(Integer id:new java.util.HashSet<>(row.itemIds)) { ids.computeIfAbsent(id,ignored->new java.util.ArrayList<>()).add(i); }
				if(!row.itemName.contains("#")||row.itemIds.isEmpty())
				{
					String name=lookupName(row.itemName);names.computeIfAbsent(name,ignored->new java.util.ArrayList<>()).add(i);
					if(row.gameName!=null) { String game=lookupName(row.gameName);if(!game.equals(name)) { names.computeIfAbsent(game,ignored->new java.util.ArrayList<>()).add(i); } }
				}
			}
		}
		public List<RateRow> item(int id,int canonicalId,String name,com.lootluck.model.SourceIdentity source)
		{
			List<Integer> positions=ids.get(id),canonical=ids.get(canonicalId);
			if(canonicalId!=id&&canonical!=null)
			{
				java.util.Set<Integer> combined=new java.util.TreeSet<>(canonical);if(positions!=null) { combined.addAll(positions); }positions=new java.util.ArrayList<>(combined);
			}
			if(positions==null||positions.isEmpty()) { positions=names.get(lookupName(baseName(name),source)); }
			if(positions==null) { return List.of(); }
			boolean noted=notedName(name);List<RateRow> result=new java.util.ArrayList<>();
			for(Integer position:positions) { RateRow row=rows.get(position);if(notedRow(row)==noted) { result.add(row); } }return result;
		}
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
	public static List<RateRow> matchCollection(String name,List<RateRow> rows,long quantity,long amount,com.lootluck.model.SourceIdentity source,boolean hasCoinPouch)
	{
		java.util.Set<RateRow> matches=new java.util.LinkedHashSet<>();
		for(long base:RogueOutfit.baseQuantities(source,name,quantity,hasCoinPouch)) { matches.addAll(matchCollection(name,rows,base,amount)); }
		return List.copyOf(matches);
	}
	public static List<RateRow> matchCollection(String name,List<RateRow> rows,long quantity,long amount)
	{
		List<RateRow> candidates=match(name,rows,false);
		if(amount!=1||quantity<=0||candidates.stream().anyMatch(row->!"1".equals(row.mechanics.get("Rolls")))) { return List.of(); }
		return candidates.stream().filter(row->{
			try {
				String text=row.quantity==null?"":row.quantity.replace(" (noted)","").trim();
				text=text.replaceAll("(?<=\\d),(?=\\d{3}(?:\\D|$))", "");
				if(text.matches("\\d+(?:\\s*[,;]\\s*\\d+)+")) {
					for(String option:text.split("[,;]")) { if(quantity==Long.parseLong(option.trim())) { return true; } }
					return false;
				}
				String low=row.mechanics.get("Quantity Low"),high=row.mechanics.get("Quantity High");
				if(low!=null&&high!=null) { return quantity>=Long.parseLong(low)&&quantity<=Long.parseLong(high); }
				String[] bounds=text.split("[-–—]");
				return bounds.length==1?quantity==Long.parseLong(bounds[0].trim()):bounds.length==2&&quantity>=Long.parseLong(bounds[0].trim())&&quantity<=Long.parseLong(bounds[1].trim());
			} catch(NumberFormatException ex) { return false; }
		}).collect(Collectors.toList());
	}
}

