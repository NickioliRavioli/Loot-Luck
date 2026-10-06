package com.lootluck;

import com.google.gson.Gson;
import com.lootluck.model.*;
import com.lootluck.rates.*;
import com.lootluck.tracking.*;
import com.lootluck.ui.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScrollBoxRatesTest
{
	@Test public void goblinScrollBoxResolvesWithoutRewritingUnknownHistoryOrDuplicatingRows() throws Exception
	{
		List<RateRow> rates=WikiParser.parse(new Gson(),Files.readString(Path.of("tools/wiki-inputs/Goblin.json")),"Goblin","2026-10-05T00:00:00Z");
		SourceIdentity source=new SourceIdentity("NPC","Goblin","id=1","[MEMBERS]");LedgerEngine engine=new LedgerEngine(new Ledger());
		Observation observation=new Observation(source,1,1,Map.of(1,1L),Map.of(1,"Scroll box (easy)"),Map.of(1,1));
		engine.observe(observation,AttemptAdapter.Assessment.unsupported("pending"));
		RateProvider provider=new RateProvider() { public List<RateRow> rows(SourceIdentity identity) { return rates; }public void refresh(SourceIdentity identity,boolean force) {} };
		PanelState view=ViewBuilder.build(engine.lifetime(),provider,true,Map.of("clue scroll (easy)",2));
		PanelState.Item item=view.sources.get(0).items.stream().filter(row->row.id==1).findFirst().get();
		assertEquals("Scroll box (easy)",item.name);assertEquals("1/128",item.rate);assertTrue(item.rateTooltip.contains("Members world only"));assertTrue(item.rateTooltip.contains("Rate unknown at collection"));assertNull(item.attempts);
		assertEquals(1,view.sources.get(0).items.stream().filter(row->ItemRateMatcher.lookupName(row.name).equals("clue scroll (easy)")).count());
		assertTrue(engine.lifetime().source(source).items.get(1).recordedRates.get("unknown").rows.isEmpty());
		engine.observe(observation,AttemptAdapter.Assessment.unsupported("pending"),rates);
		assertTrue(engine.lifetime().source(source).items.get(1).recordedRates.values().stream().anyMatch(history->history.rows.size()==2&&history.rows.stream().allMatch(row->row.itemName.equals("Clue scroll (easy)"))));
	}
	@Test public void rewardPoolCasketAliasResolvesObtainedRateWithoutDuplicate()
	{
		SourceIdentity source=new SourceIdentity("EVENT","Reward pool (Tempoross)","","");RateRow row=new RateRow();row.owner="Reward pool";row.itemName="Casket (Reward pool)";row.rawRate="1/20";row.quantity="1";row.mechanics.put("Rolls","1");row.hash="fixture";row.fetchedAt="2026-10-06T00:00:00Z";
		LedgerEngine engine=new LedgerEngine(new Ledger());engine.observe(new Observation(source,1,1,Map.of(1,1L),Map.of(1,"Casket"),Map.of()),AttemptAdapter.Assessment.unsupported("pending"),List.of(row));
		RateProvider provider=new RateProvider() { public List<RateRow> rows(SourceIdentity identity) { return List.of(row); }public void refresh(SourceIdentity identity,boolean force) {} };
		PanelState state=ViewBuilder.build(engine.lifetime(),provider,true,Map.of());assertEquals(1,state.sources.get(0).items.size());assertEquals("1/20",state.sources.get(0).items.get(0).rate);assertEquals("Casket",state.sources.get(0).items.get(0).name);assertEquals(1,engine.lifetime().source(source).items.get(1).recordedRates.values().iterator().next().rows.size());
		assertEquals("casket",ItemRateMatcher.lookupName("Casket",new SourceIdentity("NPC","Reward pool","","")));assertEquals("casket",ItemRateMatcher.lookupName("Casket",new SourceIdentity("EVENT","Other","","")));
	}
	@Test public void aliasIsTierSpecificAndDoesNotMatchRewardContainers()
	{
		for(String tier:List.of("beginner","easy","medium","hard","elite","master")) { assertEquals("clue scroll ("+tier+")",ItemRateMatcher.lookupName("Scroll box ("+tier+")")); }
		assertEquals("reward casket (easy)",ItemRateMatcher.lookupName("Reward casket (easy)"));assertEquals("scroll box (unknown)",ItemRateMatcher.lookupName("Scroll box (unknown)"));
		RateRow row=new RateRow();row.rawRate="1/128";row.conditions="<sub title=\"Members-only\" style=\"cursor:help\">(m)</sub> Requires a quest";assertEquals("1/128 *",row.display());
	}
}

