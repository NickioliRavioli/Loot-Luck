package com.lootluck.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemInformationPopupTest
{
	@Test public void wikiTextIsEscapedAndHiddenStatisticsStayHidden()
	{
		PanelState.Item item=new PanelState.Item();item.name="Item <test> & name";item.reason="HIDDEN_STATISTICS";
		item.rateTooltip="Published rate: 1/128\nConditions: <img src='https://example.test/image'> & literal text";
		PanelState.RateOption option=new PanelState.RateOption();option.tooltip=item.rateTooltip;option.color=Color.GREEN;item.rateOptions.add(option);
		String html=ItemInformationPopup.html(item);
		assertTrue(html.contains("Item &lt;test&gt; &amp; name"));assertFalse(html.contains("<img"));assertTrue(html.contains("&lt;img"));assertTrue(html.contains("#00ff00"));
		assertFalse(html.contains("HIDDEN_STATISTICS"));
	}
	@Test public void htmlPopoverScrollsLongHistoryWithoutAnEditor() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState.Item item=new PanelState.Item();item.name="Earth rune";
			item.rateTooltip="Current Wiki data\nPublished rate: 1/32\nQuantity: 36 · Rolls: 1\nTable: Dark wizard#High level\n\nRecorded history\n"+"Published rate: 1/32\nConditions: Only high-level Dark wizards\n\n".repeat(12);
			PanelState.RateOption option=new PanelState.RateOption();option.tooltip="Published rate: 1/32";option.color=new Color(109,181,232);item.rateOptions.add(option);
			ItemInformationPopup popup=new ItemInformationPopup(item);popup.setSize(popup.getPreferredSize());layout(popup);
			javax.swing.JScrollPane scroll=(javax.swing.JScrollPane)popup.getComponent(0);
			assertTrue(scroll.getVerticalScrollBar().isVisible());assertTrue(scroll.getViewport().getView() instanceof javax.swing.JLabel);
			assertTrue("Content width "+scroll.getViewport().getView().getPreferredSize().width+", viewport "+scroll.getViewport().getWidth(),scroll.getViewport().getView().getPreferredSize().width<=scroll.getViewport().getWidth());
			assertTrue(popup.getHeight()<=310);
			BufferedImage image=new BufferedImage(popup.getWidth(),popup.getHeight(),BufferedImage.TYPE_INT_ARGB);popup.paint(image.getGraphics());
			try { javax.imageio.ImageIO.write(image,"png",new java.io.File("build/item-information-popup.png")); } catch(Exception e) { throw new IllegalStateException(e); }
		});
	}
	private static void layout(Container parent)
	{
		parent.doLayout();for(Component child:parent.getComponents()) { if(child instanceof Container) { layout((Container)child); } }
	}
}
