package com.lootluck;

import com.google.gson.Gson;
import com.lootluck.rates.*;
import com.lootluck.model.*;
import com.lootluck.ui.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class WikiParserTest
{
	@Test public void numericGoblinVariantsShowTheirRates() throws Exception
	{
		List<RateRow> rates=WikiParser.parse(new Gson(),Files.readString(Path.of("tools/wiki-inputs/Goblin.json")),"Goblin","2026-10-02T00:00:00Z");
		Ledger ledger=new Ledger();Ledger.Source source=new Ledger.Source();source.identity=new SourceIdentity("NPC","Goblin","combat=2;metadata=unresolved","normal");Ledger.Item hammer=new Ledger.Item();hammer.id=2347;hammer.name="Hammer";hammer.quantity=1;hammer.occurrences=1;source.notifications=1;source.items.put(hammer.id,hammer);ledger.sources.put(source.identity.key(),source);
		RateProvider provider=new RateProvider() { public List<RateRow> rows(SourceIdentity s) { return rates; }public void refresh(SourceIdentity s,boolean force) {} };
		PanelState.Item item=ViewBuilder.build(ledger,provider,false,Map.of()).sources.get(0).items.get(0);assertEquals("Variants: 1/8.5, 1/14.2",item.rate);assertTrue(item.rateTooltip.contains("15/128"));assertTrue(item.rateTooltip.contains("9/128"));assertEquals(2,item.rateOptions.size());assertEquals(1,item.unresolvedOccurrences);assertNull(item.rateOptions.get(0).quantity);assertNull(item.attempts);
	}
	@Test public void preservesFullTablesAndVariants() throws Exception
	{
		int total=0;
		for(String owner:List.of("Large salvage","Goblin","Vorkath","Zulrah"))
		{
			List<RateRow> rows=WikiParser.parse(new Gson(),Files.readString(Path.of("tools/wiki-inputs",owner.replace(' ','-')+".json")),owner,"2026-10-02T00:00:00Z");total+=rows.size();
			assertTrue(rows.size()>20);assertTrue(rows.stream().allMatch(r->r.hash.length()==64&&!r.mechanics.isEmpty()));
			if(owner.equals("Large salvage")) { assertEquals("1/443.6",rows.stream().filter(r->r.itemName.equals("Cotton seed")).findFirst().get().display()); }
			if(owner.equals("Goblin")) { assertTrue(rows.stream().map(r->r.variant).distinct().count()>1); }
		}
		assertEquals(257,total);
	}
	@Test(expected=IllegalArgumentException.class) public void emptyResponseIsNotZeroProbability() { WikiParser.parse(new Gson(),"{\"bucket\":[]}","Goblin","2026-10-02T00:00:00Z"); }
	@Test public void unknownConditionalAndGuaranteed()
	{
		RateRow row=new RateRow();row.rawRate="Always";assertEquals("Always",row.display());row.rawRate="Varies";assertNull(row.publishedProbability());assertEquals("Rate unknown",row.display());
		row.rawRate="1/100";row.conditions="Requires a ring";assertEquals("Conditional",row.display());assertEquals(0.01,row.publishedProbability(),0);assertTrue(row.tooltip().contains("Requires a ring"));
		assertNull(WikiRateProvider.owner(new SourceIdentity("PLAYER","Goblin","","")));
	}
	@Test public void unvalidatedRowsHaveNoDerivedStatistics()
	{
		Ledger ledger=new Ledger();Ledger.Source source=new Ledger.Source();source.identity=new SourceIdentity("NPC","Goblin","unknown","normal");ledger.sources.put(source.identity.key(),source);
		RateRow row=new RateRow();row.itemName="Unobtained";row.rawRate="1/100";row.quantity="1";row.variant="Goblin";row.conditions="";row.fetchedAt="now";row.url="wiki";
		RateProvider provider=new RateProvider() { public List<RateRow> rows(SourceIdentity s) { return List.of(row); }public void refresh(SourceIdentity s,boolean force) { } };
		PanelState state=ViewBuilder.build(ledger,provider,true,Map.of());assertEquals(1,state.sources.get(0).items.size());assertNull(state.sources.get(0).items.get(0).attempts);assertNull(state.sources.get(0).items.get(0).dryness);
	}
}

