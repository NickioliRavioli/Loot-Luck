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
	private LootLuckSettingsPanel configSettings;
	private final JComboBox<String> scope=new JComboBox<>(new String[]{"Lifetime","Session"});
	private final JComboBox<String> sort=new JComboBox<>(new String[]{"Most recent","Rarest first","Driest first","Commonest first","Wiki order"});
	private final java.util.function.BiFunction<Integer,Long,Icon> icons;
	private final JComboBox<String> view=new JComboBox<>(new String[]{"By source","Recent collections","All items"});
	private final JComboBox<String> itemSort=new JComboBox<>(new String[]{"Most recent","Most occurrences","Most quantity"});
	private final JPanel controls=new JPanel(new GridLayout(1,2,4,0));
	private final JButton dataSettings=new JButton("Data settings");
	private Consumer<String> dataAction=action->{};
	private Consumer<String> validationAction=source->{};
	private int historyPage;
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
		JButton settingsButton=new JButton("⚙");settingsButton.setFont(new java.awt.Font("Dialog",java.awt.Font.PLAIN,18));settingsButton.getAccessibleContext().setAccessibleName("Settings");top.add(heading);
		JPanel headerActions=new JPanel(new GridLayout(1,4,2,0));headerActions.setOpaque(false);headerActions.add(toggleUnobtained);headerActions.add(toggleGrid);headerActions.add(toggleSources);headerActions.add(settingsButton);heading.add(headerActions,BorderLayout.EAST);
		toggleGrid.setFont(new java.awt.Font("Dialog",java.awt.Font.PLAIN,16));toggleGrid.setMargin(new java.awt.Insets(2,4,2,4));
		toggleGrid.addActionListener(e->{state.gridView=!state.gridView;gridChanged.accept(state.gridView);render();});
		toggleSources.addActionListener(e->{boolean collapse=state.sources.stream().anyMatch(source->!collapsed.contains(source.key));for(PanelState.Source source:state.sourceRows()) { if(collapse) { collapsed.add(source.key); }else { collapsed.remove(source.key); } }render();});
		settings.setOpaque(false);JButton back=new JButton("← Back to loot");settings.add(back);settings.add(label("Settings",GOLD));
		settingsButton.addActionListener(e->showSettings(true));back.addActionListener(e->showSettings(false));
		top.add(view);
		controls.setOpaque(false);controls.add(scope);controls.add(sort);top.add(controls);
		JPanel actions=new JPanel(new GridLayout(1,2,4,0));actions.setOpaque(false);
		JButton refreshButton=new JButton("Refresh"),resetButton=new JButton("Reset session");resetButton.setText("Reset");
		actions.add(refreshButton);actions.add(resetButton);settings.add(actions);add(top,BorderLayout.NORTH);
		settings.add(dataSettings);dataSettings.addActionListener(e->{
			javax.swing.JPopupMenu menu=new javax.swing.JPopupMenu();
			for(String action:new String[]{"Export data","Import data","Delete data"}) { javax.swing.JMenuItem entry=new javax.swing.JMenuItem(action);entry.addActionListener(event->dataAction.accept(action));menu.add(entry); }
			menu.show(dataSettings,0,dataSettings.getHeight());
		});
		content.setLayout(new javax.swing.BoxLayout(content,javax.swing.BoxLayout.Y_AXIS));content.setOpaque(false);add(content,BorderLayout.CENTER);
		scope.addActionListener(e->scopeChanged.accept(session()));sort.addActionListener(e->{scopeChanged.accept(session());render();});
		refreshButton.addActionListener(e->refresh.run());resetButton.addActionListener(e->reset.run());
		view.addActionListener(e->{historyPage=0;controls.removeAll();controls.setLayout(new GridLayout(1,view.getSelectedIndex()==1?1:2,4,0));controls.add(scope);if(view.getSelectedIndex()==0) { controls.add(sort); }else if(view.getSelectedIndex()==2) { controls.add(itemSort); }controls.revalidate();controls.repaint();render();});itemSort.addActionListener(e->render());
	}
	private static Icon eyeIcon(boolean slashed)
	{
		return new Icon() {
			public int getIconWidth() { return 18; }
			public int getIconHeight() { return 18; }
			public void paintIcon(java.awt.Component component,java.awt.Graphics graphics,int x,int y)
			{
				java.awt.Graphics2D g=(java.awt.Graphics2D)graphics.create();try { g.translate(x,y);g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(component.isEnabled()?GOLD:MUTED);g.setStroke(new java.awt.BasicStroke(1.5f));java.awt.geom.Path2D eye=new java.awt.geom.Path2D.Double();eye.moveTo(1,9);eye.curveTo(5,2,13,2,17,9);eye.curveTo(13,16,5,16,1,9);g.draw(eye);g.fillOval(7,7,4,4);if(slashed) { g.setStroke(new java.awt.BasicStroke(2));g.drawLine(2,2,16,16); } }finally { g.dispose(); }
			}
		};
	}
	public boolean session() { return scope.getSelectedIndex()==1; }
	public void setSettings(com.lootluck.LootLuckConfig config,java.util.function.BiConsumer<String,Object> changed,java.util.function.Function<Boolean,Icon> previewIcons)
	{
		state.includeUnobtained=config.includeUnobtained();state.itemEffect=config.itemEffect();state.gridView=config.gridView();gridChanged=value->changed.accept("gridView",value);
		configSettings=new LootLuckSettingsPanel(config,changed,previewIcons);settings.add(configSettings);settings.setLayout(new javax.swing.BoxLayout(settings,javax.swing.BoxLayout.Y_AXIS));
		for(java.awt.Component child:settings.getComponents()) { if(child instanceof javax.swing.JComponent) { ((javax.swing.JComponent)child).setAlignmentX(LEFT_ALIGNMENT);((javax.swing.JComponent)child).setMaximumSize(new Dimension(Integer.MAX_VALUE,child.getPreferredSize().height)); } }
	}
	static JButton stylePreview(ItemDisplayStyle style,Icon icon)
	{
		LootLuckPanel preview=new LootLuckPanel(id->null,value->{},value->{},()->{},()->{});
		preview.state.iconQuantities=style.iconQuantities;preview.state.stackCounts=style.stackCounts;
		PanelState.Item item=new PanelState.Item();item.key="preview";item.name="Coins";item.rate="1/128";item.quantity=12500;item.occurrences=7;item.icon=icon;item.rarity=GOLD;
		JPanel row=preview.itemRow(item);JButton button=(JButton)row.getComponent(0);
		for(java.awt.event.ActionListener listener:button.getActionListeners()) { button.removeActionListener(listener); }
		row.remove(button);button.setAlignmentX(LEFT_ALIGNMENT);button.setMaximumSize(new Dimension(Integer.MAX_VALUE,58));button.setPreferredSize(new Dimension(210,58));
		button.getAccessibleContext().setAccessibleName(style.title);return button;
	}
	private void showSettings(boolean visible)
	{
		if(configSettings!=null) { configSettings.sync(); }
		removeAll();add(visible?settings:top,BorderLayout.NORTH);if(!visible) { add(content,BorderLayout.CENTER); }revalidate();repaint();
	}
	public void setDataAction(Consumer<String> action) { dataAction=action; }
	public void setValidationAction(Consumer<String> action) { validationAction=action; }
	public boolean driest() { return PanelState.LUCK_STATISTICS_VISIBLE&&sortMode()==2; }
	public void update(PanelState state) { if(!SwingUtilities.isEventDispatchThread()) { throw new IllegalStateException("Swing thread required"); } this.state=state;render(); }
	private void render()
	{
		content.removeAll();itemButtons.clear();
		boolean showingUnobtained=state.includeUnobtained||driest();toggleUnobtained.setIcon(eyeIcon(!showingUnobtained));toggleUnobtained.setSelected(showingUnobtained);toggleUnobtained.setEnabled(view.getSelectedIndex()==0&&!driest());toggleUnobtained.getAccessibleContext().setAccessibleDescription(showingUnobtained?"Unobtained items shown":"Unobtained items hidden");
		toggleGrid.setText(state.gridView?"☰":"▦");toggleGrid.getAccessibleContext().setAccessibleName(state.gridView?"Switch to list view":"Switch to grid view");toggleGrid.setEnabled(view.getSelectedIndex()==0);
		boolean anyExpanded=state.sources.stream().anyMatch(source->!collapsed.contains(source.key));toggleSources.setText(anyExpanded?"▴":"▾");toggleSources.getAccessibleContext().setAccessibleName(anyExpanded?"Collapse all sources":"Expand all sources");toggleSources.setEnabled(view.getSelectedIndex()==0&&!state.sources.isEmpty());
		dataSettings.setEnabled(state.canManageData);
		if(configSettings!=null) { configSettings.sync(); }
		if(!state.notice.isEmpty()) { JLabel notice=label(state.notice,MUTED);content.add(notice); }
		if(view.getSelectedIndex()!=0) { renderObservations();content.revalidate();content.repaint();return; }
		for(PanelState.Source source:state.sources) { content.add(sourceCard(source));content.add(javax.swing.Box.createVerticalStrut(5)); }
		if(state.sources.isEmpty()) { JLabel empty=label("No loot recorded in this view.",MUTED);empty.setBorder(BorderFactory.createEmptyBorder(12,0,12,0));content.add(empty); }
		JLabel status=label(state.dataStatus,MUTED);content.add(status);content.revalidate();content.repaint();
	}
	private JPanel sourceCard(PanelState.Source source)
	{
			JPanel group=new JPanel(new BorderLayout()) { @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE,getPreferredSize().height); } };group.setOpaque(false);group.setAlignmentX(LEFT_ALIGNMENT);
			JButton header=new JButton();header.setLayout(new BorderLayout(4,0));header.setBackground(new Color(16,16,16));header.setBorder(BorderFactory.createEmptyBorder(8,4,8,4));
			header.add(label((collapsed.contains(source.key)?"▸ ":"▾ ")+source.name,GOLD),BorderLayout.WEST);
			header.add(label("x"+NUMBER.format(source.count),MUTED),BorderLayout.CENTER);
			if(state.showGe) { header.add(label(source.value==null?"— gp":source.value,MUTED),BorderLayout.EAST); }
			header.getAccessibleContext().setAccessibleName(source.name+", "+source.count+" recorded outcomes, "+(collapsed.contains(source.key)?"collapsed":"expanded"));
			header.addActionListener(e->{if(!collapsed.add(source.key)) { collapsed.remove(source.key); }render();});
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
		final int columns=5;int rowCount=(items.size()+columns-1)/columns;
		JPanel group=new JPanel(new BorderLayout(0,4));group.setOpaque(false);
		VirtualItemList grid=new VirtualItemList(rowCount,columns,index->{
			JPanel row=new JPanel(null) {
				@Override public void doLayout() { int side=Math.max(1,(getWidth()-2*(columns-1))/columns);int left=(getWidth()-(side*columns+2*(columns-1)))/2;for(int i=0;i<getComponentCount();i++) { getComponent(i).setBounds(left+i*(side+2),0,side,side); } }
			};row.setOpaque(false);
			for(int column=0;column<columns;column++) { int position=index*columns+column;if(position<items.size()) { row.add(gridItem(items.get(position))); }else { JPanel space=new JPanel();space.setOpaque(false);row.add(space); } }return row;
		},index->{for(int column=0;column<columns&&index*columns+column<items.size();column++) { itemButtons.remove(items.get(index*columns+column).key); }});
		group.add(grid,BorderLayout.CENTER);
		items.stream().filter(item->item.key.equals(expanded)).findFirst().ifPresent(item->{
			JPanel disclosure=new JPanel(new BorderLayout(0,3));disclosure.setOpaque(false);JPanel title=new JPanel(new GridLayout(0,1));title.setOpaque(false);title.add(label(item.name+(!state.hideUnobtainedOtherWorld&&!item.worldMarker.isEmpty()?" "+item.worldMarker:""),GOLD));title.add(rateLabel(item.rate,item.rarity));disclosure.add(title,BorderLayout.NORTH);disclosure.add(details(item),BorderLayout.CENTER);group.add(disclosure,BorderLayout.SOUTH);
			if(item.key.equals(gridReveal)) { gridReveal=null;SwingUtilities.invokeLater(()->{if(disclosure.isShowing()) { disclosure.scrollRectToVisible(new java.awt.Rectangle(0,0,disclosure.getWidth(),disclosure.getHeight())); }}); }
		});
		return group;
	}
	static Color effectColor(PanelState.Item item)
	{
		return "Varies".equals(item.rate)?new Color(160,160,160):item.rarity;
	}
	private javax.swing.Icon itemIcon(PanelState.Item item)
	{
		javax.swing.Icon sprite=item.icon!=null?item.icon:item.id>0?icons.apply(item.id,item.quantity):null;
		if(sprite==null||item.quantity>0) { return sprite; }
		// Paint the live icon rather than caching a disabled copy of an asynchronous image.
		return new javax.swing.Icon()
		{
			public int getIconWidth() { return sprite.getIconWidth(); }
			public int getIconHeight() { return sprite.getIconHeight(); }
			public void paintIcon(java.awt.Component component,java.awt.Graphics graphics,int x,int y)
			{
				java.awt.Graphics2D faded=(java.awt.Graphics2D)graphics.create();
				try { faded.setComposite(java.awt.AlphaComposite.SrcOver.derive(0.65f));sprite.paintIcon(component,faded,x,y); }
				finally { faded.dispose(); }
			}
		};
	}
	private JButton gridItem(PanelState.Item item)
	{
		JButton button=new EffectButton(item.quantity>0?state.itemEffect:ItemEffect.NONE,effectColor(item)) {
			@Override public void doLayout() { for(java.awt.Component child:getComponents()) { if("count".equals(child.getName())) { child.setBounds(2,getHeight()-14,getWidth()-4,12); }else { child.setBounds(2,2,getWidth()-4,getHeight()-4); } } }
		};button.setLayout(null);button.setBackground(ROW);button.setMargin(new java.awt.Insets(0,0,0,0));
		button.setBorder(BorderFactory.createLineBorder(item.key.equals(expanded)?GOLD:BG,2));
		JLabel icon=new JLabel(itemIcon(item));icon.setHorizontalAlignment(JLabel.CENTER);if(item.id==0) { icon.setText("?"); }button.add(icon,BorderLayout.CENTER);
		JLabel quantity=label(net.runelite.client.util.QuantityFormatter.quantityToStackSize(item.quantity),item.quantity==0?MUTED:Color.YELLOW);quantity.setName("count");quantity.setHorizontalAlignment(JLabel.RIGHT);if(!state.iconQuantities&&item.quantity>0) { button.add(quantity,BorderLayout.SOUTH); }
		button.getAccessibleContext().setAccessibleName(item.name+", quantity "+item.quantity);button.getAccessibleContext().setAccessibleDescription(item.rate+(item.key.equals(expanded)?", expanded":", collapsed"));
		itemButtons.put(item.key,button);button.addActionListener(e->{expanded=item.key.equals(expanded)?null:item.key;gridReveal=expanded;render();});makeRowClickable(button,button);return button;
	}
	private void renderObservations()
	{
		if(view.getSelectedIndex()==1)
		{
			int pages=Math.max(1,(state.collections.size()+49)/50);historyPage=Math.min(historyPage,pages-1);
			JPanel paging=new JPanel(new BorderLayout(4,0));paging.setOpaque(false);paging.setAlignmentX(LEFT_ALIGNMENT);JButton previous=new JButton("‹"),next=new JButton("›");previous.setEnabled(historyPage>0);next.setEnabled(historyPage+1<pages);previous.addActionListener(e->{historyPage--;render();});next.addActionListener(e->{historyPage++;render();});paging.add(previous,BorderLayout.WEST);paging.add(label("Page "+(historyPage+1)+" / "+pages,MUTED),BorderLayout.CENTER);paging.add(next,BorderLayout.EAST);paging.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));content.add(paging);
			if(state.collections.isEmpty()) { content.add(label("No collection history yet.",MUTED));content.add(label("History starts with new loot notifications.",MUTED)); }
			for(int index=historyPage*50;index<Math.min(state.collections.size(),(historyPage+1)*50);index++)
			{
				PanelState.Source collection=state.collections.get(index);JLabel heading=label(collection.name,GOLD);heading.setBorder(BorderFactory.createEmptyBorder(8,4,2,4));content.add(heading);content.add(label(date(collection.lastReceived),MUTED));
				if(collection.items.isEmpty()) { content.add(label("No items reported",MUTED)); }
				for(PanelState.Item item:collection.items) { content.add(observationRow(item,false)); }
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
	private JPanel sourceBreakdown(PanelState.Item item)
	{
		JPanel details=new JPanel(new GridLayout(0,1,0,3));details.setOpaque(false);details.setBorder(BorderFactory.createEmptyBorder(5,8,8,4));details.add(label("Source breakdown",GOLD));
		for(PanelState.Source source:state.sources) { for(PanelState.Item row:source.items) { if(row.id==item.id&&row.quantity>0) { details.add(label(source.name+": x"+NUMBER.format(row.quantity)+" · "+NUMBER.format(row.occurrences)+" outcomes",MUTED)); } } }
		details.setAlignmentX(LEFT_ALIGNMENT);details.setMaximumSize(new Dimension(Integer.MAX_VALUE,details.getPreferredSize().height));return details;
	}
	private JButton observationRow(PanelState.Item item,boolean combined)
	{
		JButton row=new EffectButton(item.quantity>0?state.itemEffect:ItemEffect.NONE,effectColor(item));row.setLayout(new BorderLayout(5,0));row.setBackground(ROW);row.setBorder(BorderFactory.createEmptyBorder(4,2,4,4));row.setAlignmentX(LEFT_ALIGNMENT);
		JLabel sprite=new JLabel(item.id>0?icons.apply(item.id,item.quantity):null);sprite.setPreferredSize(new Dimension(40,32));sprite.setHorizontalAlignment(JLabel.CENTER);row.add(sprite,BorderLayout.WEST);
		JPanel text=new JPanel(new GridLayout(0,1));text.setOpaque(false);text.add(label(item.name,Color.LIGHT_GRAY));if(combined&&!state.stackCounts) { text.add(label(NUMBER.format(item.occurrences)+" occurrences",MUTED)); }row.add(text,BorderLayout.CENTER);row.add(itemCounts(item),BorderLayout.EAST);row.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));row.getAccessibleContext().setAccessibleName(item.name+", quantity "+item.quantity);
		if(combined) { itemButtons.put(item.key,row);row.addActionListener(e->{expanded=item.key.equals(expanded)?null:item.key;render();JButton current=itemButtons.get(item.key);if(current!=null) { current.requestFocusInWindow(); }});makeRowClickable(row,row); }
		return row;
	}
	private JPanel itemRow(PanelState.Item item)
	{
		JPanel holder=new JPanel(new BorderLayout());holder.setOpaque(false);holder.setAlignmentX(LEFT_ALIGNMENT);
		JButton button=new EffectButton(item.quantity>0?state.itemEffect:ItemEffect.NONE,effectColor(item));button.setLayout(new BorderLayout(5,0));button.setBackground(item.key.equals(expanded)?new Color(56,49,39):ROW);button.setBorder(BorderFactory.createMatteBorder(0,0,1,0,BG));
		JLabel icon=new JLabel(itemIcon(item));icon.setHorizontalAlignment(JLabel.CENTER);icon.setBorder(BorderFactory.createEmptyBorder(0,2,0,2));icon.setPreferredSize(new Dimension(40,36));if(item.id==0) { icon.setText("?"); }button.add(icon,BorderLayout.WEST);
		JPanel text=new JPanel(new GridLayout(0,1));text.setOpaque(false);text.setBorder(BorderFactory.createEmptyBorder(4,0,4,0));
		JLabel name=label(item.name+(!state.hideUnobtainedOtherWorld&&!item.worldMarker.isEmpty()?" "+item.worldMarker:""),item.quantity==0?MUTED:Color.LIGHT_GRAY);text.add(name);
		java.util.Set<String> shownRates=new java.util.LinkedHashSet<>();
		if(state.showAllRates) { for(PanelState.RateOption option:item.rateOptions) { if(!option.historical&&shownRates.add(option.rate)) { JLabel rate=rateLabel(option.rate,option.color);text.add(rate); } } }
		String summary=item.rate.startsWith("Variants:")||item.rate.equals("Multiple published rates")?"Multiple published rates"+(item.rate.contains("*")?" *":""):item.rate;
		if(shownRates.isEmpty()) { JLabel rate=rateLabel(summary,item.rarity);text.add(rate); }button.add(text,BorderLayout.CENTER);
		if(item.quantity>0) { button.add(itemCounts(item),BorderLayout.EAST); }

		button.getAccessibleContext().setAccessibleName(item.name+", "+item.rate+", quantity "+item.quantity+", "+(item.key.equals(expanded)?"expanded":"collapsed"));
		itemButtons.put(item.key,button);button.addActionListener(e->{expanded=item.key.equals(expanded)?null:item.key;render();JButton current=itemButtons.get(item.key);if(current!=null) { current.requestFocusInWindow(); }});makeRowClickable(button,button);holder.add(button);holder.setMaximumSize(new Dimension(Integer.MAX_VALUE,Math.max(44,button.getPreferredSize().height)));return holder;
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
		JPanel wrap=new JPanel(new BorderLayout(0,4));wrap.setBackground(new Color(24,24,24));wrap.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,2,0,0,new Color(119,100,69)),BorderFactory.createEmptyBorder(7,8,8,8)));wrap.setAlignmentX(LEFT_ALIGNMENT);
		if(!item.worldMarker.isEmpty()) { wrap.add(label("(m)".equals(item.worldMarker)?"Members worlds only":"Free-to-play worlds only",MUTED),BorderLayout.NORTH); }
		JPanel grid=new JPanel(new GridLayout(0,2,8,4));grid.setOpaque(false);
		stat(grid,"Quantity",NUMBER.format(item.quantity),"All recorded units; separate from occurrences.",null);
		stat(grid,"Occurrences",NUMBER.format(item.occurrences),"Recorded notifications containing the item, including excluded/batched observations.",null);
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
		if(state.showGe) { stat(grid,"GE total",item.valueAvailable?money(item.ge):"—","Current RuneLite GE price × recorded quantity; not historical sale proceeds.",null); }
		if(state.showHa) { stat(grid,"HA total",item.valueAvailable?money(item.ha):"—","High Alchemy total before rune costs.",null); }
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
					JPanel line=new JPanel(new GridLayout(1,4,2,0));line.setOpaque(false);line.add(label(option.dropQuantity==null?"—":option.dropQuantity,MUTED));
					JLabel rate=rateLabel(option.rate,option.color);line.add(rate);
					JLabel quantity=label("x"+NUMBER.format(option.quantity==null?0:option.quantity),Color.YELLOW);quantity.setHorizontalAlignment(JLabel.RIGHT);line.add(quantity);breakdown.add(line);
				}
			}
			if(item.unresolvedOccurrences>0) { JLabel unknown=label("Unknown table    x"+NUMBER.format(item.unresolvedQuantity),MUTED);breakdown.add(unknown); }
			body.add(breakdown);
		}
		else { body.add(label("Unknown table",MUTED)); }
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
		String quantity=state.stackCounts?net.runelite.client.util.QuantityFormatter.quantityToStackSize(item.quantity):NUMBER.format(state.iconQuantities?item.occurrences:item.quantity);
		JLabel primary=label("x"+quantity,state.stackCounts?Color.YELLOW:Color.LIGHT_GRAY);primary.setHorizontalAlignment(JLabel.RIGHT);counts.add(primary);
		if(state.stackCounts&&item.occurrences>0&&item.quantity>item.occurrences) { JLabel occurrences=label(NUMBER.format(item.occurrences)+" occ.",MUTED);occurrences.setHorizontalAlignment(JLabel.RIGHT);counts.add(occurrences); }
		return counts;
	}
	private void showInformation(JButton anchor,PanelState.Item item)
	{
		javax.swing.JTextArea text=new javax.swing.JTextArea(item.name+"\n\n"+item.rateTooltip+(PanelState.LUCK_STATISTICS_VISIBLE?"\n\nStatistics\n"+item.reason:""));
		text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,12));text.setForeground(Color.LIGHT_GRAY);text.setBackground(ROW);text.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));text.setCaretPosition(0);
		javax.swing.JScrollPane scroll=new javax.swing.JScrollPane(text);scroll.setPreferredSize(new Dimension(300,300));scroll.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		javax.swing.JPopupMenu popup=new javax.swing.JPopupMenu();popup.setLayout(new BorderLayout());popup.add(scroll);popup.show(anchor,anchor.getWidth()-300,anchor.getHeight());text.requestFocusInWindow();
	}
	private void stat(JPanel grid,String name,String value,String tip,Color color)
	{
		JPanel cell=new JPanel(new BorderLayout(3,0));cell.setOpaque(false);JLabel label=label(name,MUTED),number=label(value,color==null?Color.LIGHT_GRAY:color);cell.add(label,BorderLayout.CENTER);cell.add(number,BorderLayout.EAST);grid.add(cell);
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
	private static String date(long time) { return time==0?"Never observed":DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time)); }
	public static String money(long value) { return value>=1_000_000?NUMBER.format(value/1_000_000.0)+"M gp":value>=1000?NUMBER.format(value/1000.0)+"k gp":NUMBER.format(value)+" gp"; }
}





