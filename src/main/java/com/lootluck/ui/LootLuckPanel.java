package com.lootluck.ui;

import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.FontManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Dimension;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.SwingUtilities;

/** Native sidebar: rate-first rows with a single inline disclosure. */
public final class LootLuckPanel extends PluginPanel
{
	private static final Color BG=new Color(34,34,34),ROW=new Color(30,30,30),MUTED=new Color(173,169,160),GOLD=new Color(223,192,113);
	private static final DecimalFormat NUMBER=new DecimalFormat("#,##0.#",DecimalFormatSymbols.getInstance(Locale.US));
	private static final DecimalFormat DECIMAL=new DecimalFormat("0.00",DecimalFormatSymbols.getInstance(Locale.US));
	private final JPanel content=new JPanel();
	private final JPanel top=new JPanel(new GridLayout(0,1,0,4));
	private final JPanel settings=new JPanel(new GridLayout(0,1,0,6));
	private final JPanel settingsHeader=new JPanel(new BorderLayout(4,0));
	private LootLuckSettingsPanel configSettings;
	private final javax.swing.JToggleButton lifetimeButton=new javax.swing.JToggleButton("Lifetime"),sessionButton=new javax.swing.JToggleButton("Session");
	private final JComboBox<String> sort=new JComboBox<>(new String[]{"Most recent","Rarest first","Driest first","Commonest first","Wiki order"});
	private final java.util.function.BiFunction<Integer,Long,Icon> icons;
	private final JComboBox<String> view=new JComboBox<>(new String[]{"By source","Recent collections","All items"});
	private final JComboBox<String> itemSort=new JComboBox<>(new String[]{"Most recent","Most occurrences","Most quantity"});
	private final JPanel controls=new JPanel(new GridLayout(1,1));
	private final JButton exportData=new JButton("Export data"),importData=new JButton("Import data"),deleteData=new JButton("Delete data");
	private final JButton refreshButton=new JButton("Refresh"),resetButton=new JButton("Reset session");
	private Consumer<String> dataAction=action->{};
	private Consumer<String> validationAction=source->{};
	private int historyPage;
	private Runnable viewChanged=()->{};
	public void setViewAction(Runnable action) { viewChanged=action; }
	public int requestedHistoryPage() { return view.getSelectedIndex()==1?historyPage:-2; }
	private final Consumer<Boolean> scopeChanged;
	private final Consumer<String> openWiki;
	private final Set<String> collapsed=new HashSet<>();
	private final JButton toggleSources=new JButton();
	private final JButton toggleGrid=new JButton();
	private final JButton toggleUnobtained=new JButton();
	private Consumer<Boolean> gridChanged=value->{};
	private PanelState state=new PanelState();
	private String expanded;
	private String gridReveal;
	private boolean showingSettings;
	private final JLabel noticeLabel=label("",MUTED),statusLabel=label("",MUTED),emptyLabel=label("No loot recorded in this view.",MUTED);
	private final java.util.Map<String,SourceCard> sourceCards=new java.util.HashMap<>();
	private static final class SourceCard
	{
		final JPanel panel;final java.awt.Component spacing=javax.swing.Box.createVerticalStrut(5);
		final java.util.List<Object> presentation;final PanelState.Source source;
		SourceCard(JPanel panel,java.util.List<Object> presentation,PanelState.Source source) { this.panel=panel;this.presentation=presentation;this.source=source; }
	}
	private final java.util.Map<String,JButton> itemButtons=new java.util.HashMap<>();
	public LootLuckPanel(IntFunction<Icon> icons,Consumer<Boolean> scopeChanged,Consumer<Boolean> allChanged,Runnable reset,Runnable refresh)
	{
		this(icons,scopeChanged,allChanged,reset,refresh,net.runelite.client.util.LinkBrowser::browse);
	}
	public LootLuckPanel(IntFunction<Icon> icons,Consumer<Boolean> scopeChanged,Consumer<Boolean> allChanged,Runnable reset,Runnable refresh,Consumer<String> openWiki)
	{
		this((id,quantity)->icons.apply(id),scopeChanged,allChanged,reset,refresh,openWiki);
	}
	public LootLuckPanel(java.util.function.BiFunction<Integer,Long,Icon> icons,Consumer<Boolean> scopeChanged,Consumer<Boolean> allChanged,Runnable reset,Runnable refresh)
	{
		this(icons,scopeChanged,allChanged,reset,refresh,net.runelite.client.util.LinkBrowser::browse);
	}
	public LootLuckPanel(java.util.function.BiFunction<Integer,Long,Icon> icons,Consumer<Boolean> scopeChanged,Consumer<Boolean> allChanged,Runnable reset,Runnable refresh,Consumer<String> openWiki)
	{
		this.icons=icons;this.scopeChanged=scopeChanged;this.openWiki=openWiki;
		if(!PanelState.LUCK_STATISTICS_VISIBLE) { sort.removeItem("Driest first"); }
		toggleUnobtained.setMargin(new java.awt.Insets(2,3,2,3));toggleUnobtained.setToolTipText("Show unobtained items");toggleUnobtained.getAccessibleContext().setAccessibleName("Show unobtained items");
		toggleUnobtained.addActionListener(e->{state.includeUnobtained=!state.includeUnobtained;allChanged.accept(state.includeUnobtained);render();});
		setLayout(new BorderLayout(0,5));setBackground(BG);setBorder(BorderFactory.createEmptyBorder(7,6,7,6));
		top.setOpaque(false);
		JPanel heading=new JPanel(new BorderLayout());heading.setOpaque(false);
		JLabel title=new JLabel("Loot Luck");title.setForeground(GOLD);title.setFont(FontManager.getRunescapeFont());heading.add(title,BorderLayout.CENTER);
		JButton settingsButton=new JButton("⚙");settingsButton.setFont(new java.awt.Font("Dialog",java.awt.Font.PLAIN,18));settingsButton.getAccessibleContext().setAccessibleName("Settings");settingsButton.setToolTipText("Settings");top.add(heading);
		JPanel headerActions=new JPanel(new GridLayout(1,4,2,0));headerActions.setOpaque(false);headerActions.add(toggleUnobtained);headerActions.add(toggleGrid);headerActions.add(toggleSources);headerActions.add(settingsButton);heading.add(headerActions,BorderLayout.EAST);
		for(java.awt.Component action:headerActions.getComponents()) { action.setForeground(Color.WHITE); }
		toggleGrid.setFont(new java.awt.Font("Dialog",java.awt.Font.PLAIN,16));toggleGrid.setMargin(new java.awt.Insets(2,4,2,4));
		toggleGrid.addActionListener(e->{state.gridView=!state.gridView;gridChanged.accept(state.gridView);render();});
		toggleSources.addActionListener(e->{boolean collapse=state.sources.stream().anyMatch(source->!collapsed.contains(source.key));for(PanelState.Source source:state.sourceRows()) { if(collapse) { collapsed.add(source.key); }else { collapsed.remove(source.key); } }render();});
		settings.setOpaque(false);JButton back=new JButton("← Back to loot");back.setForeground(Color.WHITE);
		settingsHeader.setBackground(BG);settingsHeader.setBorder(BorderFactory.createEmptyBorder(7,6,7,6));settingsHeader.add(back,BorderLayout.CENTER);settings.add(label("Settings",GOLD));
		settingsButton.addActionListener(e->showSettings(true));back.addActionListener(e->showSettings(false));
		JPanel scopeSelector=new JPanel(new GridLayout(1,2,0,0));scopeSelector.setOpaque(false);
		javax.swing.ButtonGroup scopeGroup=new javax.swing.ButtonGroup();scopeGroup.add(lifetimeButton);scopeGroup.add(sessionButton);lifetimeButton.setSelected(true);
		for(javax.swing.JToggleButton button:java.util.List.of(lifetimeButton,sessionButton))
		{
			button.setFocusPainted(false);button.setOpaque(true);button.setMargin(new java.awt.Insets(5,4,5,4));styleScopeButton(button);scopeSelector.add(button);
			button.addItemListener(event->{styleScopeButton(button);if(event.getStateChange()==java.awt.event.ItemEvent.SELECTED) { scopeChanged.accept(session()); }});
		}
		top.add(scopeSelector);top.add(view);
		controls.setOpaque(false);controls.add(sort);top.add(controls);
		add(top,BorderLayout.NORTH);
		for(JButton action:java.util.List.of(exportData,importData,deleteData)) { action.addActionListener(e->dataAction.accept(action.getText())); }
		deleteData.setBackground(new Color(145,45,45));deleteData.setForeground(Color.WHITE);deleteData.setOpaque(true);
		content.setLayout(new javax.swing.BoxLayout(content,javax.swing.BoxLayout.Y_AXIS));content.setOpaque(false);add(content,BorderLayout.CENTER);
		sort.addActionListener(e->{scopeChanged.accept(session());render();});
		refreshButton.addActionListener(e->refresh.run());resetButton.addActionListener(e->reset.run());
		view.addActionListener(e->{historyPage=0;controls.removeAll();top.remove(controls);if(view.getSelectedIndex()==0) { controls.add(sort);top.add(controls); }else if(view.getSelectedIndex()==2) { controls.add(itemSort);top.add(controls); }top.revalidate();top.repaint();viewChanged.run();render();});itemSort.addActionListener(e->render());
	}
	private static Icon eyeIcon(boolean slashed)
	{
		return new Icon() {
			public int getIconWidth() { return 18; }
			public int getIconHeight() { return 18; }
			public void paintIcon(java.awt.Component component,java.awt.Graphics graphics,int x,int y)
			{
				java.awt.Graphics2D g=(java.awt.Graphics2D)graphics.create();try { g.translate(x,y);g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(Color.WHITE);g.setStroke(new java.awt.BasicStroke(1.5f));java.awt.geom.Path2D eye=new java.awt.geom.Path2D.Double();eye.moveTo(1,9);eye.curveTo(5,2,13,2,17,9);eye.curveTo(13,16,5,16,1,9);g.draw(eye);g.fillOval(7,7,4,4);if(slashed) { g.setStroke(new java.awt.BasicStroke(2));g.drawLine(2,2,16,16); } }finally { g.dispose(); }
			}
		};
	}
	private static void styleScopeButton(javax.swing.JToggleButton button)
	{
		button.setBackground(button.isSelected()?new Color(56,49,39):ROW);button.setForeground(button.isSelected()?GOLD:MUTED);
		button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(button.isSelected()?GOLD:new Color(65,65,65)),BorderFactory.createEmptyBorder(5,4,5,4)));
	}
	public boolean session() { return sessionButton.isSelected(); }
	public void setSettings(com.lootluck.LootLuckConfig config,java.util.function.BiConsumer<String,Object> changed,java.util.function.Function<Boolean,Icon> previewIcons)
	{
		state.includeUnobtained=config.includeUnobtained();state.itemEffect=config.itemEffect();state.gridView=config.gridView();gridChanged=value->changed.accept("gridView",value);
		JPanel dataActions=new JPanel(new GridLayout(0,1,0,4));dataActions.setOpaque(false);dataActions.add(exportData);dataActions.add(importData);dataActions.add(deleteData);
		configSettings=new LootLuckSettingsPanel(config,changed,previewIcons,refreshButton,resetButton,dataActions);settings.add(configSettings);settings.setLayout(new javax.swing.BoxLayout(settings,javax.swing.BoxLayout.Y_AXIS));
		for(java.awt.Component child:settings.getComponents()) { if(child instanceof javax.swing.JComponent) { ((javax.swing.JComponent)child).setAlignmentX(LEFT_ALIGNMENT);((javax.swing.JComponent)child).setMaximumSize(new Dimension(Integer.MAX_VALUE,child.getPreferredSize().height)); } }
	}
	static JButton stylePreview(ItemDisplayStyle style,Icon icon)
	{
		LootLuckPanel preview=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});
		preview.state.iconQuantities=style.iconQuantities;preview.state.stackCounts=style.stackCounts;preview.state.itemEffect=ItemEffect.NONE;
		PanelState.Item item=new PanelState.Item();item.key="preview";item.name="Coins";item.rate="1/128";item.quantity=12500;item.occurrences=7;item.icon=icon;item.rarity=GOLD;
		JPanel row=preview.itemRow(item);JButton button=(JButton)row.getComponent(0);
		for(java.awt.event.ActionListener listener:button.getActionListeners()) { button.removeActionListener(listener); }
		row.remove(button);button.setAlignmentX(LEFT_ALIGNMENT);button.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));button.setPreferredSize(new Dimension(210,44));
		button.getAccessibleContext().setAccessibleName(style.title);return button;
	}
	private void showSettings(boolean visible)
	{
		showingSettings=visible;if(!visible) { render(); }
		if(configSettings!=null) { configSettings.sync(); }
		removeAll();add(visible?settings:top,BorderLayout.NORTH);if(!visible) { add(content,BorderLayout.CENTER); }
		getWrappedPanel().remove(settingsHeader);if(visible) { getWrappedPanel().add(settingsHeader,BorderLayout.NORTH); }
		revalidate();repaint();getWrappedPanel().revalidate();getWrappedPanel().repaint();
		SwingUtilities.invokeLater(()->getScrollPane().getVerticalScrollBar().setValue(0));
	}
	public void setDataAction(Consumer<String> action) { dataAction=action; }
	public void setValidationAction(Consumer<String> action) { validationAction=action; }
	public boolean driest() { return PanelState.LUCK_STATISTICS_VISIBLE&&sortMode()==2; }
	public void update(PanelState state) { if(!SwingUtilities.isEventDispatchThread()) { throw new IllegalStateException("Swing thread required"); } this.state=state;render(); }
	private void render()
	{
		for(JButton action:java.util.List.of(resetButton,exportData,importData,deleteData)) { action.setEnabled(state.canManageData); }
		if(showingSettings) { if(configSettings!=null) { configSettings.sync(); }return; }
		boolean showingUnobtained=state.includeUnobtained||driest();toggleUnobtained.setIcon(eyeIcon(!showingUnobtained));toggleUnobtained.setSelected(showingUnobtained);toggleUnobtained.setEnabled(view.getSelectedIndex()==0&&!driest());toggleUnobtained.getAccessibleContext().setAccessibleDescription(showingUnobtained?"Unobtained items shown":"Unobtained items hidden");toggleUnobtained.setToolTipText(showingUnobtained?"Hide unobtained items":"Show unobtained items");
		toggleGrid.setText(state.gridView?"☰":"▦");toggleGrid.getAccessibleContext().setAccessibleName(state.gridView?"Switch to list view":"Switch to grid view");toggleGrid.setEnabled(view.getSelectedIndex()!=2);
		toggleGrid.setToolTipText(toggleGrid.getAccessibleContext().getAccessibleName());
		boolean anyExpanded=state.sources.stream().anyMatch(source->!collapsed.contains(source.key));toggleSources.setText(anyExpanded?"▴":"▾");toggleSources.getAccessibleContext().setAccessibleName(anyExpanded?"Collapse all sources":"Expand all sources");toggleSources.setEnabled(view.getSelectedIndex()==0&&!state.sources.isEmpty());
		toggleSources.setToolTipText(toggleSources.getAccessibleContext().getAccessibleName());
		if(configSettings!=null) { configSettings.sync(); }
		if(view.getSelectedIndex()!=0)
		{
			content.removeAll();itemButtons.clear();sourceCards.clear();
			if(!state.notice.isEmpty()) { content.add(label(state.notice,MUTED)); }
			renderObservations();content.revalidate();content.repaint();return;
		}
		if(sourceCards.isEmpty()) { itemButtons.clear(); }
		Set<String> keys=new HashSet<>();for(PanelState.Source source:state.sources) { keys.add(source.key); }
		sourceCards.entrySet().removeIf(entry->{if(keys.contains(entry.getKey())) { return false; }releaseCard(entry.getValue());return true;});
		java.util.List<java.awt.Component> desired=new ArrayList<>();
		if(!state.notice.isEmpty()) { noticeLabel.setText(state.notice);desired.add(noticeLabel); }
		for(PanelState.Source source:state.sources)
		{
			java.util.List<Object> presentation=presentation(source);SourceCard card=sourceCards.get(source.key);
			if(card==null||!card.presentation.equals(presentation))
			{
				if(card!=null) { releaseCard(card); }
				card=new SourceCard(sourceCard(source),presentation,source);sourceCards.put(source.key,card);
			}
			desired.add(card.panel);desired.add(card.spacing);
		}
		if(state.sources.isEmpty()) { emptyLabel.setBorder(BorderFactory.createEmptyBorder(12,0,12,0));desired.add(emptyLabel); }
		statusLabel.setText(state.dataStatus);desired.add(statusLabel);
		for(int i=0;i<desired.size();i++)
		{
			java.awt.Component component=desired.get(i);
			if(component.getParent()!=content) { content.add(component,i); }
			else if(content.getComponent(i)!=component) { content.setComponentZOrder(component,i); }
		}
		while(content.getComponentCount()>desired.size()) { content.remove(content.getComponentCount()-1); }
		content.revalidate();content.repaint();
	}
	private void releaseCard(SourceCard card)
	{
		content.remove(card.panel);content.remove(card.spacing);for(PanelState.Item item:card.source.items) { itemButtons.remove(item.key); }
	}
	private java.util.List<Object> presentation(PanelState.Source source)
	{
		java.util.List<Object> values=new ArrayList<>(java.util.Arrays.asList(state.showGe,state.showHa,state.luckColors,state.showAllRates,state.showWikiLinks,state.groupByDropTable,state.hideUnobtainedOtherWorld,state.gridView,state.itemEffect,state.stackCounts,state.iconQuantities,sortMode(),collapsed.contains(source.key),source.key,source.name,source.count,source.value,source.wikiUrl,source.familyKey,source.validationAvailable,source.validationApproved));
		for(PanelState.Item item:source.items)
		{
			values.add(java.util.Arrays.asList(item.key,item.id,item.name,item.quantity,item.occurrences,item.rate,item.rateTooltip,item.reason,item.worldMarker,item.dropTable,item.tableOrder,item.wikiOrder,item.publishedRarity,item.lastReceived,item.occurrenceOnly,item.rarity,backgroundColor(item),item.icon,item.ge,item.ha,item.valueAvailable,item.attempts,item.dry,item.probability,item.expected,item.ratio,item.observed,item.noDrop,item.upper,item.lower,item.dryness,item.unresolvedQuantity,item.unresolvedOccurrences,item.key.equals(expanded)));
			for(PanelState.RateOption option:item.rateOptions) { values.add(java.util.Arrays.asList(option.key,option.table,option.dropQuantity,option.rate,option.tooltip,option.color,option.historical,option.quantity,option.occurrences)); }
		}
		return values;
	}
	private JPanel sourceCard(PanelState.Source source)
	{
			JPanel group=new JPanel(new BorderLayout()) { @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE,getPreferredSize().height); } };group.setOpaque(false);group.setAlignmentX(LEFT_ALIGNMENT);
			JButton header=new JButton();header.setLayout(new BorderLayout(4,0));header.setBackground(new Color(16,16,16));header.setBorder(BorderFactory.createEmptyBorder(8,4,8,4));
			JPanel summary=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT,4,0));summary.setOpaque(false);
			if(state.showGe) { summary.add(label(source.value==null?"— gp":source.value,MUTED)); }
			header.add(summary,BorderLayout.EAST);
			JLabel name=new SourceHeaderLabel((collapsed.contains(source.key)?"▸ ":"▾ ")+source.name,"x"+CountFormat.compact(source.count),MUTED);
			name.setFont(FontManager.getRunescapeSmallFont());name.setForeground(GOLD);header.add(name,BorderLayout.CENTER);
			header.getAccessibleContext().setAccessibleName(source.name+", "+source.count+" recorded outcomes, "+(collapsed.contains(source.key)?"collapsed":"expanded"));
			header.addActionListener(e->{if(!collapsed.add(source.key)) { collapsed.remove(source.key); }render();});
			makeRowClickable(header,header);
			addWikiMenu(header,source.wikiUrl==null?ViewBuilder.sourceWikiUrl(source.name):source.wikiUrl);
			JPanel heading=new JPanel(new BorderLayout(2,0));heading.setBackground(new Color(16,16,16));heading.add(header,BorderLayout.CENTER);
			if(state.showWikiLinks&&source.wikiUrl!=null)
			{
				JButton wiki=new JButton("W");wiki.setFont(new java.awt.Font("SansSerif",java.awt.Font.BOLD,10));wiki.setForeground(MUTED);wiki.setBackground(new Color(16,16,16));wiki.setBorder(BorderFactory.createEmptyBorder(0,3,0,3));wiki.setPreferredSize(new Dimension(20,26));wiki.getAccessibleContext().setAccessibleName("Open Wiki: "+source.name);wiki.addActionListener(e->openWiki.accept(source.wikiUrl));heading.add(wiki,BorderLayout.EAST);
			}
			group.add(heading,BorderLayout.NORTH);
			if(PanelState.LUCK_STATISTICS_VISIBLE&&source.validationAvailable)
			{
				JButton validation=new JButton(source.validationApproved?"Luck tracking validated":"Validate luck tracking");
				validation.setEnabled(!source.validationApproved);validation.setToolTipText("Confirm the source-specific gameplay checks before enabling exact luck statistics.");
				validation.addActionListener(e->validationAction.accept(source.familyKey==null?source.key:source.familyKey));group.add(validation,BorderLayout.SOUTH);
			}
			if(!collapsed.contains(source.key))
			{
				JPanel rows=new JPanel();rows.setOpaque(false);rows.setLayout(new javax.swing.BoxLayout(rows,javax.swing.BoxLayout.Y_AXIS));
				ArrayList<PanelState.Item> ordered=new ArrayList<>(source.items);
				ordered.sort(ItemOrdering.comparator(sortMode(),state.groupByDropTable));
				if(state.gridView) { group.add(sourceGrid(ordered),BorderLayout.CENTER); }
				else if(ordered.size()>100) { group.add(virtualSourceItems(ordered),BorderLayout.CENTER); }
				else { for(PanelState.Item item:ordered) { rows.add(itemRow(item));if(item.key.equals(expanded)) { rows.add(details(item)); } }group.add(rows,BorderLayout.CENTER); }
			}
			return group;
	}
	private JPanel virtualSourceItems(java.util.List<PanelState.Item> items)
	{
		java.util.List<Integer> heights=new ArrayList<>();
		final JPanel disclosure=items.stream().filter(item->item.key.equals(expanded)).findFirst().map(this::details).orElse(null);
		int lineHeight=getFontMetrics(FontManager.getRunescapeSmallFont()).getHeight();
		for(PanelState.Item item:items)
		{
			int rates=1;if(state.showAllRates) { rates=Math.max(1,(int)item.rateOptions.stream().filter(option->!option.historical).map(option->option.rate).distinct().count()); }
			heights.add(Math.max(44,8+(1+rates)*lineHeight+1)+(item.key.equals(expanded)&&disclosure!=null?disclosure.getPreferredSize().height:0));
		}
		return new VirtualItemList(heights,index->{
			PanelState.Item item=items.get(index);JPanel row=new JPanel(new BorderLayout());row.setOpaque(false);row.add(itemRow(item),BorderLayout.NORTH);if(item.key.equals(expanded)&&disclosure!=null) { row.add(disclosure,BorderLayout.CENTER); }return row;
		},index->itemButtons.remove(items.get(index).key));
	}
	private JPanel sourceGrid(java.util.List<PanelState.Item> items)
	{
		return sourceGrid(items,false);
	}
	private JPanel sourceGrid(java.util.List<PanelState.Item> items,boolean collection)
	{
		final int columns=5;int rowCount=(items.size()+columns-1)/columns;
		int selected=-1;for(int i=0;i<items.size();i++) { if(items.get(i).key.equals(expanded)) { selected=i;break; } }
		final int selectedRow=selected<0?-1:selected/columns;
		final PanelState.Item selectedItem=selected<0?null:items.get(selected);
		final JPanel disclosure;
		if(selectedItem==null) { disclosure=null; }
		else
		{
			disclosure=new JPanel(new BorderLayout(0,3));disclosure.setOpaque(false);JPanel title=new JPanel(new GridLayout(0,1));title.setOpaque(false);
			title.add(label(selectedItem.name+(!state.hideUnobtainedOtherWorld&&!selectedItem.worldMarker.isEmpty()?" "+selectedItem.worldMarker:""),GOLD));if(!collection) { title.add(rateLabel(selectedItem.rate,selectedItem.rarity)); }disclosure.add(title,BorderLayout.NORTH);disclosure.add(collection?collectionItemDetails(selectedItem):details(selectedItem),BorderLayout.CENTER);
		}
		return new VirtualItemList(rowCount,columns,selectedRow,disclosure==null?0:disclosure.getPreferredSize().height+6,index->{
			JPanel iconsRow=new JPanel(null) {
				@Override public void doLayout() { int side=Math.max(1,(getWidth()-2*(columns-1))/columns);int left=(getWidth()-(side*columns+2*(columns-1)))/2;for(int i=0;i<getComponentCount();i++) { getComponent(i).setBounds(left+i*(side+2),0,side,side); } }
			};iconsRow.setOpaque(false);
			for(int column=0;column<columns;column++) { int position=index*columns+column;if(position<items.size()) { iconsRow.add(gridItem(items.get(position))); }else { JPanel space=new JPanel();space.setOpaque(false);iconsRow.add(space); } }
			JPanel row=new JPanel(null) {
				@Override public void doLayout() { int side=Math.max(1,(getWidth()-2*(columns-1))/columns);iconsRow.setBounds(0,0,getWidth(),side);if(index==selectedRow&&disclosure!=null) { disclosure.setBounds(0,side+6,getWidth(),Math.max(0,getHeight()-side-8)); } }
			};row.setOpaque(false);row.add(iconsRow);
			if(index==selectedRow&&disclosure!=null)
			{
				row.add(disclosure);
				if(selectedItem.key.equals(gridReveal)) { gridReveal=null;SwingUtilities.invokeLater(()->{if(row.isShowing()) { row.scrollRectToVisible(new java.awt.Rectangle(0,0,row.getWidth(),row.getHeight())); }}); }
			}
			return row;
		},index->{for(int column=0;column<columns&&index*columns+column<items.size();column++) { itemButtons.remove(items.get(index*columns+column).key); }});
	}
	static Color effectColor(PanelState.Item item)
	{
		return "Varies".equals(item.rate)?new Color(160,160,160):item.rarity;
	}
	private Color backgroundColor(PanelState.Item item)
	{
		if(state.effectColorMode==EffectColorMode.RARITY) { return effectColor(item); }
		return state.effectValuePalette==null?new Color(160,160,160):state.effectValuePalette.color(item,state.effectColorMode);
	}
	private javax.swing.Icon itemIcon(PanelState.Item item)
	{
		return itemIcon(item,true);
	}
	private javax.swing.Icon itemIcon(PanelState.Item item,boolean quantityOverlay)
	{
		if(item.occurrenceOnly) { return new javax.swing.Icon() {
			public int getIconWidth() { return 32; }public int getIconHeight() { return 32; }
			public void paintIcon(java.awt.Component c,java.awt.Graphics g,int x,int y) { java.awt.Graphics2D graphics=(java.awt.Graphics2D)g.create();try { graphics.setColor(new Color(205,130,130));graphics.setStroke(new java.awt.BasicStroke(3));graphics.drawOval(x+5,y+5,22,22);graphics.drawLine(x+11,y+11,x+21,y+21);graphics.drawLine(x+21,y+11,x+11,y+21); }finally { graphics.dispose(); } }
		}; }
		javax.swing.Icon sprite=item.icon!=null?item.icon:item.id>0?icons.apply(item.id,item.quantity):null;
		if(sprite==null) { return null; }
		final String quantityText=CountFormat.compact(item.quantity);
		// Paint the live icon rather than caching a disabled copy of an asynchronous image.
		return new javax.swing.Icon()
		{
			public int getIconWidth() { return sprite.getIconWidth(); }
			public int getIconHeight() { return sprite.getIconHeight(); }
			public void paintIcon(java.awt.Component component,java.awt.Graphics graphics,int x,int y)
			{
				java.awt.Graphics2D faded=(java.awt.Graphics2D)graphics.create();
				try
				{
					if(item.quantity==0) { faded.setComposite(java.awt.AlphaComposite.SrcOver.derive(0.65f)); }
					sprite.paintIcon(component,faded,x,y);
					if(quantityOverlay&&item.quantity>0&&state.iconQuantities&&!state.stackCounts)
					{
						faded.setFont(FontManager.getRunescapeSmallFont());String count=quantityText;int baseline=y+faded.getFontMetrics().getAscent();
						faded.setColor(Color.BLACK);faded.drawString(count,x+3,baseline+1);faded.setColor(Color.YELLOW);faded.drawString(count,x+2,baseline);
					}
				}
				finally { faded.dispose(); }
			}
		};
	}
	private JButton gridItem(PanelState.Item item)
	{
		final String quantityText=item.occurrenceOnly?"x"+CountFormat.compact(item.occurrences):CountFormat.compact(item.quantity);
		JButton button=new EffectButton(item.quantity>0?state.itemEffect:ItemEffect.NONE,backgroundColor(item)) {
			@Override public void doLayout() { for(java.awt.Component child:getComponents()) { child.setBounds(2,2,getWidth()-4,getHeight()-4); } }
			@Override protected void paintForeground(java.awt.Graphics2D graphics)
			{
				super.paintForeground(graphics);
				if(item.quantity>0||item.occurrenceOnly)
				{
					java.awt.Graphics2D count=(java.awt.Graphics2D)graphics.create();
					try
					{
						count.setFont(FontManager.getRunescapeSmallFont());String text=quantityText;int baseline=4+count.getFontMetrics().getAscent();
						count.setColor(Color.BLACK);count.drawString(text,5,baseline+1);count.setColor(Color.YELLOW);count.drawString(text,4,baseline);
					}
					finally { count.dispose(); }
				}
			}
		};button.setLayout(null);button.setBackground(ROW);button.setMargin(new java.awt.Insets(0,0,0,0));
		button.setBorder(BorderFactory.createLineBorder(item.key.equals(expanded)?GOLD:BG,2));
		JLabel icon=new JLabel(itemIcon(item,false));icon.setHorizontalAlignment(JLabel.CENTER);if(item.id==0) { icon.setText("?"); }button.add(icon,BorderLayout.CENTER);
		button.getAccessibleContext().setAccessibleName(item.name+(item.occurrenceOnly?", occurrences "+item.occurrences:", quantity "+item.quantity));button.getAccessibleContext().setAccessibleDescription((item.rate==null?"Recorded collection item":item.rate)+(item.key.equals(expanded)?", expanded":", collapsed"));
		itemButtons.put(item.key,button);button.addActionListener(e->{expanded=item.key.equals(expanded)?null:item.key;gridReveal=expanded;render();});makeRowClickable(button,button);addWikiMenu(button,itemWikiUrl(item));return button;
	}
	private void renderObservations()
	{
		if(view.getSelectedIndex()==1)
		{
			int count=state.collectionPage==-1?state.collections.size():state.collectionCount;
			int pages=Math.max(1,(count-1)/50+1);historyPage=Math.min(historyPage,pages-1);
			JPanel paging=new JPanel(new BorderLayout(4,0));paging.setOpaque(false);paging.setAlignmentX(LEFT_ALIGNMENT);JButton previous=new JButton("‹"),next=new JButton("›");previous.setEnabled(historyPage>0);next.setEnabled(historyPage+1<pages);previous.addActionListener(e->{historyPage--;viewChanged.run();render();});next.addActionListener(e->{historyPage++;viewChanged.run();render();});paging.add(previous,BorderLayout.WEST);paging.add(label("Page "+(historyPage+1)+" / "+pages,MUTED),BorderLayout.CENTER);paging.add(next,BorderLayout.EAST);paging.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));content.add(paging);
			if(state.collectionPage!=-1&&state.collectionPage!=historyPage) { content.add(label("Loading collection history…",MUTED));return; }
			if(state.collections.isEmpty()) { content.add(label("No collection history yet.",MUTED));content.add(label("History starts with new loot notifications.",MUTED)); }
			int start=state.collectionPage==-1?historyPage*50:0;
			for(int index=start;index<Math.min(state.collections.size(),start+50);index++)
			{
				content.add(collectionCard(state.collections.get(index)));content.add(javax.swing.Box.createVerticalStrut(5));
			}
		}
		else
		{
			ArrayList<PanelState.Item> ordered=new ArrayList<>(state.allItems);
			if(itemSort.getSelectedIndex()==0) { ordered.sort(Comparator.comparingLong((PanelState.Item item)->item.lastReceived).reversed().thenComparing(item->item.name)); }
			if(itemSort.getSelectedIndex()==1) { ordered.sort(Comparator.comparingLong((PanelState.Item item)->item.occurrences).reversed().thenComparing(item->item.name)); }
			if(itemSort.getSelectedIndex()==2) { ordered.sort(Comparator.comparingLong((PanelState.Item item)->item.quantity).reversed().thenComparing(item->item.name)); }
			if(ordered.isEmpty()) { content.add(label("No items recorded in this view.",MUTED)); }
			if(ordered.size()>100)
			{
				JPanel disclosure=ordered.stream().filter(item->item.key.equals(expanded)).findFirst().map(this::sourceBreakdown).orElse(null);
				java.util.List<Integer> heights=new ArrayList<>();for(PanelState.Item item:ordered) { heights.add(42+(item.key.equals(expanded)&&disclosure!=null?disclosure.getPreferredSize().height:0)); }
				content.add(new VirtualItemList(heights,index->{PanelState.Item item=ordered.get(index);JPanel row=new JPanel(new BorderLayout());row.setOpaque(false);JButton button=observationRow(item,true);button.setPreferredSize(new Dimension(210,42));row.add(button,BorderLayout.NORTH);if(item.key.equals(expanded)&&disclosure!=null) { row.add(disclosure,BorderLayout.CENTER); }return row;},index->itemButtons.remove(ordered.get(index).key)));
			}
			else { for(PanelState.Item item:ordered) { content.add(observationRow(item,true));if(item.key.equals(expanded)) { content.add(sourceBreakdown(item)); } } }
		}
		content.add(label(state.dataStatus,MUTED));
	}
	private JPanel collectionCard(PanelState.Source collection)
	{
		JPanel card=new JPanel(new BorderLayout()) { @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE,getPreferredSize().height); } };
		card.setOpaque(false);card.setAlignmentX(LEFT_ALIGNMENT);
		boolean closed=collapsed.contains(collection.key);
		JButton header=new JButton();header.setLayout(new BorderLayout(0,3));header.setBackground(new Color(16,16,16));header.setBorder(BorderFactory.createEmptyBorder(8,4,8,4));
		JLabel name=new JLabel((closed?"▸ ":"▾ ")+collection.name) { @Override public Dimension getPreferredSize() { return new Dimension(1,getFontMetrics(getFont()).getHeight()); } };
		name.setFont(FontManager.getRunescapeSmallFont());name.setForeground(GOLD);header.add(name,BorderLayout.NORTH);
		header.add(label(date(collection.lastReceived),MUTED),BorderLayout.CENTER);
		header.getAccessibleContext().setAccessibleName(collection.name+", "+date(collection.lastReceived)+", "+(closed?"collapsed":"expanded"));
		header.addActionListener(event->{if(!collapsed.add(collection.key)) { collapsed.remove(collection.key); }render();});makeRowClickable(header,header);
		addWikiMenu(header,collection.wikiUrl==null?ViewBuilder.sourceWikiUrl(collection.name):collection.wikiUrl);card.add(header,BorderLayout.NORTH);
		if(closed) { return card; }
		if(collection.items.isEmpty()) { JLabel empty=label("No items reported",MUTED);empty.setBorder(BorderFactory.createEmptyBorder(5,6,7,6));card.add(empty,BorderLayout.CENTER); }
		else if(state.gridView) { card.add(sourceGrid(collection.items,true),BorderLayout.CENTER); }
		else
		{
			JPanel rows=new JPanel();rows.setOpaque(false);rows.setLayout(new javax.swing.BoxLayout(rows,javax.swing.BoxLayout.Y_AXIS));
			for(PanelState.Item item:collection.items) { rows.add(itemRow(item,true));if(item.key.equals(expanded)) { rows.add(collectionItemDetails(item)); } }card.add(rows,BorderLayout.CENTER);
		}
		return card;
	}
	private JPanel collectionItemDetails(PanelState.Item item)
	{
		JPanel details=new JPanel(new GridLayout(0,1));details.setOpaque(false);details.setBorder(BorderFactory.createEmptyBorder(5,8,7,6));
		if(item.occurrenceOnly) { stat(details,"Occurrences","x"+NUMBER.format(item.occurrences),item.rateTooltip,null); }
		else { stat(details,"Quantity",NUMBER.format(item.quantity),"Units in this recorded notification.",Color.YELLOW); }
		details.setAlignmentX(LEFT_ALIGNMENT);details.setMaximumSize(new Dimension(Integer.MAX_VALUE,details.getPreferredSize().height));return details;
	}
	private JPanel sourceBreakdown(PanelState.Item item)
	{
		JPanel details=new JPanel(new GridLayout(0,1,0,3));details.setOpaque(false);details.setBorder(BorderFactory.createEmptyBorder(5,8,8,4));details.add(label("Source breakdown",GOLD));
		if(item.occurrenceOnly) { for(PanelState.Source source:state.sources) { for(PanelState.Item row:source.items) { if(row.occurrenceOnly) { details.add(label(source.name+": x"+NUMBER.format(row.occurrences),MUTED)); } } } }
		for(PanelState.Source source:state.sources) { for(PanelState.Item row:source.items) { if(row.id==item.id&&row.quantity>0) { details.add(label("<html>"+source.name.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")+": <font color='#ffff00'>"+NUMBER.format(row.quantity)+"</font> · x"+NUMBER.format(row.occurrences)+" outcomes</html>",MUTED)); } } }
		details.setAlignmentX(LEFT_ALIGNMENT);details.setMaximumSize(new Dimension(Integer.MAX_VALUE,details.getPreferredSize().height));return details;
	}
	private JButton observationRow(PanelState.Item item,boolean combined)
	{
		JButton row=new EffectButton(item.quantity>0?state.itemEffect:ItemEffect.NONE,backgroundColor(item));row.setLayout(new BorderLayout(5,0));row.setBackground(ROW);row.setBorder(BorderFactory.createEmptyBorder(4,2,4,4));row.setAlignmentX(LEFT_ALIGNMENT);
		JLabel sprite=new JLabel(itemIcon(item));sprite.setPreferredSize(new Dimension(40,32));sprite.setHorizontalAlignment(JLabel.CENTER);row.add(sprite,BorderLayout.WEST);
		JPanel text=new JPanel(new GridLayout(0,1));text.setOpaque(false);text.add(label(item.name,Color.LIGHT_GRAY));if(combined&&!item.occurrenceOnly&&!state.stackCounts&&!state.iconQuantities) { text.add(label("x"+NUMBER.format(item.occurrences),MUTED)); }row.add(text,BorderLayout.CENTER);row.add(itemCounts(item),BorderLayout.EAST);row.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));row.getAccessibleContext().setAccessibleName(item.name+(item.occurrenceOnly?", occurrences "+item.occurrences:", quantity "+item.quantity));
		itemButtons.put(item.key,row);row.addActionListener(e->{expanded=item.key.equals(expanded)?null:item.key;render();JButton current=itemButtons.get(item.key);if(current!=null) { current.requestFocusInWindow(); }});makeRowClickable(row,row);
		addWikiMenu(row,itemWikiUrl(item));
		return row;
	}
	private JPanel itemRow(PanelState.Item item)
	{
		return itemRow(item,false);
	}
	private JPanel itemRow(PanelState.Item item,boolean collection)
	{
		JPanel holder=new JPanel(new BorderLayout());holder.setOpaque(false);holder.setAlignmentX(LEFT_ALIGNMENT);
		JButton button=new EffectButton(item.quantity>0?state.itemEffect:ItemEffect.NONE,backgroundColor(item));button.setLayout(new BorderLayout(5,0));button.setBackground(item.key.equals(expanded)?new Color(56,49,39):ROW);button.setBorder(BorderFactory.createMatteBorder(0,0,1,0,BG));
		JLabel icon=new JLabel(itemIcon(item));icon.setHorizontalAlignment(JLabel.CENTER);icon.setBorder(BorderFactory.createEmptyBorder(0,2,0,2));icon.setPreferredSize(new Dimension(40,36));if(item.id==0) { icon.setText("?"); }button.add(icon,BorderLayout.WEST);
		JPanel text=new JPanel(new GridLayout(0,1));text.setOpaque(false);text.setBorder(BorderFactory.createEmptyBorder(4,0,4,0));
		JLabel name=label(item.name+(!state.hideUnobtainedOtherWorld&&!item.worldMarker.isEmpty()?" "+item.worldMarker:""),item.quantity==0&&!item.occurrenceOnly?MUTED:Color.LIGHT_GRAY);text.add(name);
		if(!collection&&!item.occurrenceOnly)
		{
			java.util.Set<String> shownRates=new java.util.LinkedHashSet<>();
			if(state.showAllRates) { for(PanelState.RateOption option:item.rateOptions) { if(!option.historical&&shownRates.add(option.rate)) { JLabel rate=rateLabel(option.rate,option.color);text.add(rate); } } }
			String summary=item.rate.startsWith("Variants:")||item.rate.equals("Multiple published rates")?"Multiple published rates"+(item.rate.contains("*")?" *":""):item.rate;
			if(shownRates.isEmpty()) { JLabel rate=rateLabel(summary,item.rarity);text.add(rate); }
		}
		button.add(text,BorderLayout.CENTER);
		if(item.quantity>0||item.occurrenceOnly) { button.add(itemCounts(item),BorderLayout.EAST); }

		button.getAccessibleContext().setAccessibleName(item.name+(collection||item.occurrenceOnly?"":", "+item.rate)+(item.occurrenceOnly?", occurrences "+item.occurrences:", quantity "+item.quantity)+", "+(item.key.equals(expanded)?"expanded":"collapsed"));
		itemButtons.put(item.key,button);button.addActionListener(e->{expanded=item.key.equals(expanded)?null:item.key;render();JButton current=itemButtons.get(item.key);if(current!=null) { current.requestFocusInWindow(); }});makeRowClickable(button,button);addWikiMenu(button,itemWikiUrl(item));holder.add(button);holder.setMaximumSize(new Dimension(Integer.MAX_VALUE,Math.max(44,button.getPreferredSize().height)));return holder;
	}
	private static String itemWikiUrl(PanelState.Item item)
	{
		if(item.occurrenceOnly) { return "https://oldschool.runescape.wiki/w/Thieving#Pickpocketing"; }
		okhttp3.HttpUrl.Builder url=okhttp3.HttpUrl.get("https://oldschool.runescape.wiki/w/Special:Lookup").newBuilder().addQueryParameter("type","item").addQueryParameter("name",item.name);
		if(item.id>0) { url.addQueryParameter("id",Integer.toString(item.id)); }
		return url.build().toString();
	}
	private void addWikiMenu(javax.swing.JComponent component,String url)
	{
		javax.swing.JPopupMenu menu=new javax.swing.JPopupMenu();javax.swing.JMenuItem wiki=new javax.swing.JMenuItem("Open Wiki");
		wiki.addActionListener(event->openWiki.accept(url));menu.add(wiki);attachWikiMenu(component,menu);
	}
	private static void attachWikiMenu(javax.swing.JComponent component,javax.swing.JPopupMenu menu)
	{
		component.setComponentPopupMenu(menu);
		for(java.awt.Component child:component.getComponents()) { if(child instanceof javax.swing.JComponent) { attachWikiMenu((javax.swing.JComponent)child,menu); } }
	}
	static void makeRowClickable(java.awt.Container container,JButton button)
	{
		if(container==button)
		{
			button.setRolloverEnabled(true);
			button.addMouseListener(rowHover(button));
		}
		for(java.awt.Component child:container.getComponents())
		{
			child.addMouseListener(rowHover(button));
			child.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override public void mouseClicked(java.awt.event.MouseEvent event)
				{
					if(SwingUtilities.isLeftMouseButton(event)&&button.isEnabled()) { button.doClick(0); }
				}
			});
			if(child instanceof java.awt.Container) { makeRowClickable((java.awt.Container)child,button); }
		}
	}
	private static java.awt.event.MouseAdapter rowHover(JButton button)
	{
		return new java.awt.event.MouseAdapter()
		{
			@Override public void mouseEntered(java.awt.event.MouseEvent event)
			{
				button.getModel().setRollover(button.isEnabled());
			}
			@Override public void mouseExited(java.awt.event.MouseEvent event)
			{
				java.awt.Point point=SwingUtilities.convertPoint(event.getComponent(),event.getPoint(),button);
				button.getModel().setRollover(button.isEnabled()&&button.contains(point));
			}
		};
	}
	private JPanel details(PanelState.Item item)
	{
		if(item.occurrenceOnly) { JPanel failure=new JPanel(new GridLayout(0,1));failure.setOpaque(false);failure.setBorder(BorderFactory.createEmptyBorder(7,8,8,8));stat(failure,"Occurrences","x"+NUMBER.format(item.occurrences),item.rateTooltip,null);failure.setAlignmentX(LEFT_ALIGNMENT);failure.setMaximumSize(new Dimension(Integer.MAX_VALUE,failure.getPreferredSize().height));return failure; }
		JPanel wrap=new JPanel(new BorderLayout(0,4));wrap.setBackground(new Color(24,24,24));wrap.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,2,0,0,new Color(119,100,69)),BorderFactory.createEmptyBorder(7,8,8,8)));wrap.setAlignmentX(LEFT_ALIGNMENT);
		if(!item.worldMarker.isEmpty()) { wrap.add(label("(m)".equals(item.worldMarker)?"Members worlds only":"Free-to-play worlds only",MUTED),BorderLayout.NORTH); }
		JPanel grid=new JPanel(new GridLayout(0,1,0,6));grid.setOpaque(false);
		stat(grid,"Quantity",NUMBER.format(item.quantity),"All recorded units; separate from occurrences.",Color.YELLOW);
		stat(grid,"Occurrences","x"+NUMBER.format(item.occurrences),"Recorded notifications containing the item, including excluded/batched observations.",null);
		if(PanelState.LUCK_STATISTICS_VISIBLE)
		{
		String availability=item.attempts==null?"\nUnavailable; see item information.":"\nValidated recorded attempts; see item information for coverage.";
		stat(grid,"Attempts",item.attempts==null?"—":NUMBER.format(item.attempts),"Eligible individual attempts."+availability,null);
		stat(grid,"Per attempt",percent(item.probability),"Calculated chance of at least one item per kill/opening; the Wiki rate above may be per roll."+availability,null);
		stat(grid,"Observed",item.observed==null?"—":item.observed==0?"0%":"1/"+NUMBER.format(1/item.observed),"Occurrences ÷ eligible attempts."+availability,null);
		stat(grid,"Expected",value(item.expected),"Attempts × item-present probability."+availability,null);
		Color luck=!state.luckColors||item.ratio==null?null:item.ratio<.9?new Color(223,153,140):item.ratio>1.1?new Color(141,195,126):GOLD;
		stat(grid,"A/E",item.ratio==null?"—":value(item.ratio)+"×","Actual ÷ expected occurrences.\nColor does not indicate significance."+availability,luck);
		stat(grid,"Dry",item.dry==null?"—":NUMBER.format(item.dry),"Eligible attempts since the last drop\nor the start of this segment."+availability,null);
		stat(grid,"No drop",percent(item.noDrop),"Chance of this streak without a drop.\nNot the next-drop chance."+availability,null);
		stat(grid,"P(X ≥ k)",percent(item.upper),"Chance of this many drops or more."+availability,null);
		stat(grid,"P(X ≤ k)",percent(item.lower),"Chance of this many drops or fewer."+availability,null);
		}
		if(state.showGe) { stat(grid,"GE total",item.valueAvailable?NUMBER.format(item.ge)+" gp":"—","Current RuneLite GE price × recorded quantity; not historical sale proceeds.",null); }
		if(state.showHa) { stat(grid,"HA total",item.valueAvailable?NUMBER.format(item.ha)+" gp":"—","High Alchemy total before rune costs.",null); }
		JPanel body=new JPanel();body.setLayout(new javax.swing.BoxLayout(body,javax.swing.BoxLayout.Y_AXIS));body.setOpaque(false);body.add(grid);
		if(!item.rateOptions.isEmpty()||item.unresolvedOccurrences>0)
		{
			JPanel breakdown=new JPanel(new GridLayout(0,1,0,3));breakdown.setOpaque(false);breakdown.setBorder(BorderFactory.createEmptyBorder(8,0,2,0));
			java.util.Map<String,java.util.List<PanelState.RateOption>> tables=new java.util.LinkedHashMap<>();
			for(PanelState.RateOption option:item.rateOptions) { tables.computeIfAbsent(option.table+(option.historical?" (past)":""),key->new ArrayList<>()).add(option); }
			for(java.util.Map.Entry<String,java.util.List<PanelState.RateOption>> table:tables.entrySet())
			{
				breakdown.add(label(table.getKey(),GOLD));
				for(PanelState.RateOption option:table.getValue())
				{
					JPanel published=new JPanel(new GridLayout(1,2,2,0));published.setOpaque(false);published.add(label(option.dropQuantity==null?"—":option.dropQuantity,MUTED));
					JLabel rate=rateLabel(option.rate,option.color);published.add(rate);
					JLabel occurrences=label("x"+NUMBER.format(option.occurrences==null?0:option.occurrences),MUTED);occurrences.setHorizontalAlignment(JLabel.RIGHT);
					breakdown.add(detailCountRow(published,occurrences));
				}
			}
			if(item.unresolvedOccurrences>0)
			{
				JLabel occurrences=label("x"+NUMBER.format(item.unresolvedOccurrences),MUTED);occurrences.setHorizontalAlignment(JLabel.RIGHT);breakdown.add(detailCountRow(label("Unknown table",GOLD),occurrences));
			}
			body.add(breakdown);
		}
		else { body.add(label("Unknown table",GOLD)); }
		wrap.add(body);
		JPanel footer=new JPanel(new BorderLayout(4,0));footer.setOpaque(false);
		if(PanelState.LUCK_STATISTICS_VISIBLE) { footer.add(label(item.attempts==null?"Model pending":"Validated segment",MUTED),BorderLayout.CENTER); }
		JButton information=new JButton("i");information.setFont(new java.awt.Font("SansSerif",java.awt.Font.BOLD,12));information.setForeground(MUTED);information.setBackground(ROW);information.setPreferredSize(new Dimension(22,22));information.setMargin(new java.awt.Insets(0,0,0,0));information.getAccessibleContext().setAccessibleName("Item information: "+item.name);
		information.addActionListener(e->showInformation(information,item));footer.add(information,BorderLayout.EAST);wrap.add(footer,BorderLayout.SOUTH);
		wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE,wrap.getPreferredSize().height));return wrap;
	}
	private JPanel itemCounts(PanelState.Item item)
	{
		JPanel counts=new JPanel(new GridLayout(0,1,0,1));counts.setOpaque(false);counts.setBorder(BorderFactory.createEmptyBorder(0,0,0,4));
		if(item.occurrenceOnly) { JLabel occurrences=label("x"+NUMBER.format(item.occurrences),MUTED);occurrences.setHorizontalAlignment(JLabel.RIGHT);counts.add(occurrences);return counts; }
		String quantity=state.stackCounts?CountFormat.compact(item.quantity):NUMBER.format(state.iconQuantities?item.occurrences:item.quantity);
		boolean occurrencesOnRight=state.iconQuantities&&!state.stackCounts;
		JLabel primary=label((occurrencesOnRight?"x":"")+quantity,occurrencesOnRight?MUTED:Color.YELLOW);primary.setHorizontalAlignment(JLabel.RIGHT);counts.add(primary);
		if(state.stackCounts&&item.occurrences>0&&item.quantity>item.occurrences) { JLabel occurrences=label("x"+NUMBER.format(item.occurrences),MUTED);occurrences.setHorizontalAlignment(JLabel.RIGHT);counts.add(occurrences); }
		return counts;
	}
	private void showInformation(JButton anchor,PanelState.Item item)
	{
		javax.swing.JPopupMenu popup=new ItemInformationPopup(item);popup.show(anchor,anchor.getWidth()-popup.getPreferredSize().width,anchor.getHeight());
	}
	private void stat(JPanel grid,String name,String value,String tip,Color color)
	{
		JLabel number=label(value,color==null?Color.LIGHT_GRAY:color);number.setHorizontalAlignment(JLabel.RIGHT);grid.add(detailCountRow(label(name,MUTED),number));
	}
	private JPanel detailCountRow(javax.swing.JComponent description,JLabel count)
	{
		// Details span the sidebar minus the outer panel and disclosure borders.
		// Fall back to two lines only when the complete label/value pair cannot fit.
		JPanel row=new JPanel(new BorderLayout(4,1));row.setOpaque(false);
		int available=(getWidth()>0?getWidth():230)-getInsets().left-getInsets().right-18;
		boolean large=description.getPreferredSize().width+4+count.getPreferredSize().width>available;
		row.add(description,large?BorderLayout.NORTH:BorderLayout.CENTER);row.add(count,large?BorderLayout.SOUTH:BorderLayout.EAST);return row;
	}
	private static JLabel label(String text,Color color) { JLabel l=new JLabel(text);l.setAlignmentX(LEFT_ALIGNMENT);l.setFont(FontManager.getRunescapeSmallFont());l.setForeground(color);return l; }
	private static JLabel rateLabel(String text,Color color)
	{
		if(text.endsWith(" *")) { String rate=text.substring(0,text.length()-2).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");return label("<html>"+rate+" <font color='#ffa3ff'>*</font></html>",color); }
		return label(text,"Varies".equals(text)||"*".equals(text)?com.lootluck.rates.RateRow.VARIES_COLOR:color);
	}
	private int sortMode() { int index=sort.getSelectedIndex();return !PanelState.LUCK_STATISTICS_VISIBLE&&index>=2?index+1:index; }
	private static String value(Double value) { return value==null?"—":DECIMAL.format(value); }
	private static String percent(Double p) { return p==null?"—":new DecimalFormat("0.####",DecimalFormatSymbols.getInstance(Locale.US)).format(p*100)+"%"; }
	private static String date(long time) { return time==0?"Never observed":DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time)); }
	public static String money(long value) { return CountFormat.compact(value)+" gp"; }
}





