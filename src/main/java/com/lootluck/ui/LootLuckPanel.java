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
	private final JComboBox<String> sort=new JComboBox<>(new String[]{"Most recent","Rarest first","Driest first"});
	private final java.util.function.BiFunction<Integer,Long,Icon> icons;
	private final JComboBox<String> view=new JComboBox<>(new String[]{"By source","Recent collections","All items"});
	private final JComboBox<String> itemSort=new JComboBox<>(new String[]{"Most recent","Most occurrences","Most quantity"});
	private final JPanel controls=new JPanel(new GridLayout(1,2,4,0));
	private final JButton dataSettings=new JButton("Data settings");
	private Consumer<String> dataAction=action->{};
	private int historyPage;
	private final Consumer<Boolean> scopeChanged;
	private final Consumer<String> openWiki;
	private final Set<String> collapsed=new HashSet<>();
	private PanelState state=new PanelState();
	private String expanded;
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
		setLayout(new BorderLayout(0,5));setBackground(BG);setBorder(BorderFactory.createEmptyBorder(7,6,7,6));
		top.setOpaque(false);
		JPanel heading=new JPanel(new BorderLayout());heading.setOpaque(false);
		JLabel title=new JLabel("Loot Luck");title.setForeground(GOLD);title.setFont(FontManager.getRunescapeFont());heading.add(title,BorderLayout.CENTER);
		JButton settingsButton=new JButton("⚙");settingsButton.setFont(new java.awt.Font("Dialog",java.awt.Font.PLAIN,18));settingsButton.setToolTipText("Settings");settingsButton.getAccessibleContext().setAccessibleName("Settings");heading.add(settingsButton,BorderLayout.EAST);top.add(heading);
		settings.setOpaque(false);JButton back=new JButton("← Back to loot");settings.add(back);settings.add(label("Settings",GOLD));
		settingsButton.addActionListener(e->showSettings(true));back.addActionListener(e->showSettings(false));
		top.add(view);
		controls.setOpaque(false);controls.add(scope);controls.add(sort);top.add(controls);
		JPanel actions=new JPanel(new GridLayout(1,2,4,0));actions.setOpaque(false);
		JButton refreshButton=new JButton("Refresh"),resetButton=new JButton("Reset session");refreshButton.setToolTipText("Opt-in Wiki refresh; enable in Loot Luck configuration first.");resetButton.setText("Reset");resetButton.setToolTipText("Reset session counters only; lifetime observations remain.");
		actions.add(refreshButton);actions.add(resetButton);settings.add(actions);add(top,BorderLayout.NORTH);
		dataSettings.setToolTipText("Export, import or delete this account's Loot Luck observations.");settings.add(dataSettings);dataSettings.addActionListener(e->{
			javax.swing.JPopupMenu menu=new javax.swing.JPopupMenu();
			for(String action:new String[]{"Export data","Import data","Delete data"}) { javax.swing.JMenuItem entry=new javax.swing.JMenuItem(action);entry.addActionListener(event->dataAction.accept(action));menu.add(entry); }
			menu.show(dataSettings,0,dataSettings.getHeight());
		});
		content.setLayout(new javax.swing.BoxLayout(content,javax.swing.BoxLayout.Y_AXIS));content.setOpaque(false);add(content,BorderLayout.CENTER);
		scope.addActionListener(e->scopeChanged.accept(session()));sort.addActionListener(e->{scopeChanged.accept(session());render();});
		refreshButton.addActionListener(e->refresh.run());resetButton.addActionListener(e->reset.run());
		view.addActionListener(e->{historyPage=0;controls.removeAll();controls.setLayout(new GridLayout(1,view.getSelectedIndex()==1?1:2,4,0));controls.add(scope);if(view.getSelectedIndex()==0) { controls.add(sort); }else if(view.getSelectedIndex()==2) { controls.add(itemSort); }controls.revalidate();controls.repaint();render();});itemSort.addActionListener(e->render());
	}
	public boolean session() { return scope.getSelectedIndex()==1; }
	public void setSettings(com.lootluck.LootLuckConfig config,java.util.function.BiConsumer<String,Object> changed,java.util.function.Function<Boolean,Icon> previewIcons)
	{
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
		button.setToolTipText(style.title);button.getAccessibleContext().setAccessibleName(style.title);return button;
	}
	private void showSettings(boolean visible)
	{
		if(configSettings!=null) { configSettings.sync(); }
		removeAll();add(visible?settings:top,BorderLayout.NORTH);if(!visible) { add(content,BorderLayout.CENTER); }revalidate();repaint();
	}
	public void setDataAction(Consumer<String> action) { dataAction=action; }
	public boolean driest() { return sort.getSelectedIndex()==2; }
	public void update(PanelState state) { if(!SwingUtilities.isEventDispatchThread()) { throw new IllegalStateException("Swing thread required"); } this.state=state;render(); }
	private void render()
	{
		content.removeAll();itemButtons.clear();
		dataSettings.setEnabled(state.canManageData);
		if(configSettings!=null) { configSettings.sync(); }
		if(!state.notice.isEmpty()) { JLabel notice=label(state.notice,MUTED);notice.setToolTipText(state.notice);content.add(notice); }
		if(view.getSelectedIndex()!=0) { renderObservations();content.revalidate();content.repaint();return; }
		for(PanelState.Source source:state.sources)
		{
			JPanel group=new JPanel(new BorderLayout());group.setOpaque(false);group.setAlignmentX(LEFT_ALIGNMENT);
			JButton header=new JButton();header.setLayout(new BorderLayout(4,0));header.setBackground(new Color(16,16,16));header.setBorder(BorderFactory.createEmptyBorder(8,4,8,4));
			header.add(label((collapsed.contains(source.key)?"▸ ":"▾ ")+source.name,GOLD),BorderLayout.WEST);
			header.add(label("x"+NUMBER.format(source.count),MUTED),BorderLayout.CENTER);
			if(state.showGe) { header.add(label(source.value==null?"— gp":source.value,MUTED),BorderLayout.EAST); }
			header.setToolTipText(source.countTooltip);header.getAccessibleContext().setAccessibleName(source.name+", "+source.count+" recorded outcomes, "+(collapsed.contains(source.key)?"collapsed":"expanded"));
			header.addActionListener(e->{if(!collapsed.add(source.key)) { collapsed.remove(source.key); }render();});
			JPanel heading=new JPanel(new BorderLayout(2,0));heading.setBackground(new Color(16,16,16));heading.add(header,BorderLayout.CENTER);
			if(state.showWikiLinks&&source.wikiUrl!=null)
			{
				JButton wiki=new JButton("W");wiki.setFont(new java.awt.Font("SansSerif",java.awt.Font.BOLD,10));wiki.setForeground(MUTED);wiki.setBackground(new Color(16,16,16));wiki.setBorder(BorderFactory.createEmptyBorder(0,3,0,3));wiki.setPreferredSize(new Dimension(20,26));wiki.setToolTipText("Open "+source.name+" on the OSRS Wiki");wiki.getAccessibleContext().setAccessibleName("Open Wiki: "+source.name);wiki.addActionListener(e->openWiki.accept(source.wikiUrl));heading.add(wiki,BorderLayout.EAST);
			}
			group.add(heading,BorderLayout.NORTH);
			if(!collapsed.contains(source.key))
			{
				JPanel rows=new JPanel();rows.setOpaque(false);rows.setLayout(new javax.swing.BoxLayout(rows,javax.swing.BoxLayout.Y_AXIS));
				ArrayList<PanelState.Item> ordered=new ArrayList<>(source.items);
				if(sort.getSelectedIndex()==0) { ordered.sort(Comparator.comparingLong((PanelState.Item i)->i.lastReceived).reversed().thenComparing(i->i.name)); }
				if(sort.getSelectedIndex()==1) { ordered.sort(Comparator.comparingDouble((PanelState.Item i)->publishedOrder(i)).thenComparing(i->i.name)); }
				if(driest()) { ordered.sort(Comparator.comparingDouble((PanelState.Item i)->i.dryness==null?Double.NEGATIVE_INFINITY:i.dryness).reversed().thenComparing(i->i.name)); }
				for(PanelState.Item item:ordered) { rows.add(itemRow(item));if(item.key.equals(expanded)) { rows.add(details(item)); } }
				group.add(rows,BorderLayout.CENTER);
			}
			group.setMaximumSize(new Dimension(Integer.MAX_VALUE,group.getPreferredSize().height));content.add(group);content.add(javax.swing.Box.createVerticalStrut(5));
		}
		if(state.sources.isEmpty()) { JLabel empty=label("No loot recorded in this view.",MUTED);empty.setBorder(BorderFactory.createEmptyBorder(12,0,12,0));content.add(empty); }
		JLabel status=label(state.dataStatus,MUTED);status.setToolTipText(state.dataStatus);content.add(status);content.revalidate();content.repaint();
	}
	private static double publishedOrder(PanelState.Item item)
	{
		try { return "Always".equals(item.rate)?1:item.rate.startsWith("1/")?1/Double.parseDouble(item.rate.substring(2).replace(",","")):Double.POSITIVE_INFINITY; }
		catch(NumberFormatException e) { return Double.POSITIVE_INFINITY; }
	}
	private void renderObservations()
	{
		if(view.getSelectedIndex()==1)
		{
			int pages=Math.max(1,(state.collections.size()+49)/50);historyPage=Math.min(historyPage,pages-1);
			JPanel paging=new JPanel(new BorderLayout(4,0));paging.setOpaque(false);paging.setAlignmentX(LEFT_ALIGNMENT);JButton previous=new JButton("‹"),next=new JButton("›");previous.setEnabled(historyPage>0);next.setEnabled(historyPage+1<pages);previous.setToolTipText("Newer collections");next.setToolTipText("Older collections");previous.addActionListener(e->{historyPage--;render();});next.addActionListener(e->{historyPage++;render();});paging.add(previous,BorderLayout.WEST);paging.add(label("Page "+(historyPage+1)+" / "+pages,MUTED),BorderLayout.CENTER);paging.add(next,BorderLayout.EAST);paging.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));content.add(paging);
			if(state.collections.isEmpty()) { content.add(label("No collection history yet.",MUTED));content.add(label("History starts with new loot notifications.",MUTED)); }
			for(int index=historyPage*50;index<Math.min(state.collections.size(),(historyPage+1)*50);index++)
			{
				PanelState.Source collection=state.collections.get(index);JLabel heading=label(collection.name,GOLD);heading.setBorder(BorderFactory.createEmptyBorder(8,4,2,4));heading.setToolTipText(collection.countTooltip);content.add(heading);content.add(label(date(collection.lastReceived),MUTED));
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
			for(PanelState.Item item:ordered)
			{
				content.add(observationRow(item,true));
				if(item.key.equals(expanded))
				{
					JPanel details=new JPanel(new GridLayout(0,1,0,3));details.setOpaque(false);details.setBorder(BorderFactory.createEmptyBorder(5,8,8,4));details.add(label("Source breakdown",GOLD));
					for(PanelState.Source source:state.sources) { for(PanelState.Item row:source.items) { if(row.id==item.id&&row.quantity>0) { details.add(label(source.name+": x"+NUMBER.format(row.quantity)+" · "+NUMBER.format(row.occurrences)+" outcomes",MUTED)); } } }
					details.setAlignmentX(LEFT_ALIGNMENT);details.setMaximumSize(new Dimension(Integer.MAX_VALUE,details.getPreferredSize().height));content.add(details);
				}
			}
		}
		content.add(label(state.dataStatus,MUTED));
	}
	private JButton observationRow(PanelState.Item item,boolean combined)
	{
		JButton row=new JButton();row.setLayout(new BorderLayout(5,0));row.setBackground(ROW);row.setBorder(BorderFactory.createEmptyBorder(4,2,4,4));row.setAlignmentX(LEFT_ALIGNMENT);
		JLabel sprite=new JLabel(item.id>0?icons.apply(item.id,item.quantity):null);sprite.setPreferredSize(new Dimension(40,32));sprite.setHorizontalAlignment(JLabel.CENTER);row.add(sprite,BorderLayout.WEST);
		JPanel text=new JPanel(new GridLayout(0,1));text.setOpaque(false);text.add(label(item.name,Color.LIGHT_GRAY));if(combined&&!state.stackCounts) { text.add(label(NUMBER.format(item.occurrences)+" occurrences",MUTED)); }row.add(text,BorderLayout.CENTER);row.add(itemCounts(item),BorderLayout.EAST);row.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));row.setToolTipText(html(item.name+"\n"+(combined?"Occurrences: "+item.occurrences+" item-present notifications\n":"")+"Last received: "+date(item.lastReceived)));row.getAccessibleContext().setAccessibleName(item.name+", quantity "+item.quantity);
		if(combined) { itemButtons.put(item.key,row);row.addActionListener(e->{expanded=item.key.equals(expanded)?null:item.key;render();JButton current=itemButtons.get(item.key);if(current!=null) { current.requestFocusInWindow(); }}); }
		return row;
	}
	private JPanel itemRow(PanelState.Item item)
	{
		JPanel holder=new JPanel(new BorderLayout());holder.setOpaque(false);holder.setAlignmentX(LEFT_ALIGNMENT);
		JButton button=new JButton();button.setLayout(new BorderLayout(5,0));button.setBackground(item.key.equals(expanded)?new Color(56,49,39):ROW);button.setBorder(BorderFactory.createMatteBorder(0,0,1,0,BG));
		JLabel icon=new JLabel(item.icon!=null?item.icon:item.id>0?icons.apply(item.id,item.quantity):null);icon.setHorizontalAlignment(JLabel.CENTER);icon.setBorder(BorderFactory.createEmptyBorder(0,2,0,2));icon.setPreferredSize(new Dimension(40,36));if(item.id==0) { icon.setText("?");icon.setToolTipText("Item identity unresolved; no guessed game ID."); }button.add(icon,BorderLayout.WEST);
		JPanel text=new JPanel(new GridLayout(0,1));text.setOpaque(false);text.setBorder(BorderFactory.createEmptyBorder(4,0,4,0));
		JLabel name=label(item.name,item.quantity==0?MUTED:Color.LIGHT_GRAY);text.add(name);
		java.util.Set<String> shownRates=new java.util.LinkedHashSet<>();
		if(state.showAllRates) { for(PanelState.RateOption option:item.rateOptions) { if(!option.historical&&shownRates.add(option.rate)) { JLabel rate=label(option.rate,option.color);rate.setToolTipText(rarityTooltip(item,option));text.add(rate); } } }
		String summary=item.rate.startsWith("Variants:")||item.rate.equals("Multiple published rates")?"Multiple published rates":item.rate;
		if(shownRates.isEmpty()) { JLabel rate=label(summary,item.rarity);rate.setToolTipText(rarityTooltip(item,null));text.add(rate); }button.add(text,BorderLayout.CENTER);
		button.add(itemCounts(item),BorderLayout.EAST);
		button.setToolTipText(itemTooltip(item,summary));
		button.getAccessibleContext().setAccessibleName(item.name+", "+item.rate+", quantity "+item.quantity+", "+(item.key.equals(expanded)?"expanded":"collapsed"));
		itemButtons.put(item.key,button);button.addActionListener(e->{expanded=item.key.equals(expanded)?null:item.key;render();JButton current=itemButtons.get(item.key);if(current!=null) { current.requestFocusInWindow(); }});holder.add(button);holder.setMaximumSize(new Dimension(Integer.MAX_VALUE,Math.max(44,button.getPreferredSize().height)));return holder;
	}
	private JPanel details(PanelState.Item item)
	{
		JPanel wrap=new JPanel(new BorderLayout(0,4));wrap.setBackground(new Color(24,24,24));wrap.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,2,0,0,new Color(119,100,69)),BorderFactory.createEmptyBorder(7,8,8,8)));wrap.setAlignmentX(LEFT_ALIGNMENT);
		JPanel grid=new JPanel(new GridLayout(0,2,8,4));grid.setOpaque(false);
		stat(grid,"Quantity",NUMBER.format(item.quantity),"All recorded units; separate from occurrences.",null);
		stat(grid,"Occurrences",NUMBER.format(item.occurrences),"Recorded notifications containing the item, including excluded/batched observations.",null);
		String availability=item.attempts==null?"\nUnavailable; see item information.":"\nCurrent validated segment.";
		stat(grid,"Attempts",item.attempts==null?"—":NUMBER.format(item.attempts),"Eligible individual attempts."+availability,null);
		stat(grid,"Observed",item.observed==null?"—":item.observed==0?"0%":"1/"+NUMBER.format(1/item.observed),"Occurrences ÷ eligible attempts."+availability,null);
		stat(grid,"Expected",value(item.expected),"Attempts × item-present probability."+availability,null);
		Color luck=!state.luckColors||item.ratio==null?null:item.ratio<.9?new Color(223,153,140):item.ratio>1.1?new Color(141,195,126):GOLD;
		stat(grid,"A/E",item.ratio==null?"—":value(item.ratio)+"×","Actual ÷ expected occurrences.\nColor does not indicate significance."+availability,luck);
		stat(grid,"Dry",item.dry==null?"—":NUMBER.format(item.dry),"Eligible attempts since the last drop\nor the start of this segment."+availability,null);
		stat(grid,"No drop",percent(item.noDrop),"Chance of this streak without a drop.\nNot the next-drop chance."+availability,null);
		stat(grid,"P(X ≥ k)",percent(item.upper),"Chance of this many drops or more."+availability,null);
		stat(grid,"P(X ≤ k)",percent(item.lower),"Chance of this many drops or fewer."+availability,null);
		if(state.showGe) { stat(grid,"GE total",item.valueAvailable?money(item.ge):"—","Current RuneLite GE price × recorded quantity; not historical sale proceeds.",null); }
		if(state.showHa) { stat(grid,"HA total",item.valueAvailable?money(item.ha):"—","High Alchemy total before rune costs.",null); }
		JPanel body=new JPanel();body.setLayout(new javax.swing.BoxLayout(body,javax.swing.BoxLayout.Y_AXIS));body.setOpaque(false);body.add(grid);
		if(item.rateOptions.size()>1||item.unresolvedOccurrences>0)
		{
			JPanel breakdown=new JPanel(new GridLayout(0,1,0,3));breakdown.setOpaque(false);breakdown.setBorder(BorderFactory.createEmptyBorder(8,0,2,0));
			java.util.Map<String,java.util.List<PanelState.RateOption>> tables=new java.util.LinkedHashMap<>();
			for(PanelState.RateOption option:item.rateOptions) { tables.computeIfAbsent(option.table+(option.historical?" (past)":""),key->new ArrayList<>()).add(option); }
			for(java.util.Map.Entry<String,java.util.List<PanelState.RateOption>> table:tables.entrySet())
			{
				breakdown.add(label(table.getKey(),GOLD));
				for(PanelState.RateOption option:table.getValue())
				{
					JPanel line=new JPanel(new GridLayout(1,3,4,0));line.setOpaque(false);line.add(label(option.dropQuantity==null?"—":option.dropQuantity,MUTED));
					JLabel rate=label(option.rate,option.color);rate.setToolTipText(rarityTooltip(item,option));line.add(rate);
					JLabel quantity=label("x"+NUMBER.format(option.quantity==null?0:option.quantity),Color.YELLOW);quantity.setHorizontalAlignment(JLabel.RIGHT);quantity.setToolTipText("Collected units assigned by a unique quantity-row match; not certified rolls.");line.add(quantity);breakdown.add(line);
				}
			}
			if(item.unresolvedOccurrences>0) { JLabel unknown=label("Unknown table    x"+NUMBER.format(item.unresolvedQuantity),MUTED);unknown.setToolTipText("Unassigned collected units · "+NUMBER.format(item.unresolvedOccurrences)+" notifications. Overlapping rows, batches or missing context.");breakdown.add(unknown); }
			body.add(breakdown);
		}
		wrap.add(body);
		JPanel footer=new JPanel(new BorderLayout(4,0));footer.setOpaque(false);
		footer.add(label(item.attempts==null?"Model pending":"Validated segment",MUTED),BorderLayout.CENTER);
		JButton information=new JButton("i");information.setFont(new java.awt.Font("SansSerif",java.awt.Font.BOLD,12));information.setForeground(MUTED);information.setBackground(ROW);information.setPreferredSize(new Dimension(22,22));information.setMargin(new java.awt.Insets(0,0,0,0));information.setToolTipText("Rate history, conditions and sources");information.getAccessibleContext().setAccessibleName("Item information: "+item.name);
		information.addActionListener(e->showInformation(information,item));footer.add(information,BorderLayout.EAST);wrap.add(footer,BorderLayout.SOUTH);
		wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE,wrap.getPreferredSize().height));return wrap;
	}
	private JPanel itemCounts(PanelState.Item item)
	{
		JPanel counts=new JPanel(new GridLayout(0,1,0,1));counts.setOpaque(false);counts.setBorder(BorderFactory.createEmptyBorder(0,0,0,4));
		String quantity=state.stackCounts?net.runelite.client.util.QuantityFormatter.quantityToStackSize(item.quantity):NUMBER.format(state.iconQuantities?item.occurrences:item.quantity);
		JLabel primary=label("x"+quantity,state.stackCounts?Color.YELLOW:Color.LIGHT_GRAY);primary.setHorizontalAlignment(JLabel.RIGHT);primary.setToolTipText(state.stackCounts?"Quantity: "+NUMBER.format(item.quantity):state.iconQuantities?"Occurrences: "+NUMBER.format(item.occurrences):"Quantity: "+NUMBER.format(item.quantity));counts.add(primary);
		if(state.stackCounts&&item.occurrences>0&&item.quantity>item.occurrences) { JLabel occurrences=label(NUMBER.format(item.occurrences)+" occ.",MUTED);occurrences.setHorizontalAlignment(JLabel.RIGHT);occurrences.setToolTipText("Item-present notifications: "+NUMBER.format(item.occurrences));counts.add(occurrences); }
		return counts;
	}
	private void showInformation(JButton anchor,PanelState.Item item)
	{
		javax.swing.JTextArea text=new javax.swing.JTextArea(item.name+"\n\n"+item.rateTooltip+"\n\nStatistics\n"+item.reason);
		text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,12));text.setForeground(Color.LIGHT_GRAY);text.setBackground(ROW);text.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));text.setCaretPosition(0);
		javax.swing.JScrollPane scroll=new javax.swing.JScrollPane(text);scroll.setPreferredSize(new Dimension(300,300));scroll.setHorizontalScrollBarPolicy(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		javax.swing.JPopupMenu popup=new javax.swing.JPopupMenu();popup.setLayout(new BorderLayout());popup.add(scroll);popup.show(anchor,anchor.getWidth()-300,anchor.getHeight());text.requestFocusInWindow();
	}
	private void stat(JPanel grid,String name,String value,String tip,Color color)
	{
		JPanel cell=new JPanel(new BorderLayout(3,0));cell.setOpaque(false);JLabel label=label(name,MUTED),number=label(value,color==null?Color.LIGHT_GRAY:color);cell.add(label,BorderLayout.CENTER);cell.add(number,BorderLayout.EAST);cell.setToolTipText(html(tip));label.setToolTipText(html(tip));number.setToolTipText(html(tip));grid.add(cell);
	}
	private static JLabel label(String text,Color color) { JLabel l=new JLabel(text);l.setAlignmentX(LEFT_ALIGNMENT);l.setFont(FontManager.getRunescapeSmallFont());l.setForeground(color);return l; }
	private static String value(Double value) { return value==null?"—":DECIMAL.format(value); }
	private static String tooltipValue(long total,long each,boolean available) { return available?money(total).replace(" gp","")+" ("+NUMBER.format(each)+" ea)":"—"; }
	private static String itemTooltip(PanelState.Item item,String rate)
	{
		Color bright=new Color(225,225,225),alchemy=new Color(157,181,207);
		return "<html><body style='width:180px'>"+colored(item.name+" x"+NUMBER.format(item.quantity),bright)+"<br>"+
			colored("Occurrences ",MUTED)+colored("x"+NUMBER.format(item.occurrences),bright)+"<br>"+
			colored("Rate ",MUTED)+colored(rate,item.rarity==null?MUTED:item.rarity)+"<br>"+
			colored("GE ",MUTED)+colored(tooltipValue(item.ge,item.geEach,item.valueAvailable),item.valueAvailable?GOLD:MUTED)+"<br>"+
			colored("HA ",MUTED)+colored(tooltipValue(item.ha,item.haEach,item.valueAvailable),item.valueAvailable?alchemy:MUTED)+"</body></html>";
	}
	private static String colored(String text,Color color) { return "<font color='"+String.format(Locale.ROOT,"#%02x%02x%02x",color.getRed(),color.getGreen(),color.getBlue())+"'>"+escape(text)+"</font>"; }
	private static String rarityTooltip(PanelState.Item item,PanelState.RateOption selected)
	{
		StringBuilder tip=new StringBuilder("<html><body style='width:220px'>");
		tip.append(colored(item.name,GOLD));
		java.util.List<PanelState.RateOption> options=selected==null?item.rateOptions:java.util.Collections.singletonList(selected);
		boolean found=false;
		for(PanelState.RateOption option:options)
		{
			if(selected==null&&option.historical) { continue; }
			found=true;tip.append("<br><br>").append(colored(option.rate,option.color==null?MUTED:option.color));
			if(option.tooltip!=null) { for(String line:option.tooltip.split("\\n")) { int colon=line.indexOf(':');tip.append("<br>");if(colon>=0) { tip.append(colored(line.substring(0,colon+1)+" ",MUTED)).append(colored(line.substring(colon+1).trim(),Color.LIGHT_GRAY)); }else { tip.append(colored(line,Color.LIGHT_GRAY)); } } }
		}
		if(!found) { tip.append("<br>").append(colored(item.rate==null?"Rate unknown":item.rate,item.rarity==null?MUTED:item.rarity)).append("<br>").append(colored("No resolved published rate.",MUTED)); }
		tip.append("<br><br>").append(colored("Published table rate; event probability may differ.",MUTED));
		return tip.append("</body></html>").toString();
	}
	private static String escape(String text) { return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }
	private static String percent(Double p) { return p==null?"—":new DecimalFormat("0.####",DecimalFormatSymbols.getInstance(Locale.US)).format(p*100)+"%"; }
	private static String date(long time) { return time==0?"Never observed":DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time)); }
	public static String money(long value) { return value>=1_000_000?NUMBER.format(value/1_000_000.0)+"M gp":value>=1000?NUMBER.format(value/1000.0)+"k gp":NUMBER.format(value)+" gp"; }
	private static String html(String s) { return "<html><body style='width:180px'>"+escape(s).replace("\n","<br>")+"</body></html>"; }
}





