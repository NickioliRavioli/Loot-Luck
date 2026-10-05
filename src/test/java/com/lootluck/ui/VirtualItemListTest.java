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
