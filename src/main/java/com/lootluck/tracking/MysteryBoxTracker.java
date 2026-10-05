package com.lootluck.tracking;

import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/** Short-lived, passive observation of one explicitly opened random-event box. */
public final class MysteryBoxTracker
{
	private Map<Integer,Long> before;
	private int deadline;
	private boolean confirmed;
	public void arm(Map<Integer,Long> inventory,int tick)
	{
		clear();if(inventory.getOrDefault(ItemID.MACRO_QUIZ_MYSTERY_BOX,0L)>0) { before=new LinkedHashMap<>(inventory);deadline=tick+5; }
	}
	public void message(String message)
	{
		if(before!=null&&message.startsWith("Inside the box you find ")) { confirmed=true; }
	}
	public Map<Integer,Long> poll(Map<Integer,Long> inventory,int tick)
	{
		if(before==null) { return Map.of(); }
		if(tick>deadline) { clear();return Map.of(); }
		if(!confirmed||before.getOrDefault(ItemID.MACRO_QUIZ_MYSTERY_BOX,0L)-inventory.getOrDefault(ItemID.MACRO_QUIZ_MYSTERY_BOX,0L)!=1) { return Map.of(); }
		Map<Integer,Long> reward=new LinkedHashMap<>();inventory.forEach((id,quantity)->{long gained=quantity-before.getOrDefault(id,0L);if(id!=ItemID.MACRO_QUIZ_MYSTERY_BOX&&gained>0) { reward.put(id,gained); }});
		if(!reward.isEmpty()) { clear(); }
		return reward;
	}
	public void clear() { before=null;confirmed=false; }
	public boolean pending() { return before!=null; }
}
