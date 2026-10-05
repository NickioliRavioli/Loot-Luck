package com.lootluck.ui;

import java.util.List;
import java.util.ArrayList;
import java.awt.Color;

/** Detached render data. Worker-owned ledger objects never reach Swing. */
public final class PanelState
{
	public String notice="Loading local observations…", dataStatus="Bundled Wiki rates";
	public boolean showGe=true, showHa=false, luckColors=true, showAllRates=false;
	public boolean showWikiLinks=true;
	public boolean includeUnobtained;
	public boolean groupByDropTable;
	public boolean hideUnobtainedOtherWorld;
	public boolean gridView;
	public boolean canManageData;
	public boolean iconQuantities=true;
	public boolean stackCounts=false;
	public List<Source> sources=new ArrayList<>();
	public List<Source> collections=new ArrayList<>();
	public List<Item> allItems=new ArrayList<>();
	public static final class Source
	{
		public String key,name,countTooltip,value,wikiUrl;
		public long count,lastReceived;
		public List<Item> items=new ArrayList<>();
	}
	public static final class Item
	{
		public String key,name,rate,rateTooltip,reason;
		public String worldMarker="";
		public String dropTable="";
		public int tableOrder=Integer.MAX_VALUE,wikiOrder=Integer.MAX_VALUE;
		public Double publishedRarity;
		public int id;
		public long quantity,occurrences,lastReceived;
		public Long attempts,dry;
		public Double probability,expected,ratio,observed,noDrop,upper,lower,dryness;
		public Color rarity;
		public long ge,ha,geEach,haEach;
		public boolean valueAvailable;
		public javax.swing.Icon icon;
		public List<RateOption> rateOptions=new ArrayList<>();
		public long unresolvedQuantity,unresolvedOccurrences;
	}
	public static final class RateOption
	{
		public String key,table,rate,tooltip,dropQuantity;
		public Color color;
		public Long quantity,occurrences;
		public boolean historical;
	}
}
