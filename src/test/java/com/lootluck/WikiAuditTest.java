package com.lootluck;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.lootluck.model.*;
import com.lootluck.rates.*;
import com.lootluck.tracking.*;
import com.lootluck.stats.Statistics;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class WikiAuditTest
{
	private static Map<String,List<RateRow>> catalogue;
	private static JsonObject evidence;
	@BeforeClass public static void load() throws Exception
	{
		Gson gson=new Gson();
		catalogue=gson.fromJson(Files.readString(Path.of("src/main/resources/rates/catalogue.json")),new TypeToken<Map<String,List<RateRow>>>(){}.getType());
		WikiItemIndex index=new WikiItemIndex(gson);catalogue.values().forEach(index::apply);
		evidence=gson.fromJson(Files.readString(Path.of("tools/wiki-audit/evidence.json")),JsonObject.class);
	}
	private SourceIdentity npc(String name,int combat,int id) { return new SourceIdentity("NPC",name,"combat="+combat+";metadata="+id,"[MEMBERS]"); }
	private Observation observation(SourceIdentity source,Map<Integer,Long> items)
	{ return new Observation(source,1,1,items,Map.of(),Map.of()); }
	@Test public void discreteQuantitiesNeverBecomeAnInclusiveRange()
	{
		RateRow row=new RateRow();row.quantity="12, 15";row.mechanics.put("Rolls","1");row.mechanics.put("Quantity Low","12");row.mechanics.put("Quantity High","15");
		assertEquals(1,ItemRateMatcher.matchCollection("Air rune",List.of(row),15,1).size());
		assertTrue(ItemRateMatcher.matchCollection("Air rune",List.of(row),14,1).isEmpty());
		row.quantity="1,000";row.mechanics.remove("Quantity Low");row.mechanics.remove("Quantity High");
		assertEquals(1,ItemRateMatcher.matchCollection("Coins",List.of(row),1000,1).size());
	}
	@Test public void darkWizardTablesResolveFromPublishedCombatLevelsWithoutCustomRules()
	{
		List<RateRow> rows=new ArrayList<>();
		for(RateRow original:catalogue.get("Dark wizard")) { RateRow row=original.snapshot();row.mechanics.remove("Table combat levels");row.mechanics.remove("Table NPC IDs");rows.add(row); }
		for(int level:new int[]{7,11,20,22,23})
		{
			TableResolver.Resolution result=TableResolver.resolve(npc("Dark wizard",level,0),rows);
			assertTrue(result.reason,result.resolved);String table=level<20?"#Low level":"#High level";
			assertTrue(result.rows.stream().filter(row->row.itemName.equals("Earth rune")).allMatch(row->row.variant.endsWith(table)));
			List<RateRow> earth=ItemRateMatcher.item(557,557,"Earth rune",npc("Dark wizard",level,0),result.rows);
			assertEquals(3,earth.size());assertEquals("1/32",earth.stream().filter(row->row.quantity.equals("36")).findFirst().orElseThrow().display());
		}
		assertFalse(TableResolver.resolve(npc("Dark wizard",99,0),rows).resolved);
	}
	@Test public void ghostCommonTableAndWildernessEligibilityStayDistinct()
	{
		SourceIdentity unknown=npc("Ghost",19,85);
		SourceIdentity outside=new SourceIdentity("NPC","Ghost",unknown.variant+";wilderness=false","[MEMBERS]");
		SourceIdentity wilderness=new SourceIdentity("NPC","Ghost",unknown.variant+";wilderness=true","[MEMBERS]");
		List<RateRow> rows=TableResolver.resolve(unknown,catalogue.get("Ghost")).rows;
		assertEquals(2,rows.size());RateRow bag=rows.stream().filter(row->row.itemName.equals("Looting bag")).findFirst().orElseThrow();
		assertEquals("Wilderness",bag.mechanics.get("Required area"));assertNull(ItemEligibility.eligible(bag,unknown));
		assertEquals(Boolean.FALSE,ItemEligibility.eligible(bag,outside));assertEquals(Boolean.TRUE,ItemEligibility.eligible(bag,wilderness));
		assertEquals(1,TableResolver.resolve(npc("Ghost",76,2531),catalogue.get("Ghost")).rows.size());
		assertFalse(new ValidationRegistry(new Gson()).candidate("Ghost"));
	}
	@Test public void conditionHeadersToggleWithoutChangingEligibleAttemptsOrTotals()
	{
		LedgerEngine engine=new LedgerEngine(new Ledger());
		RateRow bag=catalogue.get("Ghost").stream().filter(row->row.itemName.equals("Looting bag")).findFirst().orElseThrow();int id=bag.itemIds.get(0);
		SourceIdentity outside=new SourceIdentity("NPC","Ghost","combat=19;metadata=85;wilderness=false","[MEMBERS]");
		SourceIdentity wild=new SourceIdentity("NPC","Ghost","combat=19;metadata=85;wilderness=true","[MEMBERS]");
		for(int n=0;n<70;n++) { engine.observe(observation(outside,Map.of()),AttemptAdapter.Assessment.unsupported("Outside Wilderness")); }
		for(int n=0;n<10;n++) { engine.observe(observation(wild,n==9?Map.of(id,1L):Map.of()),AttemptAdapter.Assessment.certified("test-only","test-bag",Map.of(id,1.0/7))); }
		for(int n=0;n<5;n++) { engine.observe(observation(npc("Ghost",19,85),Map.of()),AttemptAdapter.Assessment.unsupported("Unknown old location")); }
		RateProvider rates=new RateProvider() { public List<RateRow> rows(SourceIdentity source) { return catalogue.get("Ghost"); }public void refresh(SourceIdentity source,boolean force) {} };
		com.lootluck.ui.PanelState combined=com.lootluck.ui.ViewBuilder.build(engine.lifetime(),rates,true,Map.of());
		assertEquals(1,combined.sources.size());assertEquals(85,combined.sources.get(0).count);
		assertEquals(10,combined.sources.get(0).items.stream().filter(item->item.id==id).findFirst().orElseThrow().attempts.longValue());
		com.lootluck.ui.PanelState separate=com.lootluck.ui.ViewBuilder.build(engine.lifetime(),rates,true,Map.of(),false,null,true);
		assertEquals(3,separate.sources.size());assertEquals(85,separate.sources.stream().mapToLong(source->source.count).sum());
		com.lootluck.ui.PanelState.Source outsideView=separate.sources.stream().filter(source->source.name.contains("Outside Wilderness")).findFirst().orElseThrow();
		assertTrue(outsideView.items.stream().noneMatch(item->item.id==id));
		assertEquals(combined.allItems.get(0).quantity,separate.allItems.get(0).quantity);assertEquals(85,engine.lifetime().history.size());
		assertTrue(separate.sources.stream().anyMatch(source->source.name.startsWith("Ghost (")&&source.name.contains("Unknown location")));
	}
	@Test public void cosmeticGoblinIdsShareSeparateConditionHeadersButDifferentTablesDoNot()
	{
		LedgerEngine engine=new LedgerEngine(new Ledger());
		for(int id:new int[]{3032,3033,3045}) { engine.observe(observation(npc("Goblin",id==3045?5:2,id),Map.of(526,1L)),AttemptAdapter.Assessment.unsupported("unapproved")); }
		RateProvider rates=new RateProvider() { public List<RateRow> rows(SourceIdentity source) { return catalogue.get("Goblin"); }public void refresh(SourceIdentity source,boolean force) {} };
		com.lootluck.ui.PanelState combined=com.lootluck.ui.ViewBuilder.build(engine.lifetime(),rates,false,Map.of());
		assertEquals(1,combined.sources.size());assertEquals(3,combined.sources.get(0).count);assertEquals(3,combined.sources.get(0).items.get(0).quantity);
		com.lootluck.ui.PanelState view=com.lootluck.ui.ViewBuilder.build(engine.lifetime(),rates,false,Map.of(),false,null,true);
		assertEquals(2,view.sources.size());assertEquals(3,view.sources.stream().mapToLong(source->source.count).sum());
		assertTrue(view.sources.stream().anyMatch(source->source.name.contains("Drop table 1")&&source.count==2));
		assertEquals(3,engine.lifetime().sourceVariants().size());
	}
	@Test public void anchoredQuestBoneUsesExactVariantId()
	{
		SourceIdentity zombie=npc("Zombie",24,56);
		List<RateRow> rows=TableResolver.resolve(zombie,catalogue.get("Zombie")).rows;
		List<RateRow> bone=ItemRateMatcher.item(7863,7863,"Zombie bone",zombie,rows);
		assertEquals(1,bone.size());assertEquals("1/4",bone.get(0).display());assertEquals(List.of(7863),bone.get(0).itemIds);
		assertTrue("Polished bone must not match unpolished drop",ItemRateMatcher.item(7865,7865,"Zombie bone",zombie,rows).isEmpty());
		SourceIdentity rat=npc("Rat",1,2854);
		assertEquals("1/4",ItemRateMatcher.item(7836,7836,"Rat bone",rat,TableResolver.resolve(rat,catalogue.get("Rat")).rows).get(0).display());
	}
	@Test public void sameNamedNpcSourcesUseWikiIdsAndPreserveRawIdentity() throws Exception
	{
		Gson gson=new Gson();WikiSourceIndex index=new WikiSourceIndex(gson);
		JsonObject identities=gson.fromJson(Files.readString(Path.of("src/main/resources/rates/source-identities.json")),JsonObject.class);
		List<SourceIdentity> guards=new ArrayList<>();
		for(String owner:List.of("Guard (Shayzien)","Guard (Auburnvale)"))
		{
			String id=identities.entrySet().stream().filter(e->owner.equals(e.getValue().getAsString())).findFirst().orElseThrow().getKey();
			SourceIdentity source=index.resolve(npc("Guard",20,Integer.parseInt(id)));
			assertEquals("Guard",source.name);assertEquals(owner,RewardSources.wikiOwner(source));
			SourceIdentity restored=gson.fromJson(gson.toJson(source),SourceIdentity.class);
			assertEquals(source.key(),restored.key());assertEquals(owner,restored.family().wikiOwner);guards.add(source);
		}
		assertNotEquals(guards.get(0).family().key(),guards.get(1).family().key());
		SourceIdentity unknown=npc("Guard",20,Integer.MAX_VALUE);assertSame(unknown,index.resolve(unknown));
		SourceIdentity reward=new SourceIdentity("EVENT","Guard","metadata=1","[MEMBERS]");assertSame(reward,index.resolve(reward));
	}
	@Test public void fullCatalogueParsesAndRetainsEveryExportedRow() throws Exception
	{
		JsonObject raw=new Gson().fromJson(Files.readString(Path.of("tools/wiki-inputs/full-catalogue.json")),JsonObject.class);
		assertEquals(raw.getAsJsonArray("bucket").size(),catalogue.values().stream().mapToInt(List::size).sum());
		assertTrue(catalogue.size()>=2200);
		for(List<RateRow> rows:catalogue.values()) { for(RateRow row:rows)
		{
			assertNotNull(row.display());assertFalse(row.itemName.isBlank());assertEquals(64,row.hash.length());
			Double p=row.publishedProbability();if(p!=null) { assertTrue(p>=0&&p<=1); }
		} }
	}
	private static String quantity(String value) { return ItemRateMatcher.normalize(value).replaceAll("\\[[^]]*\\]","").replace(",","").replace('-', '–').trim(); }
	private static Double pageProbability(String value)
	{
		String plain=value.replaceAll("\\[[^]]*\\]","").trim().replaceFirst("^\\d+\\s*[×x]\\s*","");RateRow row=new RateRow();row.rawRate=plain;return row.publishedProbability();
	}
	@Test public void independentRenderedPageExpectationsMatchBundledRates()
	{
		int compared=0;
		for(Map.Entry<String,JsonElement> page:evidence.entrySet())
		{
			JsonObject witness=page.getValue().getAsJsonObject();
			if(!witness.has("renderedRows")) { continue; }
			for(RateRow actual:catalogue.getOrDefault(page.getKey(),List.of()))
			{
				List<Double> expected=new ArrayList<>();
				for(JsonElement entry:witness.getAsJsonArray("renderedRows"))
				{
					JsonObject row=entry.getAsJsonObject();
					boolean tableMatch=row.get("table").getAsString().equals(actual.variant);
					if(row.has("tables")) { for(JsonElement candidate:row.getAsJsonArray("tables")) { tableMatch|=candidate.getAsString().equals(actual.variant); } }
					if(!tableMatch||
						!ItemRateMatcher.normalize(row.get("item").getAsString()).equals(ItemRateMatcher.normalize(actual.itemName))||
						!quantity(row.get("quantity").getAsString()).equals(quantity(actual.quantity))) { continue; }
					Double p=pageProbability(row.get("rate").getAsString());if(p!=null) { expected.add(p); }
				}
				if(expected.isEmpty()) { continue; }
				Double p=actual.publishedProbability();assertNotNull(actual.owner+": "+actual.itemName,p);
				assertTrue(actual.owner+": "+actual.itemName+" "+actual.rawRate,expected.stream().anyMatch(q->Math.abs(q-p)<1e-12));compared++;
			}
		}
		assertTrue("Deep audit lost coverage: "+compared,compared>=2900);
	}
	@Test public void giantRatAndNpcIdVariantsResolveWithoutLookingAtLoot()
	{
		for(int level:new int[]{3,6})
		{
			TableResolver.Resolution low=TableResolver.resolve(npc("Giant rat",level,2856),catalogue.get("Giant rat"));
			assertTrue(low.reason,low.resolved);assertTrue(low.rows.stream().allMatch(r->r.variant.endsWith("#Low level")));
		}
		TableResolver.Resolution high=TableResolver.resolve(npc("Giant rat",26,2510),catalogue.get("Giant rat"));
		assertTrue(high.resolved);assertTrue(high.rows.stream().allMatch(r->r.variant.endsWith("#Level 26")));
		assertFalse(TableResolver.resolve(npc("Giant rat",99,999),catalogue.get("Giant rat")).resolved);
		assertTrue(TableResolver.resolve(npc("Goblin",2,3028),catalogue.get("Goblin")).rows.stream().allMatch(r->r.variant.endsWith("#Drop table 1")));
		assertTrue(TableResolver.resolve(npc("Goblin",2,5192),catalogue.get("Goblin")).rows.stream().allMatch(r->r.variant.endsWith("#Drop table 2")));
		assertFalse(TableResolver.resolve(new SourceIdentity("NPC","Goblin","combat=2;metadata=unresolved","[MEMBERS]"),catalogue.get("Goblin")).resolved);
	}
	@Test public void changedExportDoesNotReuseOldPageConditions()
	{
		List<RateRow> changed=new ArrayList<>();for(RateRow row:catalogue.get("Giant rat")) { RateRow copy=row.snapshot();copy.mechanics.remove("Table combat levels");copy.mechanics.remove("Table NPC IDs");changed.add(copy); }
		changed.get(0).rawRate="1/2";changed.get(0).mechanics.put("Table combat levels","3,6");new WikiEvidence(new Gson()).apply(changed);
		assertFalse(changed.get(0).mechanics.containsKey("Table combat levels"));
		assertFalse(changed.get(0).mechanics.containsKey("Wiki page revision"));
	}
	@Test public void wikiIdAndGameNameMatchingSurvivesDifferentPageTitles()
	{
		int aliases=0;
		for(List<RateRow> rows:catalogue.values()) { for(RateRow row:rows)
		{
			if(row.gameName==null||row.itemIds.isEmpty()||ItemRateMatcher.notedRow(row)||ItemRateMatcher.normalize(row.gameName).equals(ItemRateMatcher.normalize(row.itemName))) { continue; }
			int id=row.itemIds.get(0);SourceIdentity source=new SourceIdentity("NPC",row.owner,"","");
			assertTrue(row.owner+" / "+row.itemName,ItemRateMatcher.item(id,id,row.gameName,source,rows).contains(row));aliases++;
		} }
		assertTrue("Too little identity coverage: "+aliases,aliases>100);
		assertEquals("dragon's stone",ItemRateMatcher.normalize("\u00a0<b>Dragon\u2019s   stone</b>"));
	}
	@Test public void reviewedModelsAreGatedAndCountPresence()
	{
		ValidationRegistry registry=new ValidationRegistry(new Gson());SourceIdentity source=npc("Zulrah",725,2042);
		List<RateRow> rows=catalogue.get("Zulrah");Observation empty=observation(source,Map.of());
		assertFalse(registry.assess(empty,rows,"").certified);
		String contract=registry.contract(source,rows);assertFalse(contract.isEmpty());
		AttemptAdapter.Assessment assessment=registry.assess(empty,rows,contract);assertTrue(assessment.reason,assessment.certified);
		int fang=net.runelite.api.gameval.ItemID.BLOWPIPE_FANG;
		assertEquals(2047.0/1048576,assessment.probabilities.get(fang),1e-15);
		LedgerEngine engine=new LedgerEngine(new Ledger());
		for(int i=0;i<1000;i++) { engine.observe(empty,assessment,rows); }
		Ledger.Segment segment=engine.lifetime().source(source).segments.get("segment-0");
		assertEquals(1000,segment.attempts);assertEquals(1000,segment.dry.get(fang).longValue());
		assertEquals(Math.pow(1023.0/1024,2000),Statistics.dryProbability(1000,assessment.probabilities.get(fang)),1e-14);
		engine.observe(observation(source,Map.of(fang,2L)),assessment,rows);
		assertEquals(1,segment.occurrences.get(fang).longValue());assertEquals(2,segment.quantities.get(fang).longValue());assertEquals(0,segment.dry.get(fang).longValue());
		engine.interruptCoverage();assertNull(engine.lifetime().source(source).activeSegment);assertFalse(segment.streakValid);
		engine.observe(empty,assessment,rows);assertEquals(2,engine.lifetime().source(source).segments.size());
		assertFalse(registry.assess(new Observation(source,1,2,Map.of(),Map.of(),Map.of()),rows,contract).certified);
		assertFalse(registry.assess(observation(new SourceIdentity("NPC","Zulrah",source.variant,"[MEMBERS, LEAGUE]"),Map.of()),rows,contract).certified);
	}
	@Test public void exclusiveChickenFeathersAreNotStackQuantity()
	{
		ValidationRegistry registry=new ValidationRegistry(new Gson());SourceIdentity source=npc("Chicken",1,2692);List<RateRow> rows=catalogue.get("Chicken");
		AttemptAdapter.Assessment model=registry.assess(observation(source,Map.of()),rows,registry.contract(source,rows));
		assertTrue(model.reason,model.certified);assertEquals(.75,model.probabilities.get(net.runelite.api.gameval.ItemID.FEATHER),0);
	}
	@Test public void overallLuckSurvivesCoverageGapsWithoutInventingAStreak()
	{
		SourceIdentity source=npc("Test",1,1);LedgerEngine engine=new LedgerEngine(new Ledger());
		Observation drop=new Observation(source,1,1,Map.of(1,4L),Map.of(1,"Test item"),Map.of());
		engine.observe(drop,AttemptAdapter.Assessment.certified("test","a",Map.of(1,.1)));
		engine.interruptCoverage();
		engine.observe(observation(source,Map.of()),AttemptAdapter.Assessment.certified("test","b",Map.of(1,.2)));
		RateProvider provider=new RateProvider() { public List<RateRow> rows(SourceIdentity s) { return List.of(); }public void refresh(SourceIdentity s,boolean force) {} };
		com.lootluck.ui.PanelState.Item item=com.lootluck.ui.ViewBuilder.build(engine.lifetime(),provider,false,Map.of()).sources.get(0).items.get(0);
		assertEquals(2,item.attempts.longValue());assertEquals(.3,item.expected,1e-15);assertEquals(1/.3,item.ratio,1e-14);
		assertNull(item.probability);assertNull(item.upper);assertNull(item.lower);assertEquals(1,item.dry.longValue());assertEquals(.8,item.noDrop,1e-15);
		engine.interruptCoverage();item=com.lootluck.ui.ViewBuilder.build(engine.lifetime(),provider,false,Map.of()).sources.get(0).items.get(0);
		assertEquals(2,item.attempts.longValue());assertNull(item.dry);
	}
	@Test public void percentagesZeroAndQualitativeWikiRatesRemainDistinct()
	{
		RateRow row=new RateRow();row.rawRate="0.5%";assertEquals(.005,row.publishedProbability(),0);assertEquals("1/200",row.display());
		row.rawRate="Never";assertEquals(0,row.publishedProbability(),0);assertEquals("Never",row.display());
		row.rawRate="Common";assertNull(row.publishedProbability());assertEquals("Common",row.display());
	}
}
