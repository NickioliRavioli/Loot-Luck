package com.lootluck;

import com.google.gson.Gson;
import com.lootluck.model.*;
import com.lootluck.rates.*;
import com.lootluck.storage.LedgerStore;
import com.lootluck.tracking.*;
import com.lootluck.ui.*;
import java.util.*;
import net.runelite.client.util.Filepath;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PickpocketFailureTest
{
	@Rule public TemporaryFolder folder=new TemporaryFolder();
	private final RateProvider rates=new RateProvider() { public List<RateRow> rows(SourceIdentity source) { return List.of(); }public void refresh(SourceIdentity source,boolean force) {} };
	@Test public void failuresPersistAsOccurrencesWithoutItemsOrKillContamination() throws Exception
	{
		LedgerEngine engine=new LedgerEngine(new Ledger());SourceIdentity failure=new SourceIdentity("PICKPOCKET","Guard","outcome=failed","[MEMBERS]");
		SourceIdentity success=new SourceIdentity("PICKPOCKET","Guard","rogueDoubled=false","[MEMBERS]");SourceIdentity kill=new SourceIdentity("NPC","Guard","metadata=1","[MEMBERS]");
		engine.failPickpocket(failure,1);engine.failPickpocket(failure,2);
		engine.observe(new Observation(success,3,1,Map.of(1,50L),Map.of(1,"Coins"),Map.of()),AttemptAdapter.Assessment.unsupported("pending"));
		engine.observe(new Observation(kill,4,1,Map.of(1,5L),Map.of(1,"Coins"),Map.of()),AttemptAdapter.Assessment.unsupported("pending"));
		Ledger.Source failures=engine.lifetime().source(failure);assertEquals(2,failures.pickpocketFailures);assertEquals(0,failures.notifications);assertTrue(failures.items.isEmpty());
		Filepath directory=Filepath.Unchecked.getRooted(folder.getRoot().toPath());LedgerStore store=new LedgerStore(directory,new Gson());store.save("account",engine.lifetime());
		Filepath export=directory.joinSegment("export.json");store.exportData("account",engine.lifetime(),export);Ledger loaded=store.importData("account",export);
		assertEquals(2,loaded.source(failure).pickpocketFailures);assertTrue(loaded.history.get(0).pickpocketFailure);assertTrue(loaded.history.get(0).quantities.isEmpty());
		PanelState view=ViewBuilder.build(loaded,rates,false,Map.of());assertEquals(2,view.sources.size());
		PanelState.Source pickpocket=view.sources.stream().filter(s->s.name.contains("Pickpocket")).findFirst().get();assertEquals(3,pickpocket.count);
		PanelState.Item row=pickpocket.items.stream().filter(i->i.occurrenceOnly).findFirst().get();assertEquals(2,row.occurrences);assertEquals(0,row.quantity);assertEquals(2,row.lastReceived);assertTrue(row.rateOptions.isEmpty());assertEquals("",row.rate);
		assertEquals(1,view.sources.stream().filter(s->!s.name.contains("Pickpocket")).findFirst().get().items.size());
		assertEquals(55,view.allItems.stream().filter(i->!i.occurrenceOnly).findFirst().get().quantity);
		assertEquals(2,view.allItems.stream().filter(i->i.occurrenceOnly).findFirst().get().occurrences);
		assertEquals(2,view.collections.stream().filter(c->c.items.stream().anyMatch(i->i.occurrenceOnly)).count());
		engine.resetSession();assertTrue(ViewBuilder.build(engine.session(),rates,false,Map.of()).allItems.isEmpty());assertEquals(2,engine.lifetime().source(failure).pickpocketFailures);
	}
	@Test public void ordinaryExistingSnapshotsNeedNoMigration()
	{
		Ledger ledger=new Gson().fromJson("{\"schema\":3,\"sources\":{},\"history\":[]}",Ledger.class);LedgerStore.validate(ledger);
		LedgerEngine engine=new LedgerEngine(ledger);engine.failPickpocket(new SourceIdentity("PICKPOCKET","Man","outcome=failed","[]"),1);LedgerStore.validate(ledger);
		try { engine.failPickpocket(new SourceIdentity("NPC","Man","","[]"),2);fail("Kill failure accepted"); }catch(IllegalArgumentException expected) { }
	}
}
