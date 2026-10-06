package com.lootluck.tracking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;

/** Bounded death snapshots; ambiguous same-ID deaths never acquire a guessed location. */
public final class NpcDeathContexts
{
	private static final class Death
	{
		int id,tick;String context;
	}
	private final Map<Integer,Death> deaths=new LinkedHashMap<>();
	public void clear() { deaths.clear(); }
	public void record(int index,int id,int tick,WorldPoint point,Boolean wilderness)
	{
		prune(tick);if(point==null) { return; }
		Death death=new Death();death.id=id;death.tick=tick;
		death.context=";deathLocation="+point.getX()+","+point.getY()+","+point.getPlane()+";wilderness="+(wilderness==null?"unknown":wilderness);
		deaths.put(index,death);while(deaths.size()>128) { deaths.remove(deaths.keySet().iterator().next()); }
	}
	public String consume(int id,int tick)
	{
		prune(tick);List<Death> matches=deaths.values().stream().filter(death->death.id==id).collect(Collectors.toList());
		deaths.values().removeIf(death->death.id==id);
		return matches.size()==1?matches.get(0).context:"";
	}
	private void prune(int tick) { deaths.values().removeIf(death->tick<death.tick||tick-death.tick>5); }
	/** Conservative ordinary-map classification, corroborated by the death-time Wilderness flag.
	 * Boundary tiles, safe enclaves, instances and unrecognized dungeons remain unknown. */
	public static Boolean wilderness(WorldPoint npc,WorldPoint player,boolean playerWild,boolean instanced)
	{
		if(instanced||npc==null||player==null||npc.getPlane()!=player.getPlane()||npc.distanceTo(player)>64) { return null; }
		int x=npc.getX(),y=npc.getY();
		if(x<2000||x>=3400) { return null; }
		if(y>=2000&&y<3520) { return false; }
		// Ordinary underground map south of the Wilderness boundary (includes Taverley).
		if(!playerWild&&y>=9400&&y<9920&&player.getY()>=9400&&player.getY()<9920) { return false; }
		if(playerWild&&x>=2944&&x<3392&&y>=3552&&y<3968&&player.getY()>=3552&&player.getY()<3968)
		{
			if((x>=3100&&x<3180&&y>=3580&&y<3660)||(x>=3070&&x<3140&&y>=3900)) { return null; }
			return true;
		}
		return null;
	}
}
