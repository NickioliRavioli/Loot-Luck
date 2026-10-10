package com.lootluck.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.util.ImageUtil;

/** EDT-owned tinted atlases. Animation only selects a rectangle of an unchanged image. */
final class AuroraSprites
{
	static final int FRAMES=240,COLUMNS=16;
	private static final class Masks
	{
		static final BufferedImage GRID=ImageUtil.loadImageResource(AuroraSprites.class,"aurora-grid.png");
		static final BufferedImage LIST=ImageUtil.loadImageResource(AuroraSprites.class,"aurora-list.png");
	}
	private final long pixelLimit;
	private long pixels;
	private final Map<Long,BufferedImage> sheets=new LinkedHashMap<>(16,.75f,true);
	AuroraSprites(long pixelLimit) { this.pixelLimit=pixelLimit; }
	BufferedImage sheet(Color color,int width)
	{
		boolean grid=width<80;long key=((long)color.getRGB()<<1)|(grid?1:0);
		BufferedImage cached=sheets.get(key);if(cached!=null) { return cached; }
		BufferedImage mask=grid?Masks.GRID:Masks.LIST;
		int[] tinted=mask.getRGB(0,0,mask.getWidth(),mask.getHeight(),null,0,mask.getWidth());
		for(int index=0;index<tinted.length;index++) { tinted[index]=(tinted[index]&0xff000000)|(color.getRGB()&0xffffff); }
		BufferedImage image=new BufferedImage(mask.getWidth(),mask.getHeight(),BufferedImage.TYPE_INT_ARGB_PRE);
		image.setRGB(0,0,image.getWidth(),image.getHeight(),tinted,0,image.getWidth());
		long size=(long)image.getWidth()*image.getHeight();
		if(size<=pixelLimit)
		{
			sheets.put(key,image);pixels+=size;
			Iterator<BufferedImage> entries=sheets.values().iterator();
			while(pixels>pixelLimit) { BufferedImage evicted=entries.next();pixels-=(long)evicted.getWidth()*evicted.getHeight();evicted.flush();entries.remove(); }
		}
		return image;
	}
	void paint(Graphics2D graphics,int width,int height,Color color,long tick)
	{
		BufferedImage image=sheet(color,width);int frame=(int)Math.floorMod(tick,FRAMES);
		int frameWidth=image.getWidth()/COLUMNS,frameHeight=image.getHeight()/(FRAMES/COLUMNS);
		int x=frame%COLUMNS*frameWidth,y=frame/COLUMNS*frameHeight;
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.drawImage(image,0,0,width,height,x,y,x+frameWidth,y+frameHeight,null);
	}
	void clear() { sheets.values().forEach(BufferedImage::flush);sheets.clear();pixels=0; }
	long pixels() { return pixels; }
	int size() { return sheets.size(); }
}
