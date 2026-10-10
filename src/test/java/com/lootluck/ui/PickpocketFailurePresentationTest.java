package com.lootluck.ui;

import com.lootluck.model.*;
import com.lootluck.rates.*;
import com.lootluck.tracking.LedgerEngine;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PickpocketFailurePresentationTest
{
	@Test public void failureRowsShowOnlyOccurrencesAcrossListGridAndCountStyles() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			LedgerEngine engine=new LedgerEngine(new Ledger());SourceIdentity source=new SourceIdentity("PICKPOCKET","Master Farmer","outcome=failed","[MEMBERS]");
			for(int i=1;i<=7;i++) { engine.failPickpocket(source,i); }
			RateProvider rates=new RateProvider() { public java.util.List<RateRow> rows(SourceIdentity s) { return java.util.List.of(); }public void refresh(SourceIdentity s,boolean force) {} };
			BufferedImage sheet=new BufferedImage(250,180,BufferedImage.TYPE_INT_RGB);Graphics2D canvas=sheet.createGraphics();canvas.setColor(new Color(34,34,34));canvas.fillRect(0,0,250,180);int y=0;
			for(boolean grid:new boolean[]{false,true}) { for(ItemDisplayStyle style:ItemDisplayStyle.values())
			{
				PanelState state=ViewBuilder.build(engine.lifetime(),rates,false,Map.of());state.gridView=grid;state.iconQuantities=style.iconQuantities;state.stackCounts=style.stackCounts;
				LootLuckPanel panel=new LootLuckPanel(id->{throw new AssertionError("Failure must not request a game item icon");},value->{},value->{},()->{},()->{});panel.update(state);
				JViewport viewport=new JViewport();viewport.setSize(230,450);viewport.setView(panel);viewport.addNotify();viewport.doLayout();layout(panel);
				JButton button=find(panel);assertNotNull(button);
				assertFalse(button.getAccessibleContext().getAccessibleName().contains("quantity"));
				if(!grid) { assertTrue(labels(button).contains("x7"));assertFalse(labels(button).contains("0")); }
				BufferedImage image=new BufferedImage(button.getWidth(),button.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D paint=image.createGraphics();button.paint(paint);paint.dispose();
				if(style==ItemDisplayStyle.values()[0]) { canvas.drawImage(image,8,y,null);y+=70; }
				button.doClick();layout(panel);java.util.List<String> expanded=labels(panel);assertTrue(expanded.contains("Occurrences"));assertFalse(expanded.contains("Quantity"));assertFalse(expanded.contains("GE total"));assertFalse(expanded.contains("HA total"));assertFalse(expanded.contains("Unknown table"));viewport.removeNotify();
			} }
			canvas.dispose();try { javax.imageio.ImageIO.write(sheet,"png",new java.io.File("build/pickpocket-failure-preview.png")); }catch(java.io.IOException ex) { throw new IllegalStateException(ex); }
		});
	}
	private static void layout(Container parent) { parent.doLayout();for(Component child:parent.getComponents()) { if(child instanceof Container) { layout((Container)child); } } }
	private static JButton find(Container parent) { for(Component child:parent.getComponents()) { if(child instanceof JButton&&((JButton)child).getAccessibleContext().getAccessibleName().startsWith("Failed pickpocket")) { return (JButton)child; }if(child instanceof Container) { JButton found=find((Container)child);if(found!=null) { return found; } } }return null; }
	private static java.util.List<String> labels(Container parent) { java.util.List<String> found=new ArrayList<>();for(Component child:parent.getComponents()) { if(child instanceof JLabel) { found.add(((JLabel)child).getText()); }if(child instanceof Container) { found.addAll(labels((Container)child)); } }return found; }
}
