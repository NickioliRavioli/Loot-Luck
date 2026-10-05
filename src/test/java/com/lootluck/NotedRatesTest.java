package com.lootluck;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lootluck.model.*;
import com.lootluck.rates.*;
import com.lootluck.tracking.*;
import com.lootluck.ui.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class NotedRatesTest
{
	@Test public void separatesBrutusSteaksAndPreservesHistoricalSnapshots() throws Exception
	{
		Map<String,List<RateRow>> catalogue=new Gson().fromJson(Files.readString(Path.of("src/main/resources/rates/catalogue.json")),new TypeToken<Map<String,List<RateRow>>>(){}.getType());List<RateRow> rates=catalogue.get("Brutus");
		SourceIdentity source=new SourceIdentity("NPC","Brutus","id=1","normal");LedgerEngine engine=new LedgerEngine(new Ledger());
		engine.observe(new Observation(source,1,1,Map.of(1,1L,2,3L),Map.of(1,"Raw t-bone steak",2,"Raw t-bone steak (noted)"),Map.of(1,1,2,2)),AttemptAdapter.Assessment.unsupported("pending"),rates);
		RateProvider provider=new RateProvider() { public List<RateRow> rows(SourceIdentity s) { return rates; }public void refresh(SourceIdentity s,boolean force) {} };
		PanelState view=ViewBuilder.build(engine.lifetime(),provider,false,Map.of());assertEquals(2,view.sources.get(0).items.size());PanelState.Item raw=view.sources.get(0).items.stream().filter(i->i.id==1).findFirst().get(),noted=view.sources.get(0).items.stream().filter(i->i.id==2).findFirst().get();
		assertEquals("Always",raw.rate);assertEquals(1,raw.quantity);assertEquals(1,raw.rateOptions.size());assertEquals(3,noted.quantity);assertEquals(1,noted.rateOptions.size());assertTrue(noted.rateTooltip.contains("10/81"));assertFalse(raw.rateTooltip.contains("10/81"));assertEquals(0,raw.unresolvedOccurrences);assertEquals(0,noted.unresolvedOccurrences);assertEquals(1,engine.lifetime().source(source).items.get(1).recordedRates.values().iterator().next().rows.size());
	}
}


