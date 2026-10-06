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
