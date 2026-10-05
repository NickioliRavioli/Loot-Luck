package com.lootluck;

import com.lootluck.rates.*;
import com.lootluck.model.*;
import com.lootluck.tracking.*;
import com.lootluck.ui.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuantityRowsTest
{
	private RateRow row(String table,String quantity,String hash)
	{
		RateRow row=new RateRow();row.variant=table;row.itemName="Coins";row.quantity=quantity;row.rawRate="1/10";row.conditions="";row.hash=hash;row.fetchedAt="2026-10-05T00:00:00Z";row.mechanics.put("Rolls","1");return row;
	}
	@Test public void rangesOverlapBatchesAndMultipleRollsStayDistinct()
	{
		RateRow a=row("One","1–5","a"),b=row("Two","3-10","b");
		assertEquals(List.of(a),ItemRateMatcher.matchCollection("Coins",List.of(a,b),2,1));
		assertEquals(2,ItemRateMatcher.matchCollection("Coins",List.of(a,b),3,1).size());
		assertTrue(ItemRateMatcher.matchCollection("Coins",List.of(a,b),2,2).isEmpty());
		assertTrue(ItemRateMatcher.matchCollection("Coins",List.of(a,b),11,1).isEmpty());
		b.mechanics.put("Rolls","2");assertTrue(ItemRateMatcher.matchCollection("Coins",List.of(a,b),2,1).isEmpty());
	}
	@Test public void breakdownCountsUnitsOnceAndKeepsZeroRows()
	{
		List<RateRow> rates=List.of(row("One","1–5","a"),row("Two","3–10","b"),row("Two","20","c"));
		SourceIdentity source=new SourceIdentity("NPC","Test","id=1","normal");LedgerEngine engine=new LedgerEngine(new Ledger());
		for(long quantity:new long[]{2,3,20}) { engine.observe(new Observation(source,1,1,Map.of(1,quantity),Map.of(1,"Coins"),Map.of(1,1)),AttemptAdapter.Assessment.unsupported("pending"),rates); }
		RateProvider provider=new RateProvider() { public List<RateRow> rows(SourceIdentity identity) { return rates; } public void refresh(SourceIdentity identity,boolean force) {} };
		PanelState.Item item=ViewBuilder.build(engine.lifetime(),provider,false,Map.of()).sources.get(0).items.get(0);
		assertEquals(3,item.unresolvedQuantity);assertEquals(3,item.rateOptions.size());assertEquals(22,item.rateOptions.stream().mapToLong(option->option.quantity==null?0:option.quantity).sum());assertEquals("1–5",item.rateOptions.get(0).dropQuantity);
	}
	@Test public void neverIsAnAlternateNotAOneTimeInference()
	{
		RateRow row=row("Salvage","1","a");row.conditions="Never";assertEquals("1/10",row.display());assertTrue(row.tooltip().contains("Never"));row.rawRate="1/1";assertEquals("Always",row.display());row.conditions="Requires a quest";assertEquals("Conditional",row.display());
	}
}
