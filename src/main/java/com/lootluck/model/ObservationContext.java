package com.lootluck.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable public game context; unknown facts are never defaulted to a match. */
public final class ObservationContext
{
	public final Integer combatLevel, npcId;
	public final Boolean members;
	public final boolean standardWorld;
	private ObservationContext(Integer level, Integer id, Boolean members, boolean standard)
	{ combatLevel = level; npcId = id; this.members = members; standardWorld = standard; }
	public static ObservationContext from(SourceIdentity source)
	{
		Integer level = number(source.variant, "combat"), id = number(source.variant, "metadata");
		String world = source.context;
		Boolean members = world.startsWith("[") ? world.contains("MEMBERS") : null;
		boolean standard = world.matches("\\[(?:MEMBERS)?\\]");
		return new ObservationContext(level, "NPC".equals(source.type) ? id : null, members, standard);
	}
	private static Integer number(String text, String key)
	{
		Matcher match = Pattern.compile("(?:^|;)" + key + "=(\\d+)(?:;|$)").matcher(text);
		if (!match.find()) { return null; }
		try { return Integer.valueOf(match.group(1)); } catch (NumberFormatException ex) { return null; }
	}
}
