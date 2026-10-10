package com.lootluck.rates;

import com.lootluck.model.SourceIdentity;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Event-only aliases: persisted identities and distinct reward containers stay intact. */
public final class RewardSources
{
	private static final Map<String,String> OWNERS=new LinkedHashMap<>();
	static
	{
		for(String tier:new String[]{"beginner","easy","medium","hard","elite","master"})
		{
			OWNERS.put("clue scroll ("+tier+")","Reward casket ("+tier+")");
		}
		OWNERS.put("reward cart (wintertodt)","Reward Cart");
		OWNERS.put("reward pool (tempoross)","Reward pool");
		OWNERS.put("casket (tempoross)","Casket (Reward pool)");
		OWNERS.put("guardians of the rift","Rewards Guardian");
		// Supply crate (Wintertodt) is shared by old and extra crates; no safe name-only alias.
	}
	private RewardSources() { }
	public static String wikiOwner(SourceIdentity source)
	{
		if(source.wikiOwner!=null&&!source.wikiOwner.isBlank()) { return source.wikiOwner; }
		return "EVENT".equals(source.type)?OWNERS.getOrDefault(source.name.toLowerCase(Locale.ROOT),source.name):source.name;
	}
	public static String displayName(SourceIdentity source)
	{
		if("EVENT".equals(source.type)&&"Maze reward".equals(source.name)&&"Strange shrine".equals(source.wikiOwner)) { return source.name; }
		String owner=wikiOwner(source);
		return owner.equals(source.name)?source.name:owner;
	}
}
