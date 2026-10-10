package com.lootluck.ui;

import java.awt.Component;
import java.awt.Graphics;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import net.runelite.client.util.AsyncBufferedImage;

/** One load callback per sprite wrapper; repaint only components that used it. */
public final class AsyncItemIcon extends ImageIcon
{
	private final Set<Component> targets=Collections.newSetFromMap(new WeakHashMap<>());
	private boolean loaded;
	public AsyncItemIcon(AsyncBufferedImage image)
	{
		super(image);
		image.onLoaded(()->SwingUtilities.invokeLater(()->{
			loaded=true;
			for(Component target:targets)
			{
				Component button=SwingUtilities.getAncestorOfClass(EffectButton.class,target);
				if(button!=null) { button.repaint(); }else { target.repaint(); }
			}
			targets.clear();
		}));
	}
	@Override public void paintIcon(Component component,Graphics graphics,int x,int y)
	{
		if(!loaded&&component!=null) { targets.add(component); }super.paintIcon(component,graphics,x,y);
	}
}
