package com.lootluck.rates;

import com.lootluck.model.ObservationContext;
import com.lootluck.model.SourceIdentity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Resolve tables from independent source context, never from the received item. */
public final class TableResolver
{
	private TableResolver() { }
	public static final class Resolution
	{
		public final List<RateRow> rows;
		public final boolean resolved;
		public final String reason;
		private Resolution(List<RateRow> rows, boolean resolved, String reason)
		{ this.rows = List.copyOf(rows); this.resolved = resolved; this.reason = reason; }
	}
	public static Resolution resolve(SourceIdentity source, List<RateRow> rows)
	{
		if (rows.isEmpty()) { return new Resolution(rows, false, "No source-page rate data"); }
		ObservationContext context = ObservationContext.from(source);
		if ("NPC".equals(source.type))
		{
			rows = rows.stream().filter(row -> !row.mechanics.containsKey("Drop type") || "combat".equals(row.mechanics.get("Drop type"))).collect(java.util.stream.Collectors.toList());
		}
		else if ("PICKPOCKET".equals(source.type))
		{
			rows = rows.stream().filter(row -> "thieving".equals(row.mechanics.get("Drop type"))).collect(java.util.stream.Collectors.toList());
		}
		if (rows.isEmpty()) { return new Resolution(rows, false, "No table matches this source event type"); }
		List<RateRow> common=new ArrayList<>();
		if("NPC".equals(source.type)&&context.combatLevel!=null&&rows.stream().anyMatch(row->!row.owner.equals(row.variant)))
		{
			for(RateRow row:rows) { if(row.owner.equals(row.variant)&&contains(row.mechanics.getOrDefault("Drop level",""),context.combatLevel)) { common.add(row); } }
			rows=new ArrayList<>(rows);rows.removeAll(common);
			if(rows.isEmpty()) { return new Resolution(common,true,"Shared table applies to the recorded combat level"); }
		}
		Map<String, List<RateRow>> tables = new LinkedHashMap<>();
		for (RateRow row : rows) { tables.computeIfAbsent(row.variant == null ? "" : row.variant, k -> new ArrayList<>()).add(row); }
		List<RateRow> selected = new ArrayList<>();
		boolean missing = false;
		for (Map.Entry<String, List<RateRow>> table : tables.entrySet())
		{
			String ids = table.getValue().get(0).mechanics.getOrDefault("Table NPC IDs", "");
			if (context.npcId != null && !ids.isBlank())
			{
				if (contains(ids, context.npcId)) { selected.addAll(table.getValue()); }
				continue;
			}
			String levels = publishedLevels(table.getValue());
			if(levels.isBlank()) { levels=table.getValue().get(0).mechanics.getOrDefault("Table combat levels", ""); }
			if (levels.isBlank())
			{
				int anchor = table.getKey().indexOf('#');
				String label = anchor < 0 ? "" : table.getKey().substring(anchor + 1);
				if (Pattern.matches("Levels? \\d+(?:(?:, | and )\\d+)*", label)) { levels = label.replaceAll("[^\\d, ]", "").trim().replaceAll(" +", ","); }
			}
			if (tables.size() > 1 && (levels.isBlank() || context.combatLevel == null)) { missing = true; }
			if (levels.isBlank() || context.combatLevel == null || contains(levels, context.combatLevel)) { selected.addAll(table.getValue()); }
		}
		if(!missing&&selected.isEmpty()&&!common.isEmpty()) { return new Resolution(common,true,"Shared table applies; no variant table matches this NPC"); }
		if (missing || selected.isEmpty()) { List<RateRow> candidates=new ArrayList<>(rows);candidates.addAll(common);return new Resolution(candidates, false, "Applicable table unresolved: missing combat level or source-specific condition"); }
		long candidates = selected.stream().map(row -> row.variant).distinct().count();
		if (candidates != 1) { selected.addAll(common);return new Resolution(selected, false, "More than one table matches the recorded context"); }
		selected.addAll(common);
		if (context.members != null)
		{
			selected.removeIf(row -> context.members ? "(f)".equals(row.worldMarker()) : "(m)".equals(row.worldMarker()));
		}
		return new Resolution(selected, true, "Table resolved from recorded source context; item conditions still apply");
	}
	private static boolean contains(String list, int number)
	{
		for (String part : list.split("[, ]+")) { if (part.equals(Integer.toString(number))) { return true; } }
		return false;
	}
	private static String publishedLevels(List<RateRow> rows)
	{
		java.util.Set<Integer> common=null;
		for(RateRow row:rows)
		{
			String value=row.mechanics.getOrDefault("Drop level","");if(!value.matches("\\d+(?:[, ]+\\d+)*")) { return ""; }
			java.util.Set<Integer> levels=new java.util.TreeSet<>();
			try { for(String part:value.split("[, ]+")) { levels.add(Integer.parseInt(part)); } }catch(NumberFormatException ex) { return ""; }
			if(common==null) { common=levels; }else if(!common.equals(levels)) { return ""; }
		}
		return common==null?"":common.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
	}
}
