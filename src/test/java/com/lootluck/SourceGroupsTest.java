package com.lootluck;

import com.lootluck.model.*;
import com.lootluck.tracking.*;
import com.lootluck.rates.*;
import com.lootluck.ui.*;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceGroupsTest
{
	@Test public void combinesVariantsWithoutChangingObservationsOrMixingContexts()
	{
		LedgerEngine engine=new LedgerEngine(new Ledger());
		RateRow rate=new RateRow();rate.itemName="Bones";rate.rawRate="Always";rate.conditions="";rate.hash="bones";rate.fetchedAt="2026-10-04T00:00:00Z";
		for(int variant=1;variant<=3;variant++)
		{
			SourceIdentity source=new SourceIdentity("NPC","Cow","id="+variant,"normal");
			for(int n=0;n<(variant==2?3:1);n++) { engine.observe(new Observation(source,variant,1,Map.of(1,1L),Map.of(1,"Bones"),Map.of()),AttemptAdapter.Assessment.certified("test","bones",Map.of(1,1.0)),List.of(rate)); }
		}
		RateProvider provider=new RateProvider() { public List<RateRow> rows(SourceIdentity s) { return List.of(rate); }public void refresh(SourceIdentity s,boolean force) {} };
		PanelState view=ViewBuilder.build(engine.lifetime(),provider,false,Map.of());assertEquals(1,view.sources.size());assertEquals(5,view.sources.get(0).count);PanelState.Item item=view.sources.get(0).items.get(0);assertEquals(5,item.quantity);assertEquals(5,item.occurrences);assertEquals("Always",item.rate);assertEquals(5,item.attempts.longValue());assertEquals(5,item.expected,0);assertTrue(item.rateTooltip.contains("5 outcomes"));assertEquals(1,engine.lifetime().sources.size());assertEquals(3,engine.lifetime().sourceVariants().size());
		assertEquals(1,engine.lifetime().sourceVariants().iterator().next().items.get(1).quantity);
		SourceIdentity other=new SourceIdentity("NPC","Cow","id=1","seasonal");engine.observe(new Observation(other,5,1,Map.of(1,1L),Map.of(1,"Bones"),Map.of()),AttemptAdapter.Assessment.unsupported("pending"));assertEquals(1,ViewBuilder.build(engine.lifetime(),provider,false,Map.of()).sources.size());
	}
}


