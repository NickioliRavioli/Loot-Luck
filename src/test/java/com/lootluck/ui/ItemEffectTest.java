package com.lootluck.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemEffectTest
{
	private static BufferedImage frame(ItemEffect effect,Color color,double seconds,int width,int height)
	{
		BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
		g.setColor(new Color(30,30,30));g.fillRect(0,0,width,height);EffectButton.paintEffect(g,width,height,effect,color,seconds);g.dispose();return image;
	}
	private static boolean different(BufferedImage a,BufferedImage b)
	{
		for(int y=0;y<a.getHeight();y++) { for(int x=0;x<a.getWidth();x++) { if(a.getRGB(x,y)!=b.getRGB(x,y)) { return true; } } }return false;
	}
	@Test public void effectsAnimateStayInsideBordersAndSupportBothLayouts() throws Exception
	{
		BufferedImage sheet=new BufferedImage(460,ItemEffect.values().length*80,BufferedImage.TYPE_INT_ARGB);Graphics2D output=sheet.createGraphics();output.setColor(new Color(34,34,34));output.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
		int index=0;
		for(ItemEffect effect:ItemEffect.values())
		{
			BufferedImage row=frame(effect,Color.RED,1.25,210,44),later=frame(effect,Color.RED,2.75,210,44),grid=frame(effect,Color.RED,1.25,42,54);
			assertEquals(new Color(30,30,30).getRGB(),row.getRGB(0,0));assertEquals(new Color(30,30,30).getRGB(),grid.getRGB(41,53));
			if(effect!=ItemEffect.NONE) { assertTrue(effect.title,different(row,later));assertTrue(effect.title,different(row,frame(ItemEffect.NONE,Color.RED,1.25,210,44))); }
			output.setColor(Color.LIGHT_GRAY);output.drawString(effect.title,8,index*80+15);output.drawImage(row,8,index*80+22,null);output.drawImage(later,230,index*80+22,null);output.drawImage(grid,410,index*80+19,null);index++;
		}
		output.dispose();Path path=Path.of("build","effect-previews.png");java.nio.file.Files.createDirectories(path.getParent());ImageIO.write(sheet,"png",path.toFile());
	}
	@Test public void previewButtonsPersistAndSyncSelectedEffect() throws Exception
	{
		javax.swing.SwingUtilities.invokeAndWait(()->{
			java.util.Map<String,Object> saved=new java.util.HashMap<>();com.lootluck.LootLuckConfig config=new com.lootluck.LootLuckConfig() { @Override public ItemEffect itemEffect() { return (ItemEffect)saved.getOrDefault("itemEffect",ItemEffect.SHIMMER); } };
			LootLuckSettingsPanel panel=new LootLuckSettingsPanel(config,saved::put,flag->null);java.util.List<javax.swing.JButton> buttons=new java.util.ArrayList<>();
			for(java.awt.Component child:panel.getComponents()) { if(child instanceof javax.swing.JButton&&((javax.swing.JButton)child).getAccessibleContext().getAccessibleName().startsWith("Preview effect:")) { buttons.add((javax.swing.JButton)child); } }
			assertEquals(ItemEffect.values().length,buttons.size());
			for(int i=0;i<buttons.size();i++) { buttons.get(i).doClick();assertEquals(ItemEffect.values()[i],config.itemEffect());assertTrue(buttons.get(i).isSelected());assertEquals(1,buttons.stream().filter(javax.swing.JButton::isSelected).count()); }
		});
	}
	@Test public void rarityAuraUsesTheItemColor()
	{
		for(ItemEffect effect:new ItemEffect[]{ItemEffect.RARITY,ItemEffect.RARITY_PRISM,ItemEffect.RARITY_AURORA,ItemEffect.RARITY_OPAL})
		{
			BufferedImage red=frame(effect,Color.RED,2,210,44),blue=frame(effect,Color.BLUE,2,210,44);
			assertTrue(effect.title,different(red,blue));Color redPixel=new Color(red.getRGB(100,20)),bluePixel=new Color(blue.getRGB(100,20));assertTrue(effect.title,redPixel.getRed()>redPixel.getBlue());assertTrue(effect.title,bluePixel.getBlue()>bluePixel.getRed());
		}
	}
}
