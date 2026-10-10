package com.lootluck;

import com.lootluck.model.SourceIdentity;
import com.lootluck.rates.ItemRateMatcher;
import com.lootluck.rates.RateRow;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemRateIndexTest
{
	@Test public void indexedLookupPreservesIdPrecedenceNotesAliasesAndOriginalRowOrder()
	{
		List<RateRow> rows=new ArrayList<>();
		String[] names={"Bones","Bones","Coins","Casket (Reward pool)","Clue scroll (easy)","Raw t-bone steak#Special","No ID#Special"};
		for(int i=0;i<names.length;i++) { RateRow row=new RateRow();row.itemName=names[i];row.quantity=i==1?"1 (noted)":"1";if(i<3||i==5) { row.itemIds.add(i==1?1:i+1); }if(i==5) { row.gameName="Raw t-bone steak"; }rows.add(row); }
		// Repeated row references must retain the same order and multiplicity too.
		rows.add(rows.get(0));ItemRateMatcher.Index index=new ItemRateMatcher.Index(rows);
		List<SourceIdentity> sources=List.of(new SourceIdentity("NPC","Cow","",""),new SourceIdentity("EVENT","Reward pool","",""));
		for(SourceIdentity source:sources) { for(int id=0;id<9;id++) { for(int canonical=0;canonical<9;canonical++) { for(String name:List.of("Bones","Bones (noted)","Coins","Casket","Scroll box (easy)","Raw t-bone steak","No ID#Special","Unknown"))
		{
			assertEquals(ItemRateMatcher.item(id,canonical,name,source,rows),index.item(id,canonical,name,source));
		} } } }
	}
}
