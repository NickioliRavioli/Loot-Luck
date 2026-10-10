package com.lootluck.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class EffectLifecycleTest
{
	@Test public void gridListGridOnlyAnimatesVisibleCurrentButtons() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();state.gridView=true;state.itemEffect=ItemEffect.RARITY_AURORA;state.notice="";
			PanelState.Source source=new PanelState.Source();source.key="source";source.name="Source";
			for(int index=0;index<200;index++) { PanelState.Item item=new PanelState.Item();item.key="item:"+index;item.name="Item "+index;item.quantity=1;item.rate="1/128";source.items.add(item); }
			state.sources.add(source);LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});panel.update(state);
			JViewport viewport=new JViewport();viewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);viewport.setSize(230,400);viewport.setView(panel);
			RepaintManager previous=RepaintManager.currentManager(panel);
			Set<JComponent> repainted=Collections.newSetFromMap(new IdentityHashMap<>());
			RepaintManager.setCurrentManager(new RepaintManager() {
				@Override public void addDirtyRegion(JComponent component,int x,int y,int width,int height) { if(component instanceof EffectButton) { repainted.add(component); } }
			});
			try
			{
				viewport.addNotify();Set<JComponent> retired=Collections.newSetFromMap(new IdentityHashMap<>());
				for(int pass=0;pass<4;pass++)
				{
					viewport.doLayout();layout(panel);repainted.clear();EffectButton.animate();
					assertFalse(repainted.isEmpty());assertTrue(repainted.size()<60);
					for(JComponent button:repainted) { assertTrue(button.isShowing());assertFalse(button.getVisibleRect().isEmpty());assertFalse("Detached buttons must stop animating",retired.contains(button));assertTrue(SwingUtilities.isDescendingFrom(button,panel)); }
					retired.addAll(repainted);
					findButton(panel,state.gridView?"Switch to list view":"Switch to grid view").doClick();
				}
				viewport.setViewPosition(new Point(0,1500));viewport.doLayout();layout(panel);repainted.clear();EffectButton.animate();
				assertFalse(repainted.isEmpty());for(JComponent button:repainted) { assertFalse(button.getVisibleRect().isEmpty()); }
				viewport.removeNotify();repainted.clear();EffectButton.animate();assertTrue(repainted.isEmpty());
			}
			finally { if(viewport.isDisplayable()) { viewport.removeNotify(); }RepaintManager.setCurrentManager(previous); }
		});
	}
	private static JButton findButton(Container parent,String name)
	{
		for(Component child:parent.getComponents())
		{
			if(child instanceof JButton&&name.equals(((JButton)child).getAccessibleContext().getAccessibleName())) { return (JButton)child; }
			if(child instanceof Container) { JButton found=findButton((Container)child,name);if(found!=null) { return found; } }
		}
		return null;
	}
	private static void layout(Container parent) { parent.doLayout();for(Component child:parent.getComponents()) { if(child instanceof Container) { layout((Container)child); } } }
}
