package com.lootluck.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class EffectForegroundTest
{
	private static BufferedImage paint(EffectButton button)
	{
		BufferedImage image=new BufferedImage(button.getWidth(),button.getHeight(),BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D graphics=image.createGraphics();try { button.paint(graphics); }finally { graphics.dispose(); }return image;
	}
	@Test public void animationReusesForegroundAndTextChangesAndResizeRefreshIt() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			AtomicInteger paints=new AtomicInteger();EffectButton button=new EffectButton(ItemEffect.RARITY_AURORA,Color.RED);button.setLayout(null);button.setSize(80,44);
			JLabel label=new JLabel("First") { @Override protected void paintComponent(Graphics graphics) { paints.incrementAndGet();super.paintComponent(graphics); } };
			button.add(label);label.setBounds(2,2,76,40);paint(button);paint(button);assertEquals(1,paints.get());
			label.setText("Second");paint(button);assertEquals(2,paints.get());
			button.setSize(100,44);label.setBounds(2,2,96,40);paint(button);assertEquals(3,paints.get());
		});
	}
	@Test public void lateSpriteLoadRefreshesCachedForeground() throws Exception
	{
		AtomicReference<EffectButton> buttonRef=new AtomicReference<>();
		SwingUtilities.invokeAndWait(()->{
			AsyncBufferedImage sprite=new AsyncBufferedImage(null,16,16,BufferedImage.TYPE_INT_ARGB);
			EffectButton button=new EffectButton(ItemEffect.RARITY_AURORA,Color.BLUE);button.setLayout(null);button.setSize(42,42);
			JLabel label=new JLabel(new AsyncItemIcon(sprite));button.add(label);label.setBounds(2,2,38,38);paint(button);buttonRef.set(button);
			java.awt.Graphics2D graphics=sprite.createGraphics();graphics.setColor(Color.RED);graphics.fillRect(0,0,16,16);graphics.dispose();sprite.loaded();
		});
		SwingUtilities.invokeAndWait(()->assertEquals(Color.RED.getRGB(),paint(buttonRef.get()).getRGB(21,21)));
	}
}
