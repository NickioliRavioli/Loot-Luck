package com.lootluck;

import com.google.gson.Gson;
import com.lootluck.model.*;
import com.lootluck.tracking.*;
import com.lootluck.rates.*;
import com.lootluck.storage.LedgerStore;
import com.lootluck.ui.*;
import net.runelite.client.util.Filepath;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class CollectionHistoryTest
{
	@Test public void pagedViewsMaterializeOnlyRequestedHistoryAndKeepChronologicalOrder()
	{
		LedgerEngine engine=Fixtures.engine();RateProvider rates=new RateProvider() { public List<RateRow> rows(SourceIdentity source) { return List.of(); }public void refresh(SourceIdentity source,boolean force) {} };
		Ledger ledger=engine.lifetime();
		PanelState hidden=ViewBuilder.build(ledger,rates,false,Map.of(),false,null,false,true,-2);assertEquals(1000,hidden.collectionCount);assertTrue(hidden.collections.isEmpty());
		PanelState first=ViewBuilder.build(ledger,rates,false,Map.of(),false,null,false,true,0);assertEquals(50,first.collections.size());assertEquals(ledger.history.get(999).timestamp,first.collections.get(0).lastReceived);
		PanelState next=ViewBuilder.build(ledger,rates,false,Map.of(),false,null,false,true,1);assertEquals(50,next.collections.size());assertEquals(ledger.history.get(949).timestamp,next.collections.get(0).lastReceived);
		PanelState last=ViewBuilder.build(ledger,rates,false,Map.of(),false,null,false,true,100);assertEquals(19,last.collectionPage);assertEquals(50,last.collections.size());
		ledger.history.get(0).timestamp=Long.MAX_VALUE;
		PanelState reordered=ViewBuilder.build(ledger,rates,false,Map.of(),false,null,false,true,0);assertEquals(Long.MAX_VALUE,reordered.collections.get(0).lastReceived);assertEquals(first.collections.get(0).lastReceived,reordered.collections.get(1).lastReceived);
		assertEquals(first.allItems.get(0).quantity,reordered.allItems.get(0).quantity);
	}
	@Rule public TemporaryFolder folder=new TemporaryFolder();
	@Test public void capsHistoryWithoutChangingTotalsAndSupportsUnlimitedAndRestart() throws Exception
	{
		LedgerEngine engine=new LedgerEngine(new Ledger());engine.setHistoryLimit(2);SourceIdentity a=new SourceIdentity("NPC","Cow","a","normal"),b=new SourceIdentity("NPC","Goblin","b","normal");
		for(int n=1;n<=4;n++) { engine.observe(new Observation(n%2==0?a:b,n,1,Map.of(1,2L),Map.of(1,"Bones"),Map.of()),AttemptAdapter.Assessment.unsupported("pending")); }
		assertEquals(2,engine.lifetime().history.size());assertEquals(3,engine.lifetime().history.get(0).timestamp);engine.setHistoryLimit(0);
		engine.observe(new Observation(a,5,1,Map.of(),Map.of(),Map.of()),AttemptAdapter.Assessment.unsupported("pending"));assertEquals(3,engine.lifetime().history.size());
		RateProvider rates=new RateProvider() { public List<RateRow> rows(SourceIdentity source) { return List.of(); }public void refresh(SourceIdentity source,boolean force) {} };
		PanelState view=ViewBuilder.build(engine.lifetime(),rates,false,Map.of());assertEquals(8,view.allItems.get(0).quantity);assertEquals(4,view.allItems.get(0).occurrences);assertEquals(5,view.collections.get(0).lastReceived);assertTrue(view.collections.get(0).items.isEmpty());
		LedgerStore store=new LedgerStore(Filepath.Unchecked.getRooted(folder.getRoot().toPath()),new Gson());store.save("account",engine.lifetime());Ledger loaded=store.load("account");assertEquals(3,loaded.history.size());assertTrue(store.load("other").history.isEmpty());engine.resetSession();assertTrue(engine.session().history.isEmpty());assertEquals(3,engine.lifetime().history.size());
	}
}
