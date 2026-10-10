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
		BufferedImage later=cache.get(ItemEffect.RARITY_AURORA,Color.RED,42,42,42,42,55);assertNotSame(first,later);
		boolean changed=false;for(int y=0;y<42;y++) { for(int x=0;x<42;x++) { changed|=first.getRGB(x,y)!=later.getRGB(x,y); } }assertTrue(changed);
		BufferedImage fade=cache.get(ItemEffect.RARITY_FADE,Color.RED,42,42,42,42,25);
		assertSame(fade,cache.get(ItemEffect.RARITY_FADE,Color.RED,42,42,42,42,100));
		assertEquals(0,first.getRGB(0,0));
	}
	@Test public void memoryAndEntryLimitsHoldAcrossManyColorsAndOversizedFrames()
	{
		EffectFrames cache=new EffectFrames(4000,2);
		for(int i=0;i<100;i++) { cache.get(ItemEffect.SHIMMER,new Color(i,20,30),42,42,42,42,i);assertTrue(cache.pixels()<=4000);assertTrue(cache.size()<=2); }
		cache.get(ItemEffect.SHIMMER,Color.RED,100,100,100,100,1);assertTrue(cache.pixels()<=4000);assertTrue(cache.size()<=2);
		cache.clear();assertEquals(0,cache.pixels());assertEquals(0,cache.size());
	}
}
