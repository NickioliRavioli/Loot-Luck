package com.lootluck.ui;

import com.lootluck.LootLuckConfig;
import java.awt.Color;
import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemDisplayStyleTest
{
	@Test public void everyGridStylePaintsQuantityAboveAnOpaqueIconAtTopLeft() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			java.awt.image.BufferedImage sprite=new java.awt.image.BufferedImage(36,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D fill=sprite.createGraphics();fill.setColor(Color.CYAN);fill.fillRect(0,0,36,32);fill.dispose();
			int[] previous=null;java.awt.image.BufferedImage sheet=new java.awt.image.BufferedImage(150,50,java.awt.image.BufferedImage.TYPE_INT_ARGB);int position=0;
			for(ItemDisplayStyle style:ItemDisplayStyle.values())
			{
				PanelState state=new PanelState();state.gridView=true;state.iconQuantities=style.iconQuantities;state.stackCounts=style.stackCounts;state.itemEffect=ItemEffect.NONE;
				PanelState.Source source=new PanelState.Source();source.key="test";source.name="Grid";
				PanelState.Item item=new PanelState.Item();item.id=1;item.key="coins";item.name="Coins";item.rate="1/128";item.quantity=12500;item.icon=new javax.swing.ImageIcon(sprite);source.items.add(item);state.sources.add(source);
				LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});panel.update(state);
				javax.swing.JViewport viewport=new javax.swing.JViewport();viewport.setSize(230,400);viewport.setView(panel);viewport.addNotify();viewport.doLayout();layout(panel);
				JButton button=findButton(panel,"Coins, quantity");assertNotNull(button);
				java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(button.getWidth(),button.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);button.paint(image.getGraphics());
				int yellow=0;for(int y=0;y<image.getHeight();y++) { for(int x=0;x<image.getWidth();x++) { if(image.getRGB(x,y)==Color.YELLOW.getRGB()) { yellow++;assertTrue(x>=4&&y>=4&&y<20); } } }assertTrue(style.title,yellow>10);
				int[] pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());if(previous!=null) { assertArrayEquals(previous,pixels); }previous=pixels;
				java.awt.Graphics2D graphics=sheet.createGraphics();graphics.drawImage(image,position*50,0,null);graphics.dispose();position++;viewport.removeNotify();
			}
			try { javax.imageio.ImageIO.write(sheet,"png",new java.io.File("build/grid-count-styles.png")); } catch(java.io.IOException ex) { throw new IllegalStateException(ex); }
		});
	}
	private static JButton findButton(java.awt.Container parent,String prefix)
	{
		for(Component child:parent.getComponents())
		{
			if(child instanceof JButton&&((JButton)child).getAccessibleContext().getAccessibleName().startsWith(prefix)) { return (JButton)child; }
			if(child instanceof java.awt.Container) { JButton found=findButton((java.awt.Container)child,prefix);if(found!=null) { return found; } }
		}
		return null;
	}
	@Test public void previewsUseCompactRowsAndRenderYellowCounts() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			java.awt.image.BufferedImage sprite=new java.awt.image.BufferedImage(36,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D pixels=sprite.createGraphics();pixels.setColor(Color.GRAY);pixels.fillRect(8,4,20,24);pixels.dispose();
			java.awt.image.BufferedImage sheet=new java.awt.image.BufferedImage(230,ItemDisplayStyle.values().length*76,java.awt.image.BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics2D canvas=sheet.createGraphics();canvas.setColor(new Color(34,34,34));canvas.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
			int row=0;
			for(ItemDisplayStyle style:ItemDisplayStyle.values())
			{
				JButton button=LootLuckPanel.stylePreview(style,new javax.swing.ImageIcon(sprite));button.setBorder(javax.swing.BorderFactory.createLineBorder(Color.DARK_GRAY,2));
				assertEquals(44,button.getPreferredSize().height);assertFalse(button.isSelected());button.setSize(210,44);layout(button);
				java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(210,44,java.awt.image.BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D paint=image.createGraphics();button.paint(paint);paint.dispose();
				boolean yellow=false;for(int y=0;y<44;y++) { for(int x=0;x<210;x++) { if(image.getRGB(x,y)==Color.YELLOW.getRGB()) { yellow=true; } } }assertTrue(style.title,yellow);
				canvas.setColor(Color.LIGHT_GRAY);canvas.drawString(style.title,8,row*76+16);canvas.drawImage(image,8,row*76+22,null);row++;
			}
			canvas.dispose();try { javax.imageio.ImageIO.write(sheet,"png",new java.io.File("build/item-count-previews.png")); }catch(java.io.IOException ex) { throw new IllegalStateException(ex); }
		});
	}
	private static void layout(java.awt.Container parent)
	{
		parent.doLayout();for(Component child:parent.getComponents()) { if(child instanceof java.awt.Container) { layout((java.awt.Container)child); } }
	}
	@Test public void previewsSelectPersistAndSyncLegacySettings() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			Map<String,Object> saved=new HashMap<>();
			LootLuckConfig config=new LootLuckConfig() {
				@Override public boolean iconQuantities() { return (boolean)saved.getOrDefault("iconQuantities",true); }
				@Override public boolean stackCounts() { return (boolean)saved.getOrDefault("stackCounts",false); }
			};
			LootLuckSettingsPanel panel=new LootLuckSettingsPanel(config,saved::put,flag->null);
			Map<String,JButton> buttons=new HashMap<>();
			for(Component child:panel.getComponents()) { if(child instanceof JButton) { JButton button=(JButton)child;if(!button.getAccessibleContext().getAccessibleName().startsWith("Preview effect:")) { buttons.put(button.getAccessibleContext().getAccessibleName(),button); } } }
			assertEquals(ItemDisplayStyle.values().length,buttons.size());
			assertEquals("Selected item style",buttons.get(ItemDisplayStyle.ICON_QUANTITY.title).getAccessibleContext().getAccessibleDescription());
			for(ItemDisplayStyle style:ItemDisplayStyle.values())
			{
				JButton button=buttons.get(style.title);button.doClick();assertEquals(style,ItemDisplayStyle.selected(config));
				assertEquals(style.iconQuantities,saved.get("iconQuantities"));assertEquals(style.stackCounts,saved.get("stackCounts"));
				assertEquals(1,buttons.values().stream().filter(b->"Selected item style".equals(b.getAccessibleContext().getAccessibleDescription())).count());
				assertEquals(new Color(223,192,113),((LineBorder)button.getBorder()).getLineColor());
			}
			saved.put("stackCounts",true);saved.put("iconQuantities",true);panel.sync();assertEquals("Selected item style",buttons.get(ItemDisplayStyle.RIGHT_COUNTS.title).getAccessibleContext().getAccessibleDescription());
		});
	}
}
