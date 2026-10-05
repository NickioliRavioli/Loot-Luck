package com.lootluck;

import com.lootluck.model.*;
import com.lootluck.tracking.*;
import java.util.Map;
import java.util.HashMap;
import org.junit.Test;
import static org.junit.Assert.*;

public class LedgerEngineTest
{
	@Test public void publishedRatesAreImmutableAndUnknownHistoryStaysUnknown()
	{
		LedgerEngine engine=new LedgerEngine(new Ledger());
		engine.observe(observation(1,Map.of(1,2L)),AttemptAdapter.Assessment.unsupported("pending"));
		com.lootluck.rates.RateRow rate=new com.lootluck.rates.RateRow();rate.quantity="1–10";rate.mechanics.put("Rolls","1");rate.itemName="Test item";rate.rawRate="1/100";rate.hash="old";rate.fetchedAt="2026-10-04T00:00:00Z";
		engine.observe(observation(1,Map.of(1,3L)),AttemptAdapter.Assessment.unsupported("pending"),java.util.List.of(rate));
		rate.rawRate="1/200";rate.hash="new";
		engine.observe(observation(1,Map.of(1,4L)),AttemptAdapter.Assessment.unsupported("pending"),java.util.List.of(rate));
		Ledger.Item item=engine.lifetime().source(source).items.get(1);assertEquals(3,item.recordedRates.size());assertEquals(2,item.recordedRates.get("unknown").quantity);assertTrue(item.recordedRates.values().stream().anyMatch(v->!v.rows.isEmpty()&&v.rows.get(0).rawRate.equals("1/100")&&v.quantity==3));assertEquals(9,item.quantity);
	}
	private final SourceIdentity source=new SourceIdentity("NPC","Test","id=1","normal");
	private Observation observation(long amount,Map<Integer,Long> quantities) { return new Observation(source,1,amount,quantities,Map.of(1,"Test item"),Map.of(1,1)); }
	private AttemptAdapter.Assessment certified(String hash) { return AttemptAdapter.Assessment.certified("test",hash,Map.of(1,.1)); }
	@Test public void requestedCounters()
	{
		Ledger.Source s=Fixtures.engine().lifetime().sourceVariants().iterator().next();
		assertEquals(1000,s.notifications);assertEquals(22,s.items.get(Fixtures.COTTON).quantity);assertEquals(7,s.items.get(Fixtures.COTTON).occurrences);
		assertEquals(1000,s.segments.get(s.activeSegment).attempts);assertEquals(399,s.segments.get(s.activeSegment).dry.get(Fixtures.COTTON).longValue());
	}
	@Test public void copiedItemsLegitimateRepeatsEmptyAndBatch()
	{
		Map<Integer,Long> mutable=new HashMap<>();mutable.put(1,3L);Observation o=observation(1,mutable);mutable.clear();
		LedgerEngine e=new LedgerEngine(new Ledger());e.observe(o,certified("v1"));e.observe(o,certified("v1"));e.observe(observation(1,Map.of()),certified("v1"));
		Ledger.Source s=e.lifetime().source(source);assertEquals(6,s.items.get(1).quantity);assertEquals(2,s.items.get(1).occurrences);assertEquals(3,s.segments.get(s.activeSegment).attempts);
		e.observe(observation(5,Map.of(1,8L)),certified("v1"));assertNull(s.activeSegment);assertEquals(8,s.reportedAmount);assertEquals(14,s.items.get(1).quantity);assertEquals(3,s.items.get(1).occurrences);
		assertFalse(s.segments.get("segment-0").streakValid);
	}
	@Test public void ratesAndSessionDoNotRewriteHistory()
	{
		LedgerEngine e=new LedgerEngine(new Ledger());e.observe(observation(1,Map.of()),certified("v1"));e.observe(observation(1,Map.of(1,1L)),certified("v2"));
		Ledger.Source s=e.lifetime().source(source);assertEquals(2,s.segments.size());assertEquals(1,s.segments.get("segment-0").attempts);
		e.resetSession();assertTrue(e.session().sources.isEmpty());assertEquals(2,s.notifications);
	}
	@Test public void splitRewardsUnionOnlyByExplicitCompletion()
	{
		AttemptAssembler assembler=new AttemptAssembler();assembler.part("kill-1",observation(1,Map.of(1,2L)));assembler.part("kill-1",observation(1,Map.of(1,3L)));
		LedgerEngine e=new LedgerEngine(new Ledger());e.observe(assembler.complete("kill-1"),certified("v1"));
		assembler.part("kill-2",observation(1,Map.of(1,5L)));e.observe(assembler.complete("kill-2"),certified("v1"));
		Ledger.Source s=e.lifetime().source(source);assertEquals(2,s.notifications);assertEquals(2,s.items.get(1).occurrences);assertEquals(10,s.items.get(1).quantity);
	}
	@Test public void productionIsAlwaysGated() { assertFalse(new ValidationRegistry().assess(observation(1,Map.of())).certified); }
	@Test public void originalNotedAndUnnotedIdsPreserved()
	{
		LedgerEngine e=new LedgerEngine(new Ledger());e.observe(new Observation(source,1,1,Map.of(1,5L),Map.of(1,"Test item"),Map.of(1,1,2,1)),certified("v1"));
		assertEquals(java.util.Set.of(1,2),e.lifetime().source(source).items.get(1).originalIds);
	}
}


