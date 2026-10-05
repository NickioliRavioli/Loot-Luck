package com.lootluck;

import com.lootluck.rates.RateRow;
import java.awt.Color;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorldMarkerRatesTest
{
	@Test public void brutusFreeToPlayCoinsKeepPublishedRatesAndWorldTooltip()
	{
		String marker="<sub title=\"Free-to-play-only\" style=\"cursor:help; margin-left:3px;\">(f)</sub>";
		for(String rate:new String[]{"15/81","5/81"})
		{
			RateRow row=new RateRow();row.rawRate=rate;row.conditions=marker;
			assertEquals("15/81".equals(rate)?"1/5.4":"1/16.2",row.display());
			assertEquals("(f)",row.worldMarker());
			assertEquals(new Color(130,184,117),row.color());
			assertTrue(row.tooltip().contains("Conditions: Free-to-play world only"));assertFalse(row.tooltip().contains("<sub"));
			row.conditions=marker+" Requires a quest";assertEquals(row.rawRate.equals("15/81")?"1/5.4 *":"1/16.2 *",row.display());assertTrue(row.tooltip().contains("Free-to-play world only; Requires a quest"));
		}
	}
	@Test public void membersMarkerAndUnknownConditionsRetainTheirMeaning()
	{
		RateRow row=new RateRow();row.rawRate="1/128";row.conditions="<sub title='Members-only'>(m)</sub>";
		assertEquals("1/128",row.display());assertTrue(row.tooltip().contains("Members world only"));
		assertEquals("(m)",row.worldMarker());
		row.conditions="<sub title='Unrecognized'>(f)</sub>";assertEquals("1/128 *",row.display());
		assertEquals("",row.worldMarker());
	}
	@Test public void unobtainedWorldFilterPreservesObtainedMixedAndUnmarkedItems()
	{
		com.lootluck.model.SourceIdentity source=new com.lootluck.model.SourceIdentity("NPC","Test","id=1","");
		com.lootluck.tracking.LedgerEngine engine=new com.lootluck.tracking.LedgerEngine(new com.lootluck.model.Ledger());
		engine.observe(new com.lootluck.model.Observation(source,1,1,java.util.Map.of(1,1L),java.util.Map.of(1,"Obtained members"),java.util.Map.of()),com.lootluck.tracking.AttemptAdapter.Assessment.unsupported("pending"));
		java.util.List<RateRow> rows=new java.util.ArrayList<>();
		for(String[] entry:new String[][]{{"Obtained members","m"},{"Members","m"},{"Free","f"},{"Shared",""},{"Mixed","m"},{"Mixed","f"}})
		{
			RateRow row=new RateRow();row.itemName=entry[0];row.rawRate="1/128";row.conditions=entry[1].isEmpty()?"":"<sub title='"+(entry[1].equals("m")?"Members-only":"Free-to-play-only")+"'>("+entry[1]+")</sub>";rows.add(row);
		}
		com.lootluck.rates.RateProvider provider=new com.lootluck.rates.RateProvider() { public java.util.List<RateRow> rows(com.lootluck.model.SourceIdentity identity) { return rows; }public void refresh(com.lootluck.model.SourceIdentity identity,boolean force) {} };
		java.util.function.Function<Boolean,java.util.List<String>> names=members->com.lootluck.ui.ViewBuilder.build(engine.lifetime(),provider,true,java.util.Map.of(),true,members).sources.get(0).items.stream().map(item->item.name).collect(java.util.stream.Collectors.toList());
		assertEquals(java.util.List.of("Obtained members","Free","Shared","Mixed"),names.apply(false));
		assertEquals(java.util.List.of("Obtained members","Members","Shared","Mixed"),names.apply(true));
		assertEquals(5,names.apply(null).size());
		assertEquals(5,com.lootluck.ui.ViewBuilder.build(engine.lifetime(),provider,true,java.util.Map.of(),false,false).sources.get(0).items.size());
		assertTrue(new LootLuckConfig() {}.hideUnobtainedOtherWorld());
	}
}
