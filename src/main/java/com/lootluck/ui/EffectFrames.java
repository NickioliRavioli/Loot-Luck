package com.lootluck.ui;

import java.awt.Color;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** EDT-owned, bounded background buffers, drawn immediately and shared by matching tiles. */
final class EffectFrames
{
	private final int pixelLimit,entryLimit;
	private long pixels;
	private final Map<Key,Frame> frames=new LinkedHashMap<>(16,.75f,true);
	EffectFrames(int pixelLimit,int entryLimit) { this.pixelLimit=pixelLimit;this.entryLimit=entryLimit; }
	BufferedImage get(ItemEffect effect,Color color,int width,int height,int pixelWidth,int pixelHeight,long tick)
	{
		Key key=new Key(effect,color.getRGB(),width,height,pixelWidth,pixelHeight);
		long frameTick=effect==ItemEffect.RARITY_FADE?0:tick;
		Frame previous=frames.get(key);if(previous!=null&&previous.tick==frameTick) { return previous.image; }
		// Keep the same image across ticks so Java2D can reuse its managed surface.
		// Flushing/replacing it every tick also repeatedly allocates GPU textures.
		BufferedImage image=previous==null?new BufferedImage(pixelWidth,pixelHeight,BufferedImage.TYPE_INT_ARGB_PRE):previous.image;
		Graphics2D graphics=image.createGraphics();
		try { graphics.setComposite(AlphaComposite.Clear);graphics.fillRect(0,0,pixelWidth,pixelHeight);graphics.setComposite(AlphaComposite.SrcOver);graphics.scale((double)pixelWidth/width,(double)pixelHeight/height);EffectButton.paintEffect(graphics,width,height,effect,color,tick*.05); }
		finally { graphics.dispose(); }
		long size=(long)pixelWidth*pixelHeight;
		if(size<=pixelLimit)
		{
			if(previous!=null) { previous.tick=frameTick;return image; }
			frames.put(key,new Frame(frameTick,image));pixels+=size;
			Iterator<Frame> entries=frames.values().iterator();
			while(pixels>pixelLimit||frames.size()>entryLimit) { Frame evicted=entries.next();pixels-=evicted.size();evicted.image.flush();entries.remove(); }
		}
		return image;
	}
	void clear() { frames.values().forEach(frame->frame.image.flush());frames.clear();pixels=0; }
	long pixels() { return pixels; }
	int size() { return frames.size(); }
	private static final class Frame
	{
		long tick;final BufferedImage image;
		Frame(long tick,BufferedImage image) { this.tick=tick;this.image=image; }
		long size() { return (long)image.getWidth()*image.getHeight(); }
	}
	private static final class Key
	{
		final ItemEffect effect;final int color,width,height,pixelWidth,pixelHeight;
		Key(ItemEffect effect,int color,int width,int height,int pixelWidth,int pixelHeight) { this.effect=effect;this.color=color;this.width=width;this.height=height;this.pixelWidth=pixelWidth;this.pixelHeight=pixelHeight; }
		@Override public boolean equals(Object other)
		{
			if(!(other instanceof Key)) { return false; }Key key=(Key)other;
			return effect==key.effect&&color==key.color&&width==key.width&&height==key.height&&pixelWidth==key.pixelWidth&&pixelHeight==key.pixelHeight;
		}
		@Override public int hashCode() { return Objects.hash(effect,color,width,height,pixelWidth,pixelHeight); }
	}
}
