package com.lootluck.ui;

import java.awt.Point;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class VirtualItemListTest
{
	@Test public void gridCardsReserveEveryRowAfterSidebarResize() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();state.gridView=true;state.itemEffect=ItemEffect.NONE;
			for(int sourceIndex=0;sourceIndex<2;sourceIndex++)
			{
				PanelState.Source source=new PanelState.Source();source.key="source-"+sourceIndex;source.name="Source "+sourceIndex;
				for(int index=0;index<33;index++) { PanelState.Item item=new PanelState.Item();item.key=source.key+":"+index;item.name="Item "+index;item.quantity=1;item.occurrences=1;source.items.add(item); }
				state.sources.add(source);
			}
			LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});panel.update(state);
			JViewport viewport=new JViewport();viewport.setView(panel);viewport.addNotify();
			for(int width:new int[]{230,300,260,330})
			{
				viewport.setSize(width,900);
				for(int pass=0;pass<4;pass++) { viewport.doLayout();layout(panel); }
				checkGridBounds(panel);
			}
			java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(panel.getWidth(),panel.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics2D graphics=image.createGraphics();panel.paint(graphics);graphics.dispose();
			try { javax.imageio.ImageIO.write(image,"png",new java.io.File("build/grid-row-height-preview.png")); }catch(java.io.IOException ex) { throw new IllegalStateException(ex); }
			viewport.removeNotify();
		});
	}
	private static void layout(java.awt.Container parent)
	{
		parent.doLayout();for(java.awt.Component child:parent.getComponents()) { if(child instanceof java.awt.Container) { layout((java.awt.Container)child); } }
	}
	private static void checkGridBounds(java.awt.Container parent)
	{
		for(java.awt.Component child:parent.getComponents())
		{
			if(child instanceof VirtualItemList) { assertTrue("Grid height "+child.getHeight()+" must fit rows "+child.getPreferredSize().height,child.getHeight()>=child.getPreferredSize().height); }
			else if(child instanceof java.awt.Container) { checkGridBounds((java.awt.Container)child); }
		}
	}
	@Test public void largeListMountsOnlyViewportRowsAndReleasesOldRows() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			AtomicInteger created=new AtomicInteger(),released=new AtomicInteger();
			VirtualItemList list=new VirtualItemList(Collections.nCopies(10000,44),index->{created.incrementAndGet();JPanel row=new JPanel();row.setName("row-"+index);return row;},index->released.incrementAndGet());
			JViewport viewport=new JViewport();viewport.setSize(230,400);viewport.setView(list);viewport.addNotify();viewport.doLayout();list.doLayout();
			assertEquals(440000,list.getPreferredSize().height);assertTrue(list.getComponentCount()<20);assertTrue(created.get()<20);
			viewport.setViewPosition(new Point(0,220000));list.doLayout();assertTrue(released.get()>0);assertTrue(list.getComponentCount()<20);assertTrue(created.get()<40);
			assertTrue(java.util.Arrays.stream(list.getComponents()).anyMatch(row->"row-5000".equals(row.getName())));
			viewport.setViewPosition(new Point(0,439600));list.doLayout();assertTrue(java.util.Arrays.stream(list.getComponents()).anyMatch(row->"row-9999".equals(row.getName())));
			viewport.removeNotify();
		});
	}
}
