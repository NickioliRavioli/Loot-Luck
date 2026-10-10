package com.lootluck.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.RepaintManager;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class ValueScaledEffectTest
{
	private static final ItemEffect[] EFFECTS={ItemEffect.VALUE_FADE,ItemEffect.VALUE_DUST,ItemEffect.VALUE_AURORA};
	private static BufferedImage frame(ItemEffect effect,int tier,double seconds,int width)
	{
		BufferedImage image=new BufferedImage(width,44,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();
		try { EffectButton.paintEffect(graphics,width,44,effect,new Color(227,104,98),seconds,tier); }finally { graphics.dispose(); }return image;
	}
	private static int[] pixels(BufferedImage image) { return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()); }
	private static long opacity(BufferedImage image) { long sum=0;for(int pixel:pixels(image)) { sum+=pixel>>>24; }return sum; }
	@Test public void strengthIncreasesAcrossTiersInBothLayoutsAndLowestTierHasNoEffect()
	{
		for(int width:new int[]{42,210})
		{
			for(ItemEffect effect:EFFECTS)
			{
				assertEquals(0,opacity(frame(effect,0,2,width)));
				long previous=0;for(int tier=1;tier<5;tier++) { BufferedImage image=frame(effect,tier,2,width);long alpha=opacity(image);assertTrue(effect+" tier "+tier,alpha>previous);previous=alpha;assertEquals(0,image.getRGB(0,0)); }
				assertTrue(opacity(frame(effect,0,2,width))*20<opacity(frame(effect,4,2,width)));
				assertArrayEquals(pixels(frame(effect,0,2,width)),pixels(frame(ItemEffect.VALUE_FADE,0,2,width)));
				assertArrayEquals(pixels(frame(effect,0,2,width)),pixels(frame(effect,0,7,width)));
			}
			assertArrayEquals(pixels(frame(ItemEffect.VALUE_FADE,4,2,width)),pixels(frame(ItemEffect.VALUE_FADE,4,7,width)));
			assertFalse(Arrays.equals(pixels(frame(ItemEffect.VALUE_DUST,4,2,width)),pixels(frame(ItemEffect.VALUE_FADE,4,2,width))));
			assertFalse(Arrays.equals(pixels(frame(ItemEffect.VALUE_DUST,4,2,width)),pixels(frame(ItemEffect.VALUE_DUST,4,7,width))));
		}
	}
	@Test public void frameSharingDistinguishesStrengthEvenWithIdenticalColours()
	{
		EffectFrames cache=new EffectFrames(100000,20);
		for(ItemEffect effect:EFFECTS)
		{
			BufferedImage low=cache.get(effect,Color.RED,42,44,53,55,20,0),high=cache.get(effect,Color.RED,42,44,53,55,20,4);int[] snapshot=pixels(low);
			assertNotSame(low,high);assertTrue(opacity(low)*20<opacity(high));
			assertSame(low,cache.get(effect,Color.RED,42,44,53,55,90,0));assertArrayEquals(snapshot,pixels(low));
			assertSame(high,cache.get(effect,Color.RED,42,44,53,55,20,4));
		}
	}
	@Test public void quietTiersDoNotScheduleAnimationDamage() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			JPanel view=new JPanel(null);view.setPreferredSize(new java.awt.Dimension(220,180));view.setSize(220,180);
			java.util.concurrent.atomic.AtomicInteger foregrounds=new java.util.concurrent.atomic.AtomicInteger();
			for(int index=0;index<EFFECTS.length;index++) { EffectButton button=new EffectButton(EFFECTS[index],Color.RED,0) { @Override protected void paintForeground(Graphics2D graphics) { foregrounds.incrementAndGet(); } };button.setBounds(0,index*50,210,44);view.add(button); }
			JViewport viewport=new JViewport();viewport.setSize(220,180);viewport.setView(view);RepaintManager previous=RepaintManager.currentManager(viewport);java.util.List<JComponent> targets=new java.util.ArrayList<>();
			RepaintManager.setCurrentManager(new RepaintManager() { @Override public void addDirtyRegion(JComponent component,int x,int y,int width,int height) { targets.add(component); } });
			try
			{
				viewport.addNotify();viewport.doLayout();targets.clear();EffectButton.animate();assertTrue(targets.isEmpty());
				for(java.awt.Component child:view.getComponents())
				{
					EffectButton button=(EffectButton)child,plain=new EffectButton(ItemEffect.NONE,Color.RED);plain.setSize(button.getSize());
					for(int pass=0;pass<2;pass++)
					{
						BufferedImage image=new BufferedImage(210,44,BufferedImage.TYPE_INT_ARGB),reference=new BufferedImage(210,44,BufferedImage.TYPE_INT_ARGB);Graphics2D first=image.createGraphics(),second=reference.createGraphics();
						try { button.paint(first);plain.paint(second); }finally { first.dispose();second.dispose(); }assertArrayEquals(pixels(reference),pixels(image));
					}
				}
				assertEquals("Lowest tiers must use the ordinary foreground path without allocating a cache",EFFECTS.length*2,foregrounds.get());
				EffectButton high=new EffectButton(ItemEffect.VALUE_DUST,Color.RED,4);high.setBounds(0,0,210,44);view.add(high);targets.clear();EffectButton.animate();assertEquals(java.util.Collections.singletonList(viewport),targets);
			}
			finally { viewport.removeNotify();RepaintManager.setCurrentManager(previous); }
		});
	}
	@Test public void valueOptionsShowUnitModeAndPreserveSavedChoiceForOtherEffects() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			Map<String,Object> saved=new HashMap<>();saved.put("effectColorMode",EffectColorMode.TOTAL_VALUE);
			com.lootluck.LootLuckConfig config=new com.lootluck.LootLuckConfig() {
				@Override public ItemEffect itemEffect() { return (ItemEffect)saved.getOrDefault("itemEffect",ItemEffect.RARITY_FADE); }
				@Override public EffectColorMode effectColorMode() { return (EffectColorMode)saved.get("effectColorMode"); }
			};
			LootLuckSettingsPanel panel=new LootLuckSettingsPanel(config,saved::put,flag->null);JComboBox<?> mode=null;
			for(java.awt.Component child:panel.getComponents()) { if(child instanceof JComboBox) { mode=(JComboBox<?>)child; } }assertNotNull(mode);
			for(ItemEffect effect:EFFECTS)
			{
				for(java.awt.Component child:panel.getComponents()) { if(child instanceof JButton&&("Preview effect: "+effect.title).equals(((JButton)child).getAccessibleContext().getAccessibleName())) { ((JButton)child).doClick(); } }
				assertEquals(effect,config.itemEffect());assertFalse(mode.isEnabled());assertEquals(EffectColorMode.ITEM_VALUE,mode.getSelectedItem());assertEquals(EffectColorMode.TOTAL_VALUE,config.effectColorMode());
			}
			saved.put("itemEffect",ItemEffect.RARITY_FADE);panel.sync();assertTrue(mode.isEnabled());assertEquals(EffectColorMode.TOTAL_VALUE,mode.getSelectedItem());
			assertEquals(EffectColorMode.ITEM_VALUE,new com.lootluck.LootLuckConfig() {}.effectColorMode());
		});
	}
	@Test public void renderTierComparison() throws Exception
	{
		BufferedImage sheet=new BufferedImage(890,345,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=sheet.createGraphics();graphics.setColor(new Color(30,30,30));graphics.fillRect(0,0,890,345);
		String[] tiers={"Default: no effect","Low","Medium","High","Insane: strongest"};
		for(int column=0;column<EFFECTS.length;column++)
		{
			int left=column*295+10;graphics.setColor(Color.LIGHT_GRAY);graphics.drawString(EFFECTS[column].title,left,18);
			for(int tier=0;tier<5;tier++) { int top=35+tier*60;graphics.setColor(Color.LIGHT_GRAY);graphics.drawString(tiers[tier],left,top);graphics.drawImage(frame(EFFECTS[column],tier,2,210),left,top+5,null);graphics.drawImage(frame(EFFECTS[column],tier,2,42),left+225,top+5,null); }
		}
		graphics.dispose();javax.imageio.ImageIO.write(sheet,"png",new java.io.File("build/value-scaled-effect-previews.png"));
	}
	@Test public void itemViewsUseUnitValueAndRefreshStrengthWhenTierColoursAreIdentical() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			for(boolean grid:new boolean[]{false,true}) { for(String view:new String[]{"By source","Recent collections","All items"})
			{
				PanelState state=new PanelState();state.gridView=grid;state.itemEffect=ItemEffect.VALUE_FADE;state.effectColorMode=EffectColorMode.TOTAL_VALUE;
				PanelState.Item item=new PanelState.Item();item.key="test-item";item.name="Test item";item.rate="1/128";item.rarity=Color.BLUE;item.quantity=1000;item.geEach=50000;item.ge=50000000;item.valueAvailable=true;
				PanelState.Source source=new PanelState.Source();source.key="source";source.name="Source";source.items.add(item);state.sources.add(source);state.collections.add(source);state.allItems.add(item);
				state.effectValuePalette=identicalColours(20000);LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});panel.update(state);
				for(java.awt.Component child:((java.awt.Container)panel.getComponent(0)).getComponents()) { if(child instanceof JComboBox&&((JComboBox<?>)child).getItemAt(0).equals("By source")) { ((JComboBox<?>)child).setSelectedItem(view); } }
				JViewport viewport=new JViewport();viewport.setSize(230,400);viewport.setView(panel);
				try
				{
					viewport.addNotify();viewport.doLayout();layout(panel);EffectButton first=findItem(panel);assertNotNull(first);assertBackground(first,1);
					state.effectValuePalette=identicalColours(100000);panel.update(state);viewport.doLayout();layout(panel);EffectButton updated=findItem(panel);assertNotNull(updated);assertNotSame(first,updated);assertBackground(updated,0);
				}
				finally { viewport.removeNotify(); }
			} }
		});
	}
	private static EffectValuePalette identicalColours(int low)
	{
		return EffectValuePalette.from(new net.runelite.client.plugins.grounditems.GroundItemsConfig() {
			@Override public void setHighlightedItem(String value) { }
			@Override public void setHiddenItems(String value) { }
			@Override public int lowValuePrice() { return low; }
			@Override public Color defaultColor() { return Color.RED; }
			@Override public Color lowValueColor() { return Color.RED; }
			@Override public net.runelite.client.plugins.grounditems.config.ValueCalculationMode valueCalculationMode() { return net.runelite.client.plugins.grounditems.config.ValueCalculationMode.GE; }
		});
	}
	private static EffectButton findItem(java.awt.Container parent)
	{
		for(java.awt.Component child:parent.getComponents()) { if(child instanceof EffectButton&&((EffectButton)child).getAccessibleContext().getAccessibleName().startsWith("Test item")) { return (EffectButton)child; }if(child instanceof java.awt.Container) { EffectButton found=findItem((java.awt.Container)child);if(found!=null) { return found; } } }return null;
	}
	private static void layout(java.awt.Container parent) { parent.doLayout();for(java.awt.Component child:parent.getComponents()) { if(child instanceof java.awt.Container) { layout((java.awt.Container)child); } } }
	private static void assertBackground(EffectButton actual,int tier)
	{
		EffectButton expected=new EffectButton(ItemEffect.VALUE_FADE,Color.RED,tier);expected.setSize(actual.getSize());expected.setBackground(actual.getBackground());expected.setBorder(actual.getBorder());
		BufferedImage actualImage=new BufferedImage(actual.getWidth(),actual.getHeight(),BufferedImage.TYPE_INT_ARGB),expectedImage=new BufferedImage(actual.getWidth(),actual.getHeight(),BufferedImage.TYPE_INT_ARGB);
		Graphics2D first=actualImage.createGraphics(),second=expectedImage.createGraphics();try { actual.paintComponent(first);expected.paintComponent(second); }finally { first.dispose();second.dispose(); }
		assertEquals(expectedImage.getRGB(22,20),actualImage.getRGB(22,20));
	}
}
