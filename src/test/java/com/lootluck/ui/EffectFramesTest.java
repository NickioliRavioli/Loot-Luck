package com.lootluck.ui;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class EffectFramesTest
{
	@Test public void matchingTilesShareFramesButColorSizeAndAnimationStayDistinct()
	{
		EffectFrames cache=new EffectFrames(100000,20);
		BufferedImage first=cache.get(ItemEffect.RARITY_AURORA,Color.RED,42,42,42,42,25);
		assertSame(first,cache.get(ItemEffect.RARITY_AURORA,Color.RED,42,42,42,42,25));
		assertNotSame(first,cache.get(ItemEffect.RARITY_AURORA,Color.BLUE,42,42,42,42,25));
		assertEquals(84,cache.get(ItemEffect.RARITY_AURORA,Color.RED,42,42,84,84,25).getWidth());
		int[] earlier=first.getRGB(0,0,42,42,null,0,42);
		BufferedImage later=cache.get(ItemEffect.RARITY_AURORA,Color.RED,42,42,42,42,55);assertSame(first,later);
		assertFalse(java.util.Arrays.equals(earlier,later.getRGB(0,0,42,42,null,0,42)));
		BufferedImage fade=cache.get(ItemEffect.RARITY_FADE,Color.RED,42,42,42,42,25);
		assertSame(fade,cache.get(ItemEffect.RARITY_FADE,Color.RED,42,42,42,42,100));
		assertEquals(0,first.getRGB(0,0));
	}
	@Test public void animatedBuffersAreReusedWithoutAccumulatingOldPixels()
	{
		EffectFrames cache=new EffectFrames(100000,20);
		BufferedImage first=cache.get(ItemEffect.RARITY_AURORA,Color.RED,210,44,263,55,0);
		for(int tick=1;tick<=100;tick++) { assertSame(first,cache.get(ItemEffect.RARITY_AURORA,Color.RED,210,44,263,55,tick)); }
		EffectFrames fresh=new EffectFrames(100000,20);BufferedImage reference=fresh.get(ItemEffect.RARITY_AURORA,Color.RED,210,44,263,55,100);
		assertArrayEquals(reference.getRGB(0,0,263,55,null,0,263),first.getRGB(0,0,263,55,null,0,263));
		assertEquals(1,cache.size());assertEquals(263*55,cache.pixels());
	}
	@Test public void memoryAndEntryLimitsHoldAcrossManyColorsAndOversizedFrames()
	{
		EffectFrames cache=new EffectFrames(4000,2);
		for(int i=0;i<100;i++) { cache.get(ItemEffect.SHIMMER,new Color(i,20,30),42,42,42,42,i);assertTrue(cache.pixels()<=4000);assertTrue(cache.size()<=2); }
		cache.get(ItemEffect.SHIMMER,Color.RED,100,100,100,100,1);assertTrue(cache.pixels()<=4000);assertTrue(cache.size()<=2);
		cache.clear();assertEquals(0,cache.pixels());assertEquals(0,cache.size());
	}
}
