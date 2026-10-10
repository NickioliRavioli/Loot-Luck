package com.lootluck;

import com.lootluck.model.*;
import com.lootluck.rates.*;
import com.lootluck.tracking.*;
import java.util.*;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class RogueOutfitTest
{
	private SourceIdentity source(String context) { return new SourceIdentity("PICKPOCKET","Master Farmer",context,"[MEMBERS]"); }
	private RateRow row(String quantity,String hash)
	{
		RateRow row=new RateRow();row.owner="Master Farmer";row.variant="Master Farmer";row.itemName="Hammerstone hop seed";row.quantity=quantity;row.hash=hash;row.rawRate="1/10";row.conditions="";row.fetchedAt="2026-10-10";row.mechanics.put("Rolls","1");row.mechanics.put("Drop type","thieving");return row;
	}
	@Test public void chatEvidenceResolvesThePreviouslyAmbiguousQuantity()
	{
		RateRow one=row("1","one"),two=row("2","two");List<RateRow> rows=List.of(one,two);
		assertEquals(List.of(one),ItemRateMatcher.matchCollection(one.itemName,rows,2,1,source("rogueDoubled=true;roguePieces=3"),false));
		assertEquals(List.of(two),ItemRateMatcher.matchCollection(one.itemName,rows,2,1,source("rogueDoubled=false;roguePieces=3"),false));
		assertEquals(2,ItemRateMatcher.matchCollection(one.itemName,rows,2,1,source("roguePieces=3"),false).size());
		assertEquals(2,ItemRateMatcher.matchCollection(one.itemName,rows,2,1,source(""),false).size());
		assertTrue(ItemRateMatcher.matchCollection(one.itemName,rows,3,1,source("rogueDoubled=true"),false).isEmpty());
		assertTrue(ItemRateMatcher.matchCollection(one.itemName,rows,2,2,source("rogueDoubled=true"),false).isEmpty());
	}
	@Test public void everyOutfitSizeAndKillLootAreHandledWithoutChangingRarity()
	{
		for(int pieces=0;pieces<=5;pieces++)
		{
			long[] expected=pieces==0?new long[]{2}:pieces==5?new long[]{1}:new long[]{2,1};
			assertArrayEquals(expected,RogueOutfit.baseQuantities(source("roguePieces="+pieces),"Seed",2,false));
		}
		assertEquals(2,RogueOutfit.countPieces(new int[]{ItemID.ROGUESDEN_BODY,ItemID.ROGUESDEN_HELM,ItemID.ROGUESDEN_HELM,ItemID.COINS}));
		assertArrayEquals(new long[]{2},RogueOutfit.baseQuantities(new SourceIdentity("NPC","Guard","rogueDoubled=true","[MEMBERS]"),"Coins",2,false));
		assertArrayEquals(new long[0],RogueOutfit.baseQuantities(new SourceIdentity("PICKPOCKET","Guard","rogueDoubled=true","[MEMBERS, LEAGUE]"),"Coins",2,false));
	}
	@Test public void pouchesExtraCoinsAndCluesAreNotHalved()
	{
		SourceIdentity doubled=source("rogueDoubled=true;roguePieces=5");
		assertArrayEquals(new long[]{1},RogueOutfit.baseQuantities(doubled,"Coin pouch",1,true));
		assertArrayEquals(new long[]{50},RogueOutfit.baseQuantities(doubled,"Coins",50,true));
		assertArrayEquals(new long[]{1},RogueOutfit.baseQuantities(doubled,"Scroll box (easy)",1,false));
		assertArrayEquals(new long[]{1},RogueOutfit.baseQuantities(doubled,"Clue scroll (easy)",1,false));
	}
	@Test public void doubledUnitsPersistWithoutBecomingTwoOccurrencesOrChangingPublishedRows()
	{
		RateRow row=row("1","one");LedgerEngine engine=new LedgerEngine(new Ledger());SourceIdentity doubled=source("rogueDoubled=true;roguePieces=3");
		engine.observe(new Observation(doubled,1,1,Map.of(ItemID.HAMMERSTONE_HOP_SEED,2L),Map.of(ItemID.HAMMERSTONE_HOP_SEED,row.itemName),Map.of()),AttemptAdapter.Assessment.unsupported("pending"),List.of(row));
		Ledger.Item item=engine.lifetime().source(doubled).items.get(ItemID.HAMMERSTONE_HOP_SEED);assertEquals(2,item.quantity);assertEquals(1,item.occurrences);
		RateRow snapshot=item.recordedRates.values().iterator().next().rows.get(0);assertEquals("1/10",snapshot.rawRate);assertEquals("1",snapshot.quantity);assertTrue(snapshot.tooltip().contains("confirmed by chat"));assertFalse(row.mechanics.containsKey("Recorded loot modifier"));
	}
}
