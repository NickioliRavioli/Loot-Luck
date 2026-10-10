package com.lootluck.rates;

import com.lootluck.model.ObservationContext;
import com.lootluck.model.SourceIdentity;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/** Rogue equipment changes received units, not the published chance of selecting a reward. */
public final class RogueOutfit
{
	private static final Set<Integer> PIECES=Set.of(ItemID.ROGUESDEN_BODY,ItemID.ROGUESDEN_HELM,
		ItemID.ROGUESDEN_LEGS,ItemID.ROGUESDEN_GLOVES,ItemID.ROGUESDEN_BOOTS);
	private RogueOutfit() { }
	public static int countPieces(int[] equipment)
	{
		Set<Integer> worn=new HashSet<>();for(int id:equipment) { if(PIECES.contains(id)) { worn.add(id); } }
		return worn.size();
	}
	public static boolean coinPouch(String name) { return "coin pouch".equals(ItemRateMatcher.lookupName(ItemRateMatcher.baseName(name))); }
	private static boolean exempt(String name,boolean hasCoinPouch)
	{
		String key=ItemRateMatcher.lookupName(ItemRateMatcher.baseName(name));
		return coinPouch(name)||key.startsWith("clue scroll (")||(hasCoinPouch&&"coins".equals(key));
	}
	public static long[] baseQuantities(SourceIdentity source,String name,long received,boolean hasCoinPouch)
	{
		if(!"PICKPOCKET".equals(source.type)) { return new long[]{received}; }
		ObservationContext context=ObservationContext.from(source);
		if(!context.standardWorld) { return new long[0]; }
		if(exempt(name,hasCoinPouch)||Boolean.FALSE.equals(context.rogueDoubled)||context.rogueDoubled==null&&Integer.valueOf(0).equals(context.roguePieces)) { return new long[]{received}; }
		boolean full=Boolean.TRUE.equals(context.rogueDoubled)||context.rogueDoubled==null&&Integer.valueOf(5).equals(context.roguePieces);
		if(received%2!=0) { return full?new long[0]:new long[]{received}; }
		return full?new long[]{received/2}:new long[]{received,received/2};
	}
	public static String description(SourceIdentity source,String name,boolean hasCoinPouch)
	{
		if(!"PICKPOCKET".equals(source.type)) { return ""; }
		Integer pieces=ObservationContext.from(source).roguePieces;
		String outfit=pieces==null?"Unknown Rogue outfit": "Rogue outfit "+pieces+"/5";
		if(!ObservationContext.from(source).standardWorld) { return outfit+"; world modifiers unresolved"; }
		if(exempt(name,hasCoinPouch)) { return outfit+"; quantity not doubled"+(hasCoinPouch&&"coins".equals(ItemRateMatcher.normalize(name))?" (extra coins accompanying one pouch)":""); }
		Boolean doubled=ObservationContext.from(source).rogueDoubled;
		if(doubled!=null) { return outfit+(doubled?"; double loot confirmed by chat":"; no doubling message in matched success sequence"); }
		return outfit+(pieces==null||pieces>0&&pieces<5?"; normal or doubled quantity":pieces==5?"; doubled quantity":"; normal quantity");
	}
}
