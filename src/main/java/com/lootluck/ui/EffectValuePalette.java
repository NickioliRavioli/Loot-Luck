package com.lootluck.ui;

import java.awt.Color;
import net.runelite.client.plugins.grounditems.GroundItemsConfig;
import net.runelite.client.plugins.grounditems.config.ValueCalculationMode;

/** Detached snapshot of Ground Items' value tiers; no config access during painting. */
public final class EffectValuePalette
{
	private final int[] thresholds;
	private final Color[] colors;
	private final Color defaultColor;
	private final ValueCalculationMode calculation;
	private EffectValuePalette(GroundItemsConfig config)
	{
		thresholds=new int[]{config.insaneValuePrice(),config.highValuePrice(),config.mediumValuePrice(),config.lowValuePrice()};
		colors=new Color[]{config.insaneValueColor(),config.highValueColor(),config.mediumValueColor(),config.lowValueColor()};
		defaultColor=config.defaultColor();calculation=config.valueCalculationMode();
	}
	public static EffectValuePalette from(GroundItemsConfig config) { return new EffectValuePalette(config); }
	public Color color(PanelState.Item item,EffectColorMode mode)
	{
		if(!item.valueAvailable||item.occurrenceOnly) { return new Color(160,160,160); }
		int tier=tier(item,mode);return tier==0?defaultColor:colors[colors.length-tier];
	}
	/** Default, low, medium, high, insane: strength follows tiers, never RGB brightness. */
	public int tier(PanelState.Item item,EffectColorMode mode)
	{
		if(!item.valueAvailable||item.occurrenceOnly) { return 0; }
		long ge=mode==EffectColorMode.TOTAL_VALUE?item.ge:item.geEach;
		long ha=mode==EffectColorMode.TOTAL_VALUE?item.ha:item.haEach;
		long value=calculation==ValueCalculationMode.GE?ge:calculation==ValueCalculationMode.HA?ha:Math.max(ge,ha);
		// Ground Items disables nonpositive thresholds and uses a strict greater-than comparison.
		for(int index=0;index<thresholds.length;index++) { if(thresholds[index]>0&&value>thresholds[index]) { return thresholds.length-index; } }
		return 0;
	}
}
