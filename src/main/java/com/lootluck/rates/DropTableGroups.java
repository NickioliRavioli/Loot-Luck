package com.lootluck.rates;

/** Bucket variants retain table identity; Wintertodt material sections share one variant. */
public final class DropTableGroups
{
	private DropTableGroups() { }
	public static String table(RateRow row)
	{
		if("Reward Cart".equals(row.owner))
		{
			String name=row.itemName.toLowerCase(java.util.Locale.ROOT);
			if(name.endsWith(" logs")) { return "Logs"; }
			if(name.startsWith("uncut ")) { return "Gems"; }
			if(name.endsWith(" ore")||name.equals("coal")||name.equals("pure essence")||name.equals("limestone")) { return "Ores"; }
			if(name.startsWith("grimy ")) { return "Herbs"; }
			if(name.endsWith(" seed")||name.equals("acorn")) { return "Seeds"; }
			if(name.startsWith("raw ")) { return "Fish"; }
			if(name.equals("coins")||name.equals("saltpetre")||name.equals("dynamite")) { return "Other"; }
			return "Unique rewards";
		}
		return row.variant==null||row.variant.isBlank()?"Published table":row.variant;
	}
}
