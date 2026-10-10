package com.lootluck.tracking;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;

/** Passive, bounded inventory observation following a player-selected Maze object. */
public final class MazeRewardTracker
{
	public enum Reward
	{
		CHEST("Chest (Maze)","Chest (Maze)"), COMPLETION("Maze reward","Strange shrine");
		public final String name, wikiOwner;
		Reward(String name,String wikiOwner) { this.name=name;this.wikiOwner=wikiOwner; }
	}
	private static final Map<Integer,Long> CHEST_ITEMS=Map.of(
		ItemID.AIRRUNE,15L,ItemID.WATERRUNE,10L,ItemID.EARTHRUNE,10L,ItemID.FIRERUNE,10L,
		ItemID.BRONZE_ARROW,20L,ItemID.BOLT,10L,ItemID.IRON_ARROW,15L,
		ItemID._2DOSE1ATTACK,1L,ItemID._2DOSE1STRENGTH,1L,ItemID._2DOSE1DEFENSE,1L);
	private static final Set<Integer> COMPLETION_ITEMS=Set.of(ItemID.COINS,ItemID.FEATHER,
		ItemID.IRON_ARROW,ItemID.STEEL_ARROW,ItemID.CHAOSRUNE,ItemID.DEATHRUNE,
		ItemID.NATURERUNE,ItemID.COAL,ItemID.MITHRIL_ORE);
	private Map<Integer,Long> before;
	private Reward reward;
	private int deadline;
	private boolean approached,departed;

	public static Reward interaction(int objectId,String option)
	{
		if(objectId==ObjectID.MACRO_MAZE_CHEST&&"Open".equals(option)) { return Reward.CHEST; }
		if(objectId==ObjectID.MACRO_MAZE_COMPLETE&&"Touch".equals(option)) { return Reward.COMPLETION; }
		return null;
	}
	public void arm(Reward reward,Map<Integer,Long> inventory,int tick,boolean nearby)
	{
		clear();this.reward=reward;before=new LinkedHashMap<>(inventory);deadline=tick+100;approached=nearby;
	}
	/** Normal scene loading after touching the shrine must not discard its reward snapshot. */
	public void loading(int tick)
	{
		if(reward==Reward.COMPLETION&&approached) { departed=true;deadline=tick+5; }
		else { clear(); }
	}
	public Map<Integer,Long> poll(Map<Integer,Long> inventory,int tick,boolean nearby,IntUnaryOperator canonicalId)
	{
		if(before==null) { return Map.of(); }
		if(tick>deadline) { clear();return Map.of(); }
		if(!nearby&&!departed)
		{
			// Walking to a selected object is not a reward; keep the baseline current.
			if(approached) { clear(); } else { before=new LinkedHashMap<>(inventory); }
			return Map.of();
		}
		approached=true;
		Map<Integer,Long> gained=new LinkedHashMap<>();
		inventory.forEach((id,quantity)->{long delta=quantity-before.getOrDefault(id,0L);if(delta>0) { gained.put(id,delta); }});
		if(gained.isEmpty()) { return Map.of(); }
		boolean valid=reward==Reward.CHEST?gained.size()==1:gained.size()<=3;
		for(Map.Entry<Integer,Long> item:gained.entrySet())
		{
			int id=canonicalId.applyAsInt(item.getKey());
			valid&=reward==Reward.CHEST?item.getValue().equals(CHEST_ITEMS.get(id)):COMPLETION_ITEMS.contains(id);
		}
		// Any unexpected positive change makes this observation ambiguous; never guess its source.
		clear();return valid?gained:Map.of();
	}
	public Reward reward() { return reward; }
	public boolean pending() { return before!=null; }
	public void clear() { before=null;reward=null;approached=false;departed=false; }
}
