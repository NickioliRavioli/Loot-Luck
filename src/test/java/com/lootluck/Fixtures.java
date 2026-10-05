package com.lootluck;

import com.lootluck.model.Ledger;
import com.lootluck.model.Observation;
import com.lootluck.model.SourceIdentity;
import com.lootluck.tracking.AttemptAdapter;
import com.lootluck.tracking.LedgerEngine;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/** Test-only observations. Never loaded by LootLuckPlugin or written to account storage. */
public final class Fixtures
{
	public static final int COTTON=ItemID.COTTON_SEED;
	private Fixtures() { }
	public static LedgerEngine engine()
	{
		LedgerEngine engine=new LedgerEngine(new Ledger());
		SourceIdentity source=new SourceIdentity("EVENT","Large salvage","fixture","DEVELOPMENT ONLY");
		AttemptAdapter.Assessment assessment=AttemptAdapter.Assessment.certified("SYNTHETIC-FIXTURE-ONLY","fixture-5/2218",Map.of(COTTON,5.0/2218));
		int[] stacks={5,2,4,3,1,4,3};
		for(int n=0;n<1000;n++)
		{
			int stack=n%100==0&&n<700?stacks[n/100]:0;
			engine.observe(new Observation(source,1_791_000_000_000L+n,1,stack==0?Map.of():Map.of(COTTON,(long)stack),Map.of(COTTON,"Cotton seed"),Map.of(COTTON,COTTON)),assessment);
		}
		return engine;
	}
	public static LedgerEngine reviewEngine()
	{
		LedgerEngine engine=engine();SourceIdentity vorkath=new SourceIdentity("NPC","Vorkath","fixture","DEVELOPMENT ONLY");
		AttemptAdapter.Assessment boss=AttemptAdapter.Assessment.certified("SYNTHETIC-FIXTURE-ONLY","fixture-boss",Map.of(ItemID.RUNE_LONGSWORD,.06,ItemID.DRAGON_BONES_SUPERIOR,1.0));
		for(int n=0;n<240;n++)
		{
			Map<Integer,Long> quantities=new java.util.LinkedHashMap<>();quantities.put(ItemID.DRAGON_BONES_SUPERIOR,2L);if(n%20==0) { quantities.put(ItemID.RUNE_LONGSWORD,3L); }
			engine.observe(new Observation(vorkath,1_791_000_001_000L+n,1,quantities,Map.of(ItemID.RUNE_LONGSWORD,"Rune longsword",ItemID.DRAGON_BONES_SUPERIOR,"Superior dragon bones"),Map.of()),boss);
		}
		SourceIdentity zulrah=new SourceIdentity("NPC","Zulrah","fixture","DEVELOPMENT ONLY");
		AttemptAdapter.Assessment snake=AttemptAdapter.Assessment.certified("SYNTHETIC-FIXTURE-ONLY","fixture-snake",Map.of(ItemID.MAGIC_FANG,.002,ItemID.SNAKEBOSS_SCALE,1.0));
		for(int n=0;n<820;n++) { engine.observe(new Observation(zulrah,1_791_000_002_000L+n,1,Map.of(ItemID.SNAKEBOSS_SCALE,150L),Map.of(ItemID.SNAKEBOSS_SCALE,"Zulrah's scales"),Map.of()),snake); }
		return engine;
	}
}
