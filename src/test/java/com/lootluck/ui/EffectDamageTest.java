package com.lootluck.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class EffectDamageTest
{
	@Test public void animationBatchesClippedDamageWithoutInvalidatingForegrounds() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			JPanel view=new JPanel(null);view.setSize(100,300);view.setPreferredSize(view.getSize());
			AtomicInteger foregrounds=new AtomicInteger();
			EffectButton first=new EffectButton(ItemEffect.RARITY_AURORA,Color.RED) {
				@Override protected void paintForeground(Graphics2D graphics) { foregrounds.incrementAndGet(); }
			};
			first.setBounds(5,5,42,42);view.add(first);
			EffectButton partial=new EffectButton(ItemEffect.RARITY_AURORA,Color.BLUE);partial.setBounds(53,65,42,42);view.add(partial);
			EffectButton hidden=new EffectButton(ItemEffect.RARITY_AURORA,Color.GREEN);hidden.setBounds(5,200,42,42);view.add(hidden);
			JViewport viewport=new JViewport();viewport.setSize(100,100);viewport.setView(view);
			RepaintManager previous=RepaintManager.currentManager(viewport);
			List<JComponent> targets=new ArrayList<>();List<Rectangle> damage=new ArrayList<>();
			RepaintManager.setCurrentManager(new RepaintManager() {
				@Override public void addDirtyRegion(JComponent component,int x,int y,int width,int height) { targets.add(component);damage.add(new Rectangle(x,y,width,height)); }
			});
			try
			{
				viewport.addNotify();viewport.doLayout();paint(first);assertEquals(1,foregrounds.get());
				targets.clear();damage.clear();EffectButton.animate();
				assertEquals(java.util.Collections.singletonList(viewport),targets);
				assertEquals(java.util.Collections.singletonList(new Rectangle(5,5,90,95)),damage);
				paint(first);assertEquals("Animation must preserve the foreground cache",1,foregrounds.get());
				viewport.removeNotify();targets.clear();EffectButton.animate();assertTrue(targets.isEmpty());
			}
			finally { if(viewport.isDisplayable()) { viewport.removeNotify(); }RepaintManager.setCurrentManager(previous); }
		});
	}
	private static void paint(EffectButton button)
	{
		BufferedImage image=new BufferedImage(button.getWidth(),button.getHeight(),BufferedImage.TYPE_INT_ARGB_PRE);Graphics2D graphics=image.createGraphics();
		try { button.paint(graphics); }finally { graphics.dispose(); }
	}
}
