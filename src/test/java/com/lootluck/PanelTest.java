package com.lootluck;

import com.lootluck.ui.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

public class PanelTest
{
	@Test public void updatesKeepUnchangedCardsMountedAndRefreshChangedCountsAndEffects() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();state.notice="";
			for(String name:java.util.List.of("Cow","Dragon"))
			{
				PanelState.Source source=new PanelState.Source();source.key=name;source.name=name;
				PanelState.Item item=new PanelState.Item();item.key=name+":coins";item.name=name+" coins";item.rate="Always";item.quantity=10;item.occurrences=1;source.items.add(item);state.sources.add(source);
			}
			LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});panel.update(state);
			JButton cow=findButton(panel,"Cow coins"),dragon=findButton(panel,"Dragon coins");
			state.dataStatus="Refreshed status";panel.update(state);assertSame(cow,findButton(panel,"Cow coins"));assertSame(dragon,findButton(panel,"Dragon coins"));
			state.sources.get(0).items.get(0).quantity=20;panel.update(state);assertNotSame(cow,findButton(panel,"Cow coins"));assertSame(dragon,findButton(panel,"Dragon coins"));
			state.itemEffect=com.lootluck.ui.ItemEffect.RARITY_AURORA;panel.update(state);assertNotSame(dragon,findButton(panel,"Dragon coins"));
			findButton(panel,"Cow coins").doClick();assertNotNull(findLabel(panel,"Quantity"));
		});
	}
	@Test public void recentCollectionRowsMatchSourceRowColoursAndHeight() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();state.itemEffect=ItemEffect.NONE;PanelState.Source source=new PanelState.Source();source.key="source";source.name="Cow";
			PanelState.Item item=new PanelState.Item();item.key="source:coins";item.name="Coins";item.quantity=12500;item.occurrences=1;item.rate="Always";source.items.add(item);state.sources.add(source);
			PanelState.Source record=new PanelState.Source();record.key="collection:0";record.name="Cow";record.items.add(item);state.collections.add(record);
			LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});panel.update(state);layout(panel,230);JButton sourceRow=findButton(panel,"Coins");
			findViewSelector(panel).setSelectedItem("Recent collections");layout(panel,230);JButton recordRow=findButton(panel,"Coins");
			assertEquals(sourceRow.getBackground(),recordRow.getBackground());assertEquals(sourceRow.getPreferredSize().height,recordRow.getPreferredSize().height);assertEquals(sourceRow.getHeight(),recordRow.getHeight());assertEquals(sourceRow.getBorder().getBorderInsets(sourceRow),recordRow.getBorder().getBorderInsets(recordRow));
			assertNull(findLabel(recordRow,"Always"));assertEquals(sourceRow.getParent().getPreferredSize().height,recordRow.getParent().getPreferredSize().height);
		});
	}
	@Test public void recentCollectionCardsSupportHoverGridSecondsAndPaging() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();state.notice="";state.itemEffect=ItemEffect.NONE;long time=java.time.Instant.parse("2026-10-06T03:04:05Z").toEpochMilli();
			for(int n=0;n<3;n++)
			{
				PanelState.Source record=new PanelState.Source();record.key="collection:"+n;record.name=n==2?"Empty notification":"Cow";record.lastReceived=time-n*1000;
				if(n<2) { PanelState.Item item=new PanelState.Item();item.key=record.key+":coins";item.name="Coins";item.id=net.runelite.api.gameval.ItemID.COINS;item.quantity=12500;item.occurrences=1;record.items.add(item); }
				state.collections.add(record);
			}
			java.util.List<String> opened=new java.util.ArrayList<>();LootLuckPanel panel=new LootLuckPanel(id->new javax.swing.ImageIcon(new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB)),value->{},value->{},()->{},()->{},opened::add);panel.update(state);findViewSelector(panel).setSelectedItem("Recent collections");
			javax.swing.JViewport viewport=new javax.swing.JViewport();viewport.setSize(230,700);viewport.setView(panel);viewport.addNotify();viewport.doLayout();layoutChildren(panel);
			assertEquals(2,countLabels(panel,"▾ Cow"));assertNull(findLabel(panel,"1 item"));assertNull(findLabel(panel,"0 items"));assertNotNull(findLabel(panel,"No items reported"));
			java.time.format.DateTimeFormatter format=java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss").withZone(java.time.ZoneId.systemDefault());assertNotNull(findLabel(panel,format.format(java.time.Instant.ofEpochMilli(time))));assertNotNull(findLabel(panel,format.format(java.time.Instant.ofEpochMilli(time-1000))));
			JButton row=findButton(panel,"Coins");for(Component text:new Component[]{findLabel(row,"Coins"),findLabel(row,"x1"),findIcon(row)})
			{
				assertNotNull(text);text.dispatchEvent(new java.awt.event.MouseEvent(text,java.awt.event.MouseEvent.MOUSE_ENTERED,0,0,1,1,0,0,0,false,java.awt.event.MouseEvent.NOBUTTON));assertTrue(row.getModel().isRollover());java.awt.Point outside=SwingUtilities.convertPoint(row,new java.awt.Point(-1,-1),text);text.dispatchEvent(new java.awt.event.MouseEvent(text,java.awt.event.MouseEvent.MOUSE_EXITED,0,0,outside.x,outside.y,0,0,0,false,java.awt.event.MouseEvent.NOBUTTON));assertFalse(row.getModel().isRollover());
			}
			clickLabel(panel,"Coins");assertNotNull(findLabel(panel,"12,500"));assertNull(findLabel(panel,"Rate unknown"));assertWikiMenuTree(findButton(panel,"Coins"));clickLabel(panel,"Coins");assertNull(findLabel(panel,"Quantity"));viewport.doLayout();layoutChildren(panel);saveCollectionPreview(panel,"build/recent-collections-list.png");
			JButton gridToggle=findButton(panel,"Switch to grid view");assertTrue(gridToggle.isEnabled());gridToggle.doClick();viewport.doLayout();layoutChildren(panel);JButton icon=findButton(panel,"Coins");assertNotNull(icon);assertEquals(icon.getWidth(),icon.getHeight());assertEquals(2,countLabels(panel,"▾ Cow"));assertNotNull(findLabel(panel,"No items reported"));assertWikiMenuTree(icon);openWikiMenu(icon);assertEquals(1,opened.size());saveCollectionPreview(panel,"build/recent-collections-grid.png");
			icon.doClick();viewport.doLayout();layoutChildren(panel);assertNotNull(findLabel(panel,"12,500"));assertNull(findLabel(panel,"Rate unknown"));assertNull(findLabel(panel,"Source breakdown"));assertNull(findLabel(panel,"Occurrences"));findButton(panel,"Switch to list view").doClick();assertNotNull(findLabel(panel,"12,500"));
			clickLabel(panel,format.format(java.time.Instant.ofEpochMilli(time)));assertEquals(1,countLabels(panel,"Coins"));assertNotNull(findLabel(panel,"▸ Cow"));clickLabel(panel,format.format(java.time.Instant.ofEpochMilli(time)));assertEquals(2,countLabels(panel,"Coins"));
			for(int n=3;n<51;n++) { PanelState.Source record=new PanelState.Source();record.key="collection:"+n;record.name="Record "+n;record.lastReceived=time-n*1000;state.collections.add(record); }panel.update(state);assertNotNull(findLabel(panel,"Page 1 / 2"));assertNull(findLabel(panel,"▾ Record 50"));findButton(panel,"›").doClick();assertNotNull(findLabel(panel,"Page 2 / 2"));assertNotNull(findLabel(panel,"▾ Record 50"));assertNotNull(findLabel(panel,"No items reported"));assertNull(findLabel(panel,"Cow"));viewport.removeNotify();
		});
	}
	private static void saveCollectionPreview(LootLuckPanel panel,String path)
	{
		BufferedImage image=new BufferedImage(230,Math.min(700,panel.getHeight()),BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D graphics=image.createGraphics();try { panel.paint(graphics);ImageIO.write(image,"png",new java.io.File(path)); }catch(Exception error) { throw new IllegalStateException(error); }finally { graphics.dispose(); }
	}
	@Test public void segmentedScopeSitsAboveViewsAndKeepsItsSelection() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			java.util.List<Boolean> changes=new java.util.ArrayList<>();LootLuckPanel panel=new LootLuckPanel(id->null,changes::add,value->{},()->{},()->{});panel.update(new PanelState());layout(panel,230);
			javax.swing.JToggleButton lifetime=findScopeButton(panel,"Lifetime"),session=findScopeButton(panel,"Session");JComboBox<?> views=findViewSelector(panel);
			assertNotNull(lifetime);assertNotNull(session);assertTrue(lifetime.isSelected());assertFalse(session.isSelected());assertFalse(panel.session());assertEquals(lifetime.getWidth(),session.getWidth());assertEquals(lifetime.getY(),session.getY());
			assertTrue(SwingUtilities.convertPoint(lifetime,0,lifetime.getHeight(),panel).y<=SwingUtilities.convertPoint(views,0,0,panel).y);
			session.doClick();assertTrue(panel.session());assertTrue(session.isSelected());assertFalse(lifetime.isSelected());assertEquals(java.util.List.of(true),changes);session.doClick();assertEquals(java.util.List.of(true),changes);
			for(String view:java.util.List.of("Recent collections","All items","By source")) { views.setSelectedItem(view);layout(panel,230);assertTrue(panel.session());assertTrue(session.isSelected());assertTrue(SwingUtilities.convertPoint(session,0,session.getHeight(),panel).y<=SwingUtilities.convertPoint(views,0,0,panel).y); }
			assertEquals(java.util.List.of(true),changes);lifetime.doClick();assertFalse(panel.session());assertTrue(lifetime.isSelected());assertFalse(session.isSelected());assertEquals(java.util.List.of(true,false),changes);
		});
	}
	private static javax.swing.JToggleButton findScopeButton(Container container,String text)
	{
		for(Component child:container.getComponents()) { if(child instanceof javax.swing.JToggleButton&&text.equals(((javax.swing.JToggleButton)child).getText())) { return (javax.swing.JToggleButton)child; }if(child instanceof Container) { javax.swing.JToggleButton found=findScopeButton((Container)child,text);if(found!=null) { return found; } } }return null;
	}
	@Test public void sourceHeaderTextForwardsHoverAndLeftClicksButNotRightClicks() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();state.notice="";PanelState.Source source=new PanelState.Source();source.key="cow";source.name="Cow";source.count=12;source.value="1K gp";source.wikiUrl="https://oldschool.runescape.wiki/w/Cow";
			PanelState.Item item=new PanelState.Item();item.key="coins";item.name="Coins";item.rate="Always";source.items.add(item);state.sources.add(source);
			java.util.List<String> opened=new java.util.ArrayList<>();LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{},opened::add);panel.update(state);layout(panel,230);
			for(boolean valueText:new boolean[]{false,true})
			{
				JButton header=findButton(panel,"Cow");Component text=findLabel(header,valueText?"1K gp":"▾ Cow");assertNotNull(text);
				text.dispatchEvent(new java.awt.event.MouseEvent(text,java.awt.event.MouseEvent.MOUSE_ENTERED,0,0,1,1,0,false));assertTrue(header.getModel().isRollover());
				java.awt.Point outside=SwingUtilities.convertPoint(header,new java.awt.Point(-1,-1),text);text.dispatchEvent(new java.awt.event.MouseEvent(text,java.awt.event.MouseEvent.MOUSE_EXITED,0,0,outside.x,outside.y,0,false));assertFalse(header.getModel().isRollover());
				text.dispatchEvent(new java.awt.event.MouseEvent(text,java.awt.event.MouseEvent.MOUSE_CLICKED,0,0,1,1,1,false,java.awt.event.MouseEvent.BUTTON3));assertNotNull(findButton(panel,"Coins"));assertTrue(opened.isEmpty());
				clickLabel(panel,valueText?"1K gp":"▾ Cow");assertNull(findButton(panel,"Coins"));clickLabel(panel,valueText?"1K gp":"▸ Cow");assertNotNull(findButton(panel,"Coins"));layout(panel,230);
			}
			openWikiMenu(findLabel(panel,"▾ Cow"));assertEquals(java.util.List.of(source.wikiUrl),opened);assertNotNull(findButton(panel,"Coins"));
		});
	}
	@Test public void wikiContextMenusCoverHeadersItemsAndObservationViews() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();state.notice="";state.showWikiLinks=false;
			PanelState.Source source=new PanelState.Source();source.key="cow";source.name="Cow";source.wikiUrl="https://oldschool.runescape.wiki/w/Cow";source.value="1K gp";
			PanelState.Item item=new PanelState.Item();item.key="coins";item.id=net.runelite.api.gameval.ItemID.COINS;item.name="Coins";item.quantity=100;item.occurrences=1;item.rate="Always";
			source.items.add(item);state.sources.add(source);state.collections.add(source);state.allItems.add(item);
			java.util.List<String> opened=new java.util.ArrayList<>();LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{},opened::add);panel.update(state);
			JButton header=findButton(panel,"Cow"),row=findButton(panel,"Coins");
			assertWikiMenuTree(header);assertWikiMenuTree(row);openWikiMenu(findLabel(header,"▾ Cow"));
			assertEquals(source.wikiUrl,opened.get(0));assertNotNull(findButton(panel,"Coins"));
			openWikiMenu(findLabel(row,"Coins"));okhttp3.HttpUrl itemUrl=okhttp3.HttpUrl.get(opened.get(1));
			assertEquals("/w/Special:Lookup",itemUrl.encodedPath());assertEquals("item",itemUrl.queryParameter("type"));assertEquals(Integer.toString(item.id),itemUrl.queryParameter("id"));assertEquals("Coins",itemUrl.queryParameter("name"));assertNull(findLabel(panel,"Quantity"));
			row.doClick();assertNotNull(findLabel(panel,"Quantity"));header.doClick();assertNull(findButton(panel,"Coins"));findButton(panel,"Cow").doClick();assertNotNull(findButton(panel,"Coins"));
			javax.swing.JViewport viewport=new javax.swing.JViewport();viewport.setSize(230,700);viewport.setView(panel);viewport.addNotify();state.gridView=true;panel.update(state);viewport.doLayout();layoutChildren(panel);assertWikiMenuTree(findButton(panel,"Coins"));openWikiMenu(findButton(panel,"Coins"));assertEquals(opened.get(1),opened.get(2));
			JComboBox<?> views=findViewSelector(panel);views.setSelectedItem("Recent collections");viewport.doLayout();layoutChildren(panel);assertWikiMenuTree(findButton(panel,"Coins"));openWikiMenu(findLabel(panel,"▾ Cow"));assertEquals(source.wikiUrl,opened.get(3));
			views.setSelectedItem("All items");assertWikiMenuTree(findButton(panel,"Coins"));openWikiMenu(findButton(panel,"Coins"));assertEquals(opened.get(1),opened.get(4));
			item.id=0;item.name="Unknown & unusual item";panel.update(state);openWikiMenu(findButton(panel,item.name));okhttp3.HttpUrl fallback=okhttp3.HttpUrl.get(opened.get(5));assertNull(fallback.queryParameter("id"));assertEquals(item.name,fallback.queryParameter("name"));
			viewport.removeNotify();
		});
	}
	private static void openWikiMenu(Component component)
	{
		assertNotNull(component);javax.swing.JPopupMenu menu=((javax.swing.JComponent)component).getComponentPopupMenu();assertNotNull(menu);
		assertEquals(1,menu.getComponentCount());javax.swing.JMenuItem action=(javax.swing.JMenuItem)menu.getComponent(0);assertEquals("Open Wiki",action.getText());action.doClick();
	}
	private static void assertWikiMenuTree(Component component)
	{
		assertNotNull(component);assertNotNull(((javax.swing.JComponent)component).getComponentPopupMenu());
		if(component instanceof Container) { for(Component child:((Container)component).getComponents()) { assertSame(((javax.swing.JComponent)component).getComponentPopupMenu(),((javax.swing.JComponent)child).getComponentPopupMenu());assertWikiMenuTree(child); } }
	}
	@Test public void sourceHeadersReserveSummarySpaceAndBoundLongNames() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();state.notice="";state.showGe=true;state.showWikiLinks=true;
			for(String name:java.util.List.of("Cow","Example: large quantities","Example: a very long source label which must end with an ellipsis"))
			{
				PanelState.Source source=new PanelState.Source();source.key=name;source.name=name;source.count=1_234_567_890L;source.value=LootLuckPanel.money(4_702_500_000_000L);source.wikiUrl="https://oldschool.runescape.wiki/w/Ghost";state.sources.add(source);
			}
			LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});panel.update(state);layout(panel,230);
			for(PanelState.Source source:state.sources)
			{
				JButton header=findButton(panel,source.name);Component name=findLabel(header,"▾ "+source.name),value=findLabel(header,"4T gp");
				assertNotNull(name);assertNotNull(value);
				assertTrue(name.getWidth()>0);assertTrue(name.getHeight()>=name.getPreferredSize().height);
				assertEquals(name.getFontMetrics(name.getFont()).getHeight(),name.getPreferredSize().height);
				assertTrue(value.getWidth()>=value.getPreferredSize().width);
				assertTrue(SwingUtilities.convertPoint(value,0,0,header).x>=name.getX()+name.getWidth());
			}
			BufferedImage image=new BufferedImage(230,panel.getHeight(),BufferedImage.TYPE_INT_ARGB);panel.paint(image.getGraphics());
			try { ImageIO.write(image,"png",new java.io.File("build/source-header-values.png")); } catch(Exception e) { throw new IllegalStateException(e); }
		});
	}
	@Test public void expandedNumbersRemainExactAndFitAtSidebarWidth() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			for(boolean grid:new boolean[]{false,true})
			{
				PanelState state=new PanelState();state.notice="";state.gridView=grid;state.showGe=true;state.showHa=true;
				PanelState.Source source=new PanelState.Source();source.key="large";source.name="Large totals";
				PanelState.Item item=new PanelState.Item();item.key="coins";item.name="Coins";item.quantity=Long.MAX_VALUE;item.occurrences=Long.MAX_VALUE;item.unresolvedOccurrences=Long.MAX_VALUE;
				item.rate="1/128";item.valueAvailable=true;item.ge=2_149_631_130_647L;item.ha=Long.MAX_VALUE;
				PanelState.RateOption option=new PanelState.RateOption();option.table="Main";option.dropQuantity="1";option.rate="1/128";option.occurrences=Long.MAX_VALUE;item.rateOptions.add(option);
				source.items.add(item);state.sources.add(source);
				LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});panel.update(state);
				javax.swing.JViewport viewport=new javax.swing.JViewport();viewport.setSize(230,700);viewport.setView(panel);viewport.addNotify();viewport.doLayout();layoutChildren(panel);
				findButton(panel,"Coins").doClick();viewport.doLayout();layoutChildren(panel);
				Container body=findLabel(panel,"Quantity").getParent().getParent().getParent();
				for(String text:new String[]{"9,223,372,036,854,775,807","x9,223,372,036,854,775,807","2,149,631,130,647 gp","9,223,372,036,854,775,807 gp"})
				{
					assertNotNull(text,findLabel(body,text));assertNumericLabelFits(body,text);
				}
				Component geLabel=findLabel(body,"GE total"),geValue=findLabel(body,"2,149,631,130,647 gp");
				assertEquals(geLabel.getY(),geValue.getY());assertTrue(geValue.getX()>=geLabel.getX()+geLabel.getWidth());
				if(!grid)
				{
					BufferedImage image=new BufferedImage(230,panel.getHeight(),BufferedImage.TYPE_INT_ARGB);panel.paint(image.getGraphics());
					try { ImageIO.write(image,"png",new java.io.File("build/large-detail-values.png")); } catch(Exception e) { throw new IllegalStateException(e); }
				}
				viewport.removeNotify();
			}
		});
	}
	private static void assertNumericLabelFits(Container container,String text)
	{
		for(Component child:container.getComponents())
		{
			if(child instanceof javax.swing.JLabel&&text.equals(((javax.swing.JLabel)child).getText())) { assertTrue(text+" clipped: "+child.getWidth()+" < "+child.getPreferredSize().width,child.getWidth()>=child.getPreferredSize().width);assertTrue(child.getHeight()>=child.getPreferredSize().height); }
			if(child instanceof Container) { assertNumericLabelFits((Container)child,text); }
		}
	}
	@Test public void settingsBackHeaderStaysOutsideTheScrollingContent() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});
			java.util.List<String> actions=new java.util.ArrayList<>();panel.setDataAction(actions::add);
			PanelState state=new PanelState();state.canManageData=true;
			panel.setSettings(new LootLuckConfig(){},(key,value)->{},value->null);panel.update(state);findButton(panel,"Settings").doClick();
			javax.swing.JPanel wrapper=panel.getWrappedPanel();wrapper.setSize(242,400);layoutChildren(wrapper);
			JButton back=findButton(wrapper,"← Back to loot");assertNotNull(back);assertNull(SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class,back));
			int before=SwingUtilities.convertPoint(back,0,0,wrapper).y;
			javax.swing.JScrollPane scroll=(javax.swing.JScrollPane)wrapper.getComponent(0);scroll.getViewport().setViewPosition(new java.awt.Point(0,600));layoutChildren(wrapper);
			assertEquals(before,SwingUtilities.convertPoint(back,0,0,wrapper).y);assertTrue(scroll.getViewport().getViewPosition().y>0);
			assertNull(findButton(wrapper,"Data settings"));assertNotNull(findButton(wrapper,"Reset session"));
			for(String action:java.util.List.of("Export data","Import data","Delete data")) { findButton(wrapper,action).doClick(); }
			assertEquals(java.util.List.of("Export data","Import data","Delete data"),actions);
			java.awt.Color deleteColor=findButton(wrapper,"Delete data").getBackground();assertTrue(deleteColor.getRed()>deleteColor.getGreen());
			assertTrue(SwingUtilities.convertPoint(findLabel(wrapper,"Collection history limit"),0,0,wrapper).y>SwingUtilities.convertPoint(findLabel(wrapper,"Background effects"),0,0,wrapper).y);
			state.canManageData=false;panel.update(state);assertFalse(findButton(wrapper,"Reset session").isEnabled());assertFalse(findButton(wrapper,"Delete data").isEnabled());
			back.doClick();assertNull(findButton(wrapper,"← Back to loot"));assertNotNull(findButton(wrapper,"Settings"));
		});
	}
	@Test public void mainHeaderShowsItemsDirectlyWithoutConditionHeaders() throws Exception
	{
		javax.swing.SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();state.notice="";state.showGe=false;state.showWikiLinks=false;
			PanelState.Source family=new PanelState.Source();family.key="ghost";family.name="Ghost";family.count=80;
			PanelState.Item item=new PanelState.Item();item.key="bag";item.name="Looting bag";item.quantity=1;item.rate="1/7";item.rarity=java.awt.Color.GRAY;
			family.items.add(item);state.sources.add(family);
			LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});panel.update(state);
			assertNull(findButton(panel,"Wilderness"));
			assertNotNull(findLabel(panel,"Looting bag"));assertEquals(1,countLabels(panel,"Looting bag"));
			findButton(panel,"Ghost").doClick();assertNull(findLabel(panel,"Looting bag"));
			findButton(panel,"Ghost").doClick();assertEquals(1,countLabels(panel,"Looting bag"));
		});
	}
	@Test public void singleDropTableAppearsInExpandedDetails() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();PanelState.Source source=new PanelState.Source();source.key="test";source.name="Test";PanelState.Item item=new PanelState.Item();item.key="single";item.name="Single table item";item.rate="1/20";source.items.add(item);state.sources.add(source);
			PanelState.RateOption option=new PanelState.RateOption();option.table="Unique";option.rate="1/20";option.dropQuantity="1";option.color=java.awt.Color.GREEN;item.rateOptions.add(option);
			LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});panel.update(state);findButton(panel,"Single table item").doClick();assertNotNull(findLabel(panel,"Unique"));item.rateOptions.clear();panel.update(state);assertNotNull(findLabel(panel,"Unknown table"));
		});
	}
	@Test public void headerEyeToggleUpdatesPreferenceAndTracksState() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			java.util.List<Boolean> changes=new java.util.ArrayList<>();LootLuckPanel panel=new LootLuckPanel(id->null,value->{},changes::add,()->{},()->{});PanelState state=new PanelState();panel.update(state);
			JButton eye=findButton(panel,"Show unobtained items");assertFalse(eye.isSelected());assertEquals("Show unobtained items",eye.getToolTipText());eye.doClick();assertEquals(java.util.List.of(true),changes);assertTrue(eye.isSelected());eye.doClick();assertEquals(java.util.List.of(true,false),changes);assertFalse(eye.isSelected());state.includeUnobtained=true;panel.update(state);assertTrue(eye.isSelected());
		});
	}
	@Test public void unobtainedIconsKeepLiveColorAndTransparencyAcrossSelection() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			BufferedImage sprite=new BufferedImage(36,32,BufferedImage.TYPE_INT_ARGB);
			PanelState state=new PanelState();state.gridView=true;state.iconQuantities=false;state.hideUnobtainedOtherWorld=true;
			PanelState.Source source=new PanelState.Source();source.key="test";source.name="Test";
			PanelState.Item item=new PanelState.Item();item.id=1;item.key="unobtained";item.name="Unobtained";item.worldMarker="(m)";item.rate="1/128";item.icon=new javax.swing.ImageIcon(sprite);source.items.add(item);state.sources.add(source);
			LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});panel.update(state);javax.swing.JViewport viewport=new javax.swing.JViewport();viewport.setSize(230,400);viewport.setView(panel);viewport.addNotify();viewport.doLayout();layoutChildren(panel);
			javax.swing.JLabel before=findIcon(findButton(panel,"Unobtained"));assertTrue(before.isEnabled());JButton square=findButton(panel,"Unobtained");assertEquals(square.getWidth(),square.getHeight());assertEquals(square.getWidth(),before.getX()*2+before.getWidth());assertEquals(square.getHeight(),before.getY()*2+before.getHeight());
			BufferedImage empty=new BufferedImage(36,32,BufferedImage.TYPE_INT_ARGB);before.getIcon().paintIcon(before,empty.getGraphics(),0,0);assertEquals(0,empty.getRGB(5,5));
			// Simulate completion of an asynchronous sprite after its first paint.
			java.awt.Graphics2D graphics=sprite.createGraphics();graphics.setColor(java.awt.Color.ORANGE);graphics.fillRect(0,0,36,32);graphics.dispose();
			for(int mode=0;mode<2;mode++)
			{
				findButton(panel,"Unobtained").doClick();viewport.doLayout();layoutChildren(panel);
				if(mode==0) { assertNotNull(findLabel(panel,"Members worlds only")); }
				assertNull(findLabel(panel,"Unobtained (m)"));
				javax.swing.JLabel icon=findIcon(findButton(panel,"Unobtained"));assertTrue(icon.isEnabled());
				BufferedImage result=new BufferedImage(36,32,BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D target=result.createGraphics();icon.getIcon().paintIcon(icon,target,0,0);target.dispose();
				java.awt.Color color=new java.awt.Color(result.getRGB(5,5),true);assertEquals(255,color.getRed());assertTrue(color.getGreen()>190);assertEquals(0,color.getBlue());assertTrue(color.getAlpha()>=160&&color.getAlpha()<=170);
				assertNull(findLabel(findButton(panel,"Unobtained"),"0"));assertNull(findLabel(findButton(panel,"Unobtained"),"x0"));
				if(mode==0) { findButton(panel,"Switch to list view").doClick();viewport.doLayout();layoutChildren(panel); }
			}
			viewport.removeNotify();
		});
	}
	@Test public void virtualGridSelectsDetailsBelowIconsAndReturnsToList() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();state.notice="";state.iconQuantities=false;PanelState.Source source=new PanelState.Source();source.key="test";source.name="Grid source";
			for(int i=0;i<10000;i++) { PanelState.Item item=new PanelState.Item();item.id=i+1;item.key="grid:"+i;item.name=String.format(java.util.Locale.ROOT,"Reward %05d",i);item.rate="1/128";item.quantity=i==0?12:0;source.items.add(item); }
			state.sources.add(source);java.util.concurrent.atomic.AtomicInteger icons=new java.util.concurrent.atomic.AtomicInteger();LootLuckPanel panel=new LootLuckPanel(id->{icons.incrementAndGet();return null;},value->{},value->{},()->{},()->{});panel.update(state);
			javax.swing.JViewport viewport=new javax.swing.JViewport();viewport.setSize(230,400);viewport.setView(panel);viewport.addNotify();viewport.doLayout();layoutChildren(panel);icons.set(0);
			findButton(panel,"Switch to grid view").doClick();viewport.doLayout();layoutChildren(panel);assertTrue(icons.get()<100);assertNotNull(findButton(panel,"Reward 00000"));assertNull(findButton(panel,"Reward 09999"));assertNull(findLabel(panel,"Quantity"));
			JButton icon=findButton(panel,"Reward 00000");assertEquals(icon.getWidth(),icon.getHeight());JButton missing=findButton(panel,"Reward 00001");assertEquals(missing.getWidth(),missing.getHeight());assertEquals(icon.getWidth(),missing.getWidth());icon.doClick();viewport.doLayout();layoutChildren(panel);assertNotNull(findLabel(panel,"Quantity"));assertNotNull(findLabel(panel,"Reward 00000"));assertEquals(new java.awt.Color(223,192,113),((javax.swing.border.LineBorder)findButton(panel,"Reward 00000").getBorder()).getLineColor());assertNoTooltips(panel);
			JButton selected=findButton(panel,"Reward 00000"),nextRow=findButton(panel,"Reward 00005");assertNotNull(nextRow);
			int iconBottom=SwingUtilities.convertPoint(selected,0,selected.getHeight(),panel).y;
			Component quantity=findLabel(panel,"Quantity");int detailTop=SwingUtilities.convertPoint(quantity,0,0,panel).y;
			assertTrue(detailTop>iconBottom);assertTrue(detailTop<SwingUtilities.convertPoint(nextRow,0,0,panel).y);
			assertEquals(selected.getHeight(),selected.getParent().getHeight());
			findButton(panel,"Reward 00005").doClick();viewport.doLayout();layoutChildren(panel);assertNotNull(findLabel(panel,"Reward 00005"));assertNull(findLabel(panel,"Reward 00000"));
			selected=findButton(panel,"Reward 00005");quantity=findLabel(panel,"Quantity");assertTrue(SwingUtilities.convertPoint(quantity,0,0,panel).y>SwingUtilities.convertPoint(selected,0,selected.getHeight(),panel).y);
			findButton(panel,"Reward 00000").doClick();viewport.doLayout();layoutChildren(panel);
			findButton(panel,"Reward 00000").doClick();viewport.doLayout();layoutChildren(panel);assertNull(findLabel(panel,"Quantity"));findButton(panel,"Switch to list view").doClick();viewport.doLayout();layoutChildren(panel);assertNotNull(findLabel(panel,"1/128"));viewport.removeNotify();
		});
	}
	@Test public void largeRewardSourcesLoadVisibleIconsAndToggleAllHeaders() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();state.notice="";java.util.concurrent.atomic.AtomicInteger icons=new java.util.concurrent.atomic.AtomicInteger();
			PanelState.Source source=new PanelState.Source();source.key="casket";source.name="Reward casket (easy)";
			for(int i=0;i<10000;i++) { PanelState.Item item=new PanelState.Item();item.id=i+1;item.key="item:"+i;item.name=String.format(java.util.Locale.ROOT,"Item %05d",i);item.rate="1/128";source.items.add(item); }
			state.sources.add(source);LootLuckPanel panel=new LootLuckPanel(id->{icons.incrementAndGet();return null;},value->{},value->{},()->{},()->{});panel.update(state);javax.swing.JViewport viewport=new javax.swing.JViewport();viewport.setSize(230,400);viewport.setView(panel);viewport.addNotify();viewport.doLayout();layoutChildren(panel);
			assertTrue(icons.get()<30);assertNotNull(findButton(panel,"Item 00000"));assertNull(findButton(panel,"Item 09999"));
			findButton(panel,"Item 00000").doClick();viewport.doLayout();layoutChildren(panel);assertNotNull(findLabel(panel,"Quantity"));
			findButton(panel,"Collapse all sources").doClick();assertNull(findButton(panel,"Item 00000"));
			findButton(panel,"Expand all sources").doClick();viewport.doLayout();layoutChildren(panel);assertNotNull(findButton(panel,"Item 00000"));assertNotNull(findLabel(panel,"Quantity"));viewport.removeNotify();
		});
	}
	@Test public void observationViewsOmitRatesAndKeepRepeatedRewardsSeparate() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();state.notice="";PanelState.Item item=new PanelState.Item();item.id=1;item.key="all:1";item.name="Bones";item.quantity=2;item.occurrences=2;item.lastReceived=10;state.allItems.add(item);
			for(int n=0;n<2;n++) { PanelState.Source collection=new PanelState.Source();collection.key="collection:"+n;collection.name="Cow";collection.lastReceived=10-n;collection.countTooltip="Raw notification";collection.items.add(item);state.collections.add(collection); }
			LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});panel.update(state);JComboBox<?> chooser=findViewSelector(panel);assertNotNull(chooser);chooser.setSelectedItem("Recent collections");assertNotNull(findLabel(panel,"Page 1 / 1"));assertEquals(2,countLabels(panel,"▾ Cow"));assertNull(findLabel(panel,"Rate unknown"));chooser.setSelectedItem("All items");assertNotNull(findLabel(panel,"x2"));layout(panel,230);JButton row=findButton(panel,"Bones");assertEquals(0,row.getX());assertEquals(row.getParent().getWidth(),row.getWidth());assertNull(findLabel(panel,"Rate unknown"));
		});
	}
	private static JComboBox<?> findViewSelector(Container c) { for(Component child:c.getComponents()) { if(child instanceof JComboBox&&((JComboBox<?>)child).getItemCount()==3&&"By source".equals(((JComboBox<?>)child).getItemAt(0))) { return (JComboBox<?>)child; }if(child instanceof Container) { JComboBox<?> found=findViewSelector((Container)child);if(found!=null) { return found; } } }return null; }
	private static int countLabels(Container c,String text) { int count=0;for(Component child:c.getComponents()) { if(child instanceof javax.swing.JLabel&&text.equals(((javax.swing.JLabel)child).getText())) { count++; }if(child instanceof Container) { count+=countLabels((Container)child,text); } }return count; }
	@Test public void wikiButtonTogglesWithoutCollapsingSource() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();state.notice="";PanelState.Source source=new PanelState.Source();source.key="salvage";source.name="Large salvage";source.validationAvailable=true;source.count=1000;source.value="733k gp";source.countTooltip="Recorded outcomes";source.wikiUrl="https://oldschool.runescape.wiki/w/Large_salvage";
			PanelState.Item item=new PanelState.Item();item.key="plank";item.name="Plank";item.rate="1/17.2";item.rateTooltip="Published rate";item.quantity=1;item.rarity=java.awt.Color.GREEN;source.items.add(item);state.sources.add(source);
			java.util.List<String> opened=new java.util.ArrayList<>();LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{},opened::add);panel.update(state);
			JButton wiki=findButton(panel,"Open Wiki: Large salvage");assertNotNull(wiki);wiki.doClick();assertEquals(java.util.List.of(source.wikiUrl),opened);assertNotNull(findButton(panel,"Plank"));layout(panel,230);assertTrue(panel.getPreferredSize().width<=280);
			state.showWikiLinks=false;panel.update(state);assertNull(findButton(panel,"Open Wiki: Large salvage"));assertNotNull(findButton(panel,"Plank"));
		});
	}
	@Test public void togglesAllKnownRatesAndKeepsUnknownTableCounts() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();state.notice="";PanelState.Source source=new PanelState.Source();source.key="goblin";source.name="Goblin";source.count=1;source.countTooltip="Recorded outcomes";
			PanelState.Item item=new PanelState.Item();item.key="hammer";item.name="Hammer";item.rate="Variants: 1/8.5, 1/14.2";item.rateTooltip="Published variants";item.reason="Unresolved";item.quantity=1;item.occurrences=1;item.unresolvedQuantity=1;item.unresolvedOccurrences=1;item.rarity=java.awt.Color.GREEN;
			for(String rate:new String[]{"1/8.5","1/14.2"}) { PanelState.RateOption option=new PanelState.RateOption();option.table="Table "+(item.rateOptions.size()+1);option.rate=rate;option.color=java.awt.Color.GREEN;item.rateOptions.add(option); }
			source.items.add(item);state.sources.add(source);LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});panel.update(state);
			assertNotNull(findLabel(panel,"Multiple published rates"));assertNull(((javax.swing.JLabel)findLabel(panel,"Multiple published rates")).getToolTipText());assertNull(findLabel(panel,"1/8.5"));state.showAllRates=true;panel.update(state);assertNotNull(findLabel(panel,"1/8.5"));assertNotNull(findLabel(panel,"1/14.2"));assertNull(((javax.swing.JLabel)findLabel(panel,"1/8.5")).getToolTipText());
			findButton(panel,"Hammer").doClick();assertNotNull(findLabel(panel,"Unknown table"));assertNotNull(findLabel(panel,"x1"));layout(panel,230);assertTrue(panel.getPreferredSize().width<=280);
		});
	}
	@Test public void inlineExpansionCollapseSortAndStandardSidebar() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();state.notice="Fixture preview";state.dataStatus="Synthetic test data";
			PanelState.Source source=new PanelState.Source();source.key="test";source.name="Large salvage";source.validationAvailable=true;source.count=1000;source.value="733k gp";source.countTooltip="Recorded notifications";
			PanelState.Item item=new PanelState.Item();item.key="cotton";item.name="Cotton seed";item.rate="1/443.6";item.quantity=22;item.occurrences=7;item.reason="Fixture certified segment";item.rarity=java.awt.Color.ORANGE;item.rateTooltip="5/2218";item.attempts=1000L;item.expected=2.254;item.ratio=3.105;item.dry=399L;item.noDrop=.406;
			source.items.add(item);state.sources.add(source);
			item.valueAvailable=true;item.ge=5100;item.ha=3300;item.geEach=150;item.haEach=100;
			item.id=1;BufferedImage sprite=new BufferedImage(36,32,BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D spriteGraphics=sprite.createGraphics();spriteGraphics.setColor(java.awt.Color.ORANGE);spriteGraphics.fillRect(0,0,36,32);spriteGraphics.dispose();item.icon=new javax.swing.ImageIcon(sprite);
			LootLuckPanel panel=new LootLuckPanel(id->null,session->{},all->{},()->{},()->{});panel.update(state);
			assertNull(findLabel(panel,"Quantity"));JButton button=findButton(panel,"Cotton seed");assertNotNull(button);button.doClick();assertNotNull(findLabel(panel,"Quantity"));
			assertNull(button.getToolTipText());assertNotNull(findButton(panel,"Item information: Cotton seed"));
			assertNull(findLabel(panel,"A/E"));assertNull(findLabel(panel,"Expected"));assertNull(findLabel(panel,"Dry"));assertNull(findLabel(panel,"Per attempt"));assertNull(findLabel(panel,"Validated segment"));assertNull(findButton(panel,"Validate luck tracking"));findButton(panel,"Cotton seed").doClick();assertNull(findLabel(panel,"Quantity"));
			assertNotNull(findLabel(panel,"x7"));state.iconQuantities=false;panel.update(state);assertNotNull(findLabel(panel,"22"));state.iconQuantities=true;panel.update(state);assertNotNull(findLabel(panel,"x7"));
			state.stackCounts=true;panel.update(state);clickLabel(panel,"1/443.6");assertNotNull(findLabel(panel,"Quantity"));clickLabel(panel,"x7");assertNull(findLabel(panel,"Quantity"));assertNoTooltips(panel);assertNotNull(findLabel(panel,"22"));assertEquals(java.awt.Color.YELLOW,((javax.swing.JLabel)findLabel(panel,"22")).getForeground());assertNotNull(findLabel(panel,"x7"));item.quantity=7;panel.update(state);assertNull(findLabel(panel,"x7"));item.quantity=22;state.stackCounts=false;panel.update(state);
			findButton(panel,"Cotton seed").doClick();layout(panel,230);assertTrue(panel.getPreferredSize().width<=280);
			javax.swing.JLabel icon=findIcon(panel);assertNotNull(icon);assertTrue(icon.getWidth()-icon.getInsets().left-icon.getInsets().right>=36);
			BufferedImage image=new BufferedImage(230,panel.getPreferredSize().height,BufferedImage.TYPE_INT_ARGB);panel.paint(image.getGraphics());
			try { java.io.File out=new java.io.File("build/ui-check.png");out.getParentFile().mkdirs();ImageIO.write(image,"png",out); } catch(Exception e) { throw new IllegalStateException(e); }
			findButton(panel,"Large salvage").doClick();assertNull(findButton(panel,"Cotton seed"));findButton(panel,"Large salvage").doClick();assertNotNull(findButton(panel,"Cotton seed"));
		});
	}
	@Test public void hoverRemainsActiveAcrossItemLabelsAndClearsOutsideRow() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			PanelState state=new PanelState();PanelState.Source source=new PanelState.Source();source.key="test";source.name="Test";
			PanelState.Item item=new PanelState.Item();item.key="coins";item.name="Coins";item.rate="1/5.4";item.quantity=70;item.occurrences=2;source.items.add(item);state.sources.add(source);
			LootLuckPanel panel=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});panel.update(state);layout(panel,230);
			JButton row=findButton(panel,"Coins");Component rate=findLabel(row,"1/5.4");Component count=findLabel(row,"x2");
			rate.dispatchEvent(new java.awt.event.MouseEvent(rate,java.awt.event.MouseEvent.MOUSE_ENTERED,0,0,1,1,0,false));assertTrue(row.getModel().isRollover());
			java.awt.Point inside=SwingUtilities.convertPoint(row,new java.awt.Point(1,1),rate);
			rate.dispatchEvent(new java.awt.event.MouseEvent(rate,java.awt.event.MouseEvent.MOUSE_EXITED,0,0,inside.x,inside.y,0,false));assertTrue(row.getModel().isRollover());
			count.dispatchEvent(new java.awt.event.MouseEvent(count,java.awt.event.MouseEvent.MOUSE_ENTERED,0,0,1,1,0,false));assertTrue(row.getModel().isRollover());
			java.awt.Point outside=SwingUtilities.convertPoint(row,new java.awt.Point(-1,-1),count);
			count.dispatchEvent(new java.awt.event.MouseEvent(count,java.awt.event.MouseEvent.MOUSE_EXITED,0,0,outside.x,outside.y,0,false));assertFalse(row.getModel().isRollover());
		});
	}
	private static void clickLabel(Container panel,String text)
	{
		Component label=findLabel(panel,text);assertNotNull(label);
		label.dispatchEvent(new java.awt.event.MouseEvent(label,java.awt.event.MouseEvent.MOUSE_CLICKED,0,0,1,1,0,0,1,false,java.awt.event.MouseEvent.BUTTON1));
	}
	private static void assertNoTooltips(Container container)
	{
		for(Component child:container.getComponents()) { if(child instanceof javax.swing.JComponent) { if(child instanceof JButton&&java.util.Set.of("Show unobtained items","Switch to list view","Switch to grid view","Collapse all sources","Expand all sources","Settings").contains(((JButton)child).getAccessibleContext().getAccessibleName())) { JButton action=(JButton)child;assertNotNull(action.getToolTipText());assertEquals(java.awt.Color.WHITE,action.getForeground()); }else { assertNull(((javax.swing.JComponent)child).getToolTipText()); } }if(child instanceof Container) { assertNoTooltips((Container)child); } }
	}
	private static Component findLabel(Container c,String text) { for(Component child:c.getComponents()) { if(child instanceof javax.swing.JLabel && text.equals(((javax.swing.JLabel)child).getText())) { return child; } if(child instanceof Container) { Component found=findLabel((Container)child,text);if(found!=null) { return found; } } }return null; }
	private static javax.swing.JLabel findIcon(Container c) { for(Component child:c.getComponents()) { if(child instanceof javax.swing.JLabel&&((javax.swing.JLabel)child).getIcon()!=null) { return (javax.swing.JLabel)child; }if(child instanceof Container) { javax.swing.JLabel found=findIcon((Container)child);if(found!=null) { return found; } } }return null; }
	private static JButton findButton(Container c,String name) { for(Component child:c.getComponents()) { if(child instanceof JButton&&((JButton)child).getAccessibleContext().getAccessibleName().startsWith(name)) { return (JButton)child; }if(child instanceof Container) { JButton found=findButton((Container)child,name);if(found!=null) { return found; } } }return null; }
	private static void layout(Container c,int width) { c.setSize(new Dimension(width,c.getPreferredSize().height));c.doLayout();for(Component child:c.getComponents()) { if(child instanceof Container) { Container nested=(Container)child;nested.doLayout();layoutChildren(nested); } } }
	private static void layoutChildren(Container c) { c.doLayout();for(Component child:c.getComponents()) { if(child instanceof Container) { layoutChildren((Container)child); } } }
}



