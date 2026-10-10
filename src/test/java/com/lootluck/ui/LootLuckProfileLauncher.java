package com.lootluck.ui;

import com.lootluck.LootLuckPlugin;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.VolatileImage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Development-only instrumentation; records geometry and timing, never item/account data. */
public final class LootLuckProfileLauncher
{
	public static void main(String[] args) throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			EffectButton.observer=new PaintObserver();
			RepaintManager previous=RepaintManager.currentManager((JComponent)null);
			if(previous.getClass()==RepaintManager.class)
			{
				ProfileRepaintManager manager=new ProfileRepaintManager();
				manager.setDoubleBufferingEnabled(previous.isDoubleBufferingEnabled());manager.setDoubleBufferMaximumSize(previous.getDoubleBufferMaximumSize());RepaintManager.setCurrentManager(manager);
			}
		});
		ExternalPluginManager.loadBuiltin(LootLuckPlugin.class);RuneLite.main(args);
	}
	@Name("lootluck.Paint") @Label("Loot Luck item paint") @Category("Loot Luck") @StackTrace(false)
	static final class PaintEvent extends Event
	{
		String stage,effect,graphics,configuration;
		int width,height,clipWidth,clipHeight;
		double scaleX,scaleY;
		boolean foregroundPresent;
	}
	@Name("lootluck.AnimationClock") @Label("Loot Luck animation requests") @Category("Loot Luck") @StackTrace(false)
	static final class ClockEvent extends Event { int registered,visible,regions;long requestedPixels,repaintPixels; }
	@Name("lootluck.RepaintRequest") @Label("Loot Luck repaint request") @Category("Loot Luck") @StackTrace(true)
	static final class RepaintEvent extends Event { int width,height;boolean showing; }
	@Name("lootluck.SwingPaintPass") @Label("Swing dirty region paint pass") @Category("Loot Luck") @StackTrace(false)
	static final class PassEvent extends Event { int effectRequests;long requestedPixels; }
	@Name("lootluck.SwingBuffer") @Label("Swing volatile buffer") @Category("Loot Luck") @StackTrace(false)
	static final class BufferEvent extends Event { int proposedWidth,proposedHeight,width,height;boolean accelerated; }
	static final class PaintObserver implements EffectButton.PaintObserver
	{
		@Override public Runnable paint(Graphics2D graphics,String stage,String effect,int width,int height,boolean foregroundPresent)
		{
			PaintEvent event=new PaintEvent();if(!event.isEnabled()) { return null; }
			event.stage=stage;event.effect=effect;event.width=width;event.height=height;event.foregroundPresent=foregroundPresent;event.graphics=graphics.getClass().getName();
			event.configuration=graphics.getDeviceConfiguration()==null?"none":graphics.getDeviceConfiguration().getClass().getName();
			Rectangle clip=graphics.getClipBounds();if(clip!=null) { event.clipWidth=clip.width;event.clipHeight=clip.height; }
			AffineTransform transform=graphics.getTransform();event.scaleX=Math.hypot(transform.getScaleX(),transform.getShearY());event.scaleY=Math.hypot(transform.getScaleY(),transform.getShearX());
			event.begin();return ()->{event.end();event.commit();};
		}
		@Override public void clock(int registered,int visible,long requestedPixels,int regions,long repaintPixels)
		{
			ClockEvent event=new ClockEvent();if(!event.isEnabled()) { return; }event.registered=registered;event.visible=visible;event.requestedPixels=requestedPixels;event.regions=regions;event.repaintPixels=repaintPixels;event.commit();
		}
	}
	static final class ProfileRepaintManager extends RepaintManager
	{
		private final AtomicInteger effectRequests=new AtomicInteger();
		private final AtomicLong requestedPixels=new AtomicLong();
		@Override public Image getVolatileOffscreenBuffer(Component component,int width,int height)
		{
			Image image=super.getVolatileOffscreenBuffer(component,width,height);
			BufferEvent event=new BufferEvent();
			if(event.isEnabled())
			{
				event.proposedWidth=width;event.proposedHeight=height;
				if(image!=null) { event.width=image.getWidth(null);event.height=image.getHeight(null);event.accelerated=image instanceof VolatileImage&&((VolatileImage)image).getCapabilities().isAccelerated(); }
				event.commit();
			}
			return image;
		}
		@Override public void addDirtyRegion(JComponent component,int x,int y,int width,int height)
		{
			if(component instanceof EffectButton&&width>0&&height>0)
			{
				effectRequests.incrementAndGet();requestedPixels.addAndGet((long)width*height);
				RepaintEvent event=new RepaintEvent();if(event.isEnabled()) { event.width=width;event.height=height;event.showing=component.isShowing();event.commit(); }
			}
			super.addDirtyRegion(component,x,y,width,height);
		}
		@Override public void paintDirtyRegions()
		{
			PassEvent event=new PassEvent();event.effectRequests=effectRequests.getAndSet(0);event.requestedPixels=requestedPixels.getAndSet(0);event.begin();
			try { super.paintDirtyRegions(); }finally { event.end();event.commit(); }
		}
	}
}
