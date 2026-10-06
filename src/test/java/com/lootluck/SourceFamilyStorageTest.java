package com.lootluck;

import com.google.gson.Gson;
import com.lootluck.model.*;
import com.lootluck.rates.*;
import com.lootluck.storage.*;
import com.lootluck.tracking.*;
import com.lootluck.ui.*;
import net.runelite.client.util.Filepath;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class SourceFamilyStorageTest
{
	@Rule public TemporaryFolder folder=new TemporaryFolder();
	@Test public void oneFamilyPreservesVariantsWorldsHistoriesAndTotalsAcrossRoundTrip() throws Exception
	{
		LedgerEngine engine=new LedgerEngine(new Ledger());
		SourceIdentity first=new SourceIdentity("NPC","Cow","id=1","[MEMBERS]"),second=new SourceIdentity("NPC","Cow","id=2","[]");
		for(SourceIdentity source:List.of(first,second)) {
			RateRow rate=new RateRow();rate.itemName="Bones";rate.quantity="1";rate.conditions="";rate.rawRate=source==first?"1/100":"1/200";rate.hash=source.variant;rate.fetchedAt="2026-10-05T00:00:00Z";rate.mechanics.put("Rolls","1");
			engine.observe(new Observation(source,10,1,Map.of(1,1L),Map.of(1,"Bones"),Map.of(1,1)),AttemptAdapter.Assessment.certified(source.variant,rate.hash,Map.of(1,.01)),List.of(rate));
		}
		Filepath directory=Filepath.Unchecked.getRooted(folder.getRoot().toPath());LedgerStore store=new LedgerStore(directory,new Gson());store.save("A",engine.lifetime());Filepath export=directory.joinSegment("export.json");store.exportData("A",engine.lifetime(),export);Ledger loaded=store.importData("A",export);
		assertEquals(1,loaded.sources.size());assertEquals(2,loaded.sources.get(first.family().key()).variants.size());assertEquals(2,loaded.history.size());assertEquals("[MEMBERS]",loaded.source(first).identity.context);assertEquals("[]",loaded.source(second).identity.context);
		assertEquals("1/100",loaded.source(first).items.get(1).recordedRates.values().iterator().next().rows.get(0).rawRate);assertEquals("1/200",loaded.source(second).items.get(1).recordedRates.values().iterator().next().rows.get(0).rawRate);
		RateProvider provider=new RateProvider() { public List<RateRow> rows(SourceIdentity source) { return List.of(); }public void refresh(SourceIdentity source,boolean force) {} };
		PanelState view=ViewBuilder.build(loaded,provider,false,Map.of());assertEquals(1,view.sources.size());assertEquals(2,view.sources.get(0).count);assertEquals(2,view.sources.get(0).items.get(0).quantity);assertEquals(2,view.allItems.get(0).quantity);assertEquals(2,view.allItems.get(0).occurrences);assertEquals(2,view.sources.get(0).items.get(0).attempts.longValue());assertNull(view.sources.get(0).items.get(0).dry);
		LedgerEngine resumed=new LedgerEngine(store.load("A"));resumed.observe(new Observation(first,11,1,Map.of(1,1L),Map.of(1,"Bones"),Map.of()),AttemptAdapter.Assessment.unsupported("pending"));assertEquals(1,resumed.lifetime().sources.size());assertEquals(2,resumed.lifetime().source(first).notifications);assertEquals(1,resumed.lifetime().source(second).notifications);
	}
	@Test public void sameNameDifferentTypesRemainSeparateAndOldSchemaIsRejected()
	{
		LedgerEngine engine=new LedgerEngine(new Ledger());for(String type:List.of("NPC","EVENT")) { SourceIdentity source=new SourceIdentity(type,"Cow","id=1","normal");engine.observe(new Observation(source,1,1,Map.of(),Map.of(),Map.of()),AttemptAdapter.Assessment.unsupported("pending")); }assertEquals(2,engine.lifetime().sources.size());LedgerStore.validate(engine.lifetime());
		Ledger old=new Ledger();old.schema=2;try { LedgerStore.validate(old);fail("Old schema accepted"); }catch(IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("format")); }
	}
}
