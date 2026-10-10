package com.lootluck.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.grounditems.GroundItemsConfig;
import net.runelite.client.plugins.grounditems.config.ValueCalculationMode;
import org.junit.Test;
import static org.junit.Assert.*;

public class EffectValuePaletteTest
{
	private static class GroundConfig implements GroundItemsConfig
	{
		ValueCalculationMode mode=ValueCalculationMode.GE;
		int low=20000;
		@Override public void setHighlightedItem(String value) { }
		@Override public void setHiddenItems(String value) { }
		@Override public ValueCalculationMode valueCalculationMode() { return mode; }
		@Override public int lowValuePrice() { return low; }
	}
	private static PanelState.Item item(long ge,long ha,long quantity)
	{
		PanelState.Item item=new PanelState.Item();item.geEach=ge;item.haEach=ha;item.quantity=quantity;item.ge=ge*quantity;item.ha=ha*quantity;item.valueAvailable=true;item.rarity=Color.RED;return item;
	}
	@Test public void tiersMatchGroundItemsBoundariesAndDisabledThresholds()
	{
		GroundConfig config=new GroundConfig();EffectValuePalette palette=EffectValuePalette.from(config);
		assertEquals(config.defaultColor(),palette.color(item(20000,0,1),EffectColorMode.ITEM_VALUE));
		assertEquals(config.lowValueColor(),palette.color(item(20001,0,1),EffectColorMode.ITEM_VALUE));
		assertEquals(config.lowValueColor(),palette.color(item(100000,0,1),EffectColorMode.ITEM_VALUE));
		assertEquals(config.mediumValueColor(),palette.color(item(100001,0,1),EffectColorMode.ITEM_VALUE));
		assertEquals(config.highValueColor(),palette.color(item(1000001,0,1),EffectColorMode.ITEM_VALUE));
		assertEquals(config.insaneValueColor(),palette.color(item(10000001,0,1),EffectColorMode.ITEM_VALUE));
		long[] boundaries={20000,100000,1000000,10000000};
		for(int tier=0;tier<boundaries.length;tier++) { assertEquals(tier,palette.tier(item(boundaries[tier],0,100),EffectColorMode.ITEM_VALUE));assertEquals(tier+1,palette.tier(item(boundaries[tier]+1,0,100),EffectColorMode.ITEM_VALUE)); }
		config.low=0;assertEquals(config.defaultColor(),EffectValuePalette.from(config).color(item(50000,0,1),EffectColorMode.ITEM_VALUE));
		assertEquals(0,EffectValuePalette.from(config).tier(item(50000,0,1),EffectColorMode.ITEM_VALUE));
		assertEquals(config.lowValueColor(),palette.color(item(50000,0,1),EffectColorMode.ITEM_VALUE)); // detached snapshot
	}
	@Test public void unitAndTotalValuesHonorGeHaAndHighestWithoutChangingRarity()
	{
		GroundConfig config=new GroundConfig();PanelState.Item item=item(5000,25000,100);
		assertEquals(config.defaultColor(),EffectValuePalette.from(config).color(item,EffectColorMode.ITEM_VALUE));
		assertEquals(0,EffectValuePalette.from(config).tier(item,EffectColorMode.ITEM_VALUE));
		assertEquals(config.mediumValueColor(),EffectValuePalette.from(config).color(item,EffectColorMode.TOTAL_VALUE));
		config.mode=ValueCalculationMode.HA;
		assertEquals(config.lowValueColor(),EffectValuePalette.from(config).color(item,EffectColorMode.ITEM_VALUE));
		assertEquals(1,EffectValuePalette.from(config).tier(item,EffectColorMode.ITEM_VALUE));
		assertEquals(config.highValueColor(),EffectValuePalette.from(config).color(item,EffectColorMode.TOTAL_VALUE));
		config.mode=ValueCalculationMode.HIGHEST;
		assertEquals(config.highValueColor(),EffectValuePalette.from(config).color(item,EffectColorMode.TOTAL_VALUE));
		assertEquals(Color.RED,item.rarity);
		item.valueAvailable=false;assertEquals(new Color(160,160,160),EffectValuePalette.from(config).color(item,EffectColorMode.ITEM_VALUE));
		item.valueAvailable=true;item.occurrenceOnly=true;assertEquals(new Color(160,160,160),EffectValuePalette.from(config).color(item,EffectColorMode.TOTAL_VALUE));
		assertEquals(0,EffectValuePalette.from(config).tier(item,EffectColorMode.ITEM_VALUE));
	}
	@Test public void settingPersistsAndSyncs() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			Map<String,Object> saved=new HashMap<>();com.lootluck.LootLuckConfig config=new com.lootluck.LootLuckConfig() {
				@Override public EffectColorMode effectColorMode() { return (EffectColorMode)saved.getOrDefault("effectColorMode",EffectColorMode.ITEM_VALUE); }
			};
			LootLuckSettingsPanel panel=new LootLuckSettingsPanel(config,saved::put,flag->null);
			JComboBox<?> control=null;for(java.awt.Component child:panel.getComponents()) { if(child instanceof JComboBox) { control=(JComboBox<?>)child; } }
			assertNotNull(control);assertEquals(EffectColorMode.ITEM_VALUE,control.getSelectedItem());
			control.setSelectedItem(EffectColorMode.TOTAL_VALUE);assertEquals(EffectColorMode.TOTAL_VALUE,saved.get("effectColorMode"));
			saved.put("effectColorMode",EffectColorMode.ITEM_VALUE);panel.sync();assertEquals(EffectColorMode.ITEM_VALUE,control.getSelectedItem());
		});
	}
	@Test public void renderValueTiersInListAndGridEffects() throws Exception
	{
		GroundConfig config=new GroundConfig();EffectValuePalette palette=EffectValuePalette.from(config);
		long[] values={1,20001,100001,1000001,10000001};
		BufferedImage sheet=new BufferedImage(300,values.length*65,BufferedImage.TYPE_INT_RGB);Graphics2D g=sheet.createGraphics();g.setColor(new Color(30,30,30));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
		for(int index=0;index<values.length;index++)
		{
			Color color=palette.color(item(values[index],0,1),EffectColorMode.ITEM_VALUE);
			g.setColor(Color.LIGHT_GRAY);g.drawString(values[index]+" gp",6,index*65+14);
			Graphics2D row=(Graphics2D)g.create(0,index*65+18,210,44);EffectButton.paintEffect(row,210,44,ItemEffect.RARITY_FADE,color,2);row.dispose();
			Graphics2D grid=(Graphics2D)g.create(235,index*65+18,44,44);EffectButton.paintEffect(grid,44,44,ItemEffect.RARITY_FADE,color,2);grid.dispose();
		}
		g.dispose();javax.imageio.ImageIO.write(sheet,"png",new java.io.File("build/value-effect-previews.png"));
	}
}
