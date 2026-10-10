package com.lootluck.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class AuroraSpritesTest
{
	@Test public void tintAndAnimationReuseTheSameSheetAcrossFramesAndSizes()
	{
		AuroraSprites sprites=new AuroraSprites(8*1024*1024);
		for(int width:new int[]{42,210})
		{
			BufferedImage red=sprites.sheet(Color.RED,width),blue=sprites.sheet(Color.BLUE,width);
			assertNotSame(red,blue);assertSame(red,sprites.sheet(Color.RED,width+10));
			BufferedImage first=paint(sprites,width,44,Color.RED,0),later=paint(sprites,width,44,Color.RED,60);
			assertFalse(java.util.Arrays.equals(first.getRGB(0,0,width,44,null,0,width),later.getRGB(0,0,width,44,null,0,width)));
			assertArrayEquals(first.getRGB(0,0,width,44,null,0,width),paint(sprites,width,44,Color.RED,AuroraSprites.FRAMES).getRGB(0,0,width,44,null,0,width));
			for(int tick=0;tick<480;tick++) { paint(sprites,width,44,Color.RED,tick);assertSame(red,sprites.sheet(Color.RED,width)); }
			Color redPixel=new Color(first.getRGB(width/2,22),true),bluePixel=new Color(paint(sprites,width,44,Color.BLUE,0).getRGB(width/2,22),true);
			assertEquals(255,redPixel.getRed());assertEquals(0,redPixel.getBlue());assertEquals(255,bluePixel.getBlue());assertEquals(0,bluePixel.getRed());assertEquals(redPixel.getAlpha(),bluePixel.getAlpha());
			long seamDifference=0;
			BufferedImage last=paint(sprites,width,44,Color.RED,AuroraSprites.FRAMES-1);
			for(int y=0;y<44;y++) { for(int x=0;x<width;x++) { seamDifference+=Math.abs((first.getRGB(x,y)>>>24)-(last.getRGB(x,y)>>>24)); } }
			assertTrue("Loop seam should be one small motion step",(double)seamDifference/(width*44)<2);
		}
		assertEquals(4,sprites.size());
	}
	@Test public void coloredSheetsAreBoundedAndReleased()
	{
		AuroraSprites sprites=new AuroraSprites(1000000);
		for(int index=0;index<30;index++)
		{
			sprites.sheet(new Color(index*8,40,60),42);sprites.sheet(new Color(index*8,40,60),210);
			assertTrue(sprites.pixels()<=1000000);assertTrue(sprites.size()<=4);
		}
		sprites.clear();assertEquals(0,sprites.pixels());assertEquals(0,sprites.size());
	}
	private static BufferedImage paint(AuroraSprites sprites,int width,int height,Color color,long tick)
	{
		BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();
		try { sprites.paint(graphics,width,height,color,tick); }finally { graphics.dispose(); }return image;
	}
}
