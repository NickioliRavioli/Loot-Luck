package com.lootluck.ui;

import com.lootluck.rates.RateRow;
import com.lootluck.rates.DropTableGroups;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemOrderingTest
{
	private PanelState.Item item(String name,String rate,int table,int position)
	{
		PanelState.Item item=new PanelState.Item();item.name=name;item.rate=rate;item.tableOrder=table;item.wikiOrder=position;return item;
	}
	@Test public void raritySortsBothDirectionsWithVariableRatesLast()
	{
		PanelState.Item common=item("Common","1/5",1,1),rare=item("Rare","1/1000",0,0),varies=item("Variable","Varies",1,2);
		List<PanelState.Item> items=new ArrayList<>(Arrays.asList(common,varies,rare));items.sort(ItemOrdering.comparator(1,false));assertEquals(Arrays.asList(rare,common,varies),items);
		items.sort(ItemOrdering.comparator(3,false));assertEquals(Arrays.asList(common,rare,varies),items);
		items.sort(ItemOrdering.comparator(3,true));assertEquals(Arrays.asList(rare,common,varies),items);
		items.sort(ItemOrdering.comparator(4,true));assertEquals(Arrays.asList(rare,common,varies),items);
	}
	@Test public void publishedVariableRatesAndWintertodtSectionsArePreserved()
	{
		RateRow row=new RateRow();row.owner="Reward Cart";row.variant="Reward Cart";row.rawRate="Varies";row.conditions="Depends on skill level";row.itemName="Oak logs";
		assertEquals("Varies",row.display());assertEquals(new java.awt.Color(255,163,255),row.color());assertNull(row.publishedProbability());assertEquals("Logs",DropTableGroups.table(row));
		row.itemName="Grimy ranarr weed";assertEquals("Herbs",DropTableGroups.table(row));row.itemName="Spirit seed";assertEquals("Seeds",DropTableGroups.table(row));
		row.owner="Other source";row.variant="Other source#Rare drop table";assertEquals(row.variant,DropTableGroups.table(row));
		row.rawRate="";assertEquals("*",row.display());
	}
}
