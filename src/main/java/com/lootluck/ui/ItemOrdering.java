package com.lootluck.ui;

import java.util.Comparator;

final class ItemOrdering
{
	private ItemOrdering() { }
	static Comparator<PanelState.Item> comparator(int sort,boolean grouped)
	{
		Comparator<PanelState.Item> order;
		switch(sort)
		{
			case 1: order=Comparator.comparing(item->probability(item),Comparator.nullsLast(Comparator.naturalOrder()));break;
			case 2: order=Comparator.comparing((PanelState.Item item)->item.dryness,Comparator.nullsLast(Comparator.reverseOrder()));break;
			case 3: order=Comparator.comparing(item->probability(item),Comparator.nullsLast(Comparator.reverseOrder()));break;
			case 4: order=Comparator.comparingInt(item->item.wikiOrder);break;
			default: order=Comparator.comparingLong((PanelState.Item item)->item.lastReceived).reversed();
		}
		order=order.thenComparing(item->item.name);
		return grouped?Comparator.comparingInt((PanelState.Item item)->item.tableOrder).thenComparing(order):order;
	}
	private static Double probability(PanelState.Item item)
	{
		if(item.publishedRarity!=null) { return item.publishedRarity; }
		try { return "Always".equals(item.rate.replace(" *",""))?1.0:item.rate.replace("~","").replace(" *","").startsWith("1/")?1/Double.parseDouble(item.rate.replace("~","").replace(" *","").substring(2).replace(",","")):null; }
		catch(RuntimeException ignored) { return null; }
	}
}
