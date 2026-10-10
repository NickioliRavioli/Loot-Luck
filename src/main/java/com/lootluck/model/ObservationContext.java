package com.lootluck.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable public game context; unknown facts are never defaulted to a match. */
public final class ObservationContext
{
	public final Integer combatLevel, npcId, roguePieces;
	public final Boolean members, wilderness;
	public final Boolean rogueDoubled;
	public final boolean standardWorld;
	private ObservationContext(Integer level, Integer id, Boolean members, Boolean wilderness, boolean standard,Integer pieces,Boolean doubled)
	{ combatLevel = level; npcId = id; this.members = members; this.wilderness=wilderness;standardWorld = standard;roguePieces=pieces;rogueDoubled=doubled; }
	public static ObservationContext from(SourceIdentity source)
	{
		Integer level = number(source.variant, "combat"), id = number(source.variant, "metadata");
		String world = source.context;
		Boolean members = world.startsWith("[") ? world.contains("MEMBERS") : null;
		boolean standard = world.matches("\\[(?:MEMBERS)?\\]");
		Matcher area=Pattern.compile("(?:^|;)wilderness=(true|false)(?:;|$)").matcher(source.variant);
		Integer pieces="PICKPOCKET".equals(source.type)?number(source.variant,"roguePieces"):null;
		if(pieces!=null&&pieces>5) { pieces=null; }
		Matcher bonus=Pattern.compile("(?:^|;)rogueDoubled=(true|false)(?:;|$)").matcher(source.variant);
		Boolean doubled="PICKPOCKET".equals(source.type)&&bonus.find()?Boolean.valueOf(bonus.group(1)):null;
		return new ObservationContext(level, "NPC".equals(source.type) ? id : null, members,area.find()?Boolean.valueOf(area.group(1)):null, standard,pieces,doubled);
	}
	private static Integer number(String text, String key)
	{
		Matcher match = Pattern.compile("(?:^|;)" + key + "=(\\d+)(?:;|$)").matcher(text);
		if (!match.find()) { return null; }
		try { return Integer.valueOf(match.group(1)); } catch (NumberFormatException ex) { return null; }
	}
}
