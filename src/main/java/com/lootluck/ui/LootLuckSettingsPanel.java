package com.lootluck.ui;

import com.lootluck.LootLuckConfig;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/** Explicit config bindings keep the sidebar and RuneLite configuration in sync. */
final class LootLuckSettingsPanel extends JPanel
{
	private final List<Runnable> updates=new ArrayList<>();
	private boolean syncing;
	LootLuckSettingsPanel(LootLuckConfig config,BiConsumer<String,Object> changed,java.util.function.Function<Boolean,javax.swing.Icon> previewIcons)
	{
		this(config,changed,previewIcons,null,null,null);
	}
	LootLuckSettingsPanel(LootLuckConfig config,BiConsumer<String,Object> changed,java.util.function.Function<Boolean,javax.swing.Icon> previewIcons,javax.swing.JButton refresh,javax.swing.JButton reset,javax.swing.JComponent dataActions)
	{
		setOpaque(false);setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
		section("Wiki updates");
		toggle("Allow Wiki refresh","wikiRefresh",config::wikiRefresh,null,changed);
		hint("<b>Off:</b> Uses bundled rates or your last saved Wiki download. No background Wiki requests; Refresh is unavailable. Loot recording still works, but rates may become outdated.");
		hint("<b>On:</b> Automatically updates outdated drop tables and enables Refresh. Downloads may take a few minutes; saved rates stay available if the connection fails.");
		hint("Requests send your IP address and public game-content queries to the OSRS Wiki. Your account name and loot history are never sent.");
		if(refresh!=null) { refresh.setAlignmentX(LEFT_ALIGNMENT);refresh.setMaximumSize(new Dimension(Integer.MAX_VALUE,28));add(refresh);updates.add(()->refresh.setEnabled(config.wikiRefresh())); }
		section("Layout");
		toggle("Grid view","gridView",config::gridView,null,changed);
		toggle("Group by drop table","groupByDropTable",config::groupByDropTable,"Keeps items from the same table together. Sorting applies within each table.",changed);
		toggle("Show variants separately","separateConditionVariants",config::separateConditionVariants,"Gives each drop-condition variant its own source header. Tracking and totals stay the same.",changed);
		section("Items and rates");
		toggle("Hide unobtained from other world type","hideUnobtainedOtherWorld",config::hideUnobtainedOtherWorld,"Hides members-only drops on free worlds and free-only drops on members worlds. Obtained items stay visible.",changed);
		toggle("Show all known rates","showAllRates",config::showAllRates,"Shows published rate alternatives beneath item names. Full breakdowns remain in expanded details.",changed);
		toggle("Show source Wiki links","showWikiLinks",config::showWikiLinks,null,changed);
		section("Values");
		toggle("Show GE values","showGe",config::showGe,"Uses current prices, not the price when an item was collected.",changed);
		toggle("Show HA values","showHa",config::showHa,"High Alchemy totals exclude rune costs.",changed);
		if(PanelState.LUCK_STATISTICS_VISIBLE) { toggle("Luck colors","luckColors",config::luckColors,"Colors actual/expected ratios; colors do not indicate statistical significance.",changed); }
		section("History and data");
		JLabel history=new JLabel("Collection history limit");history.setForeground(Color.LIGHT_GRAY);history.setAlignmentX(LEFT_ALIGNMENT);add(history);
		JSpinner limit=new JSpinner(new SpinnerNumberModel(Math.max(0,config.historyLimit()),0,Integer.MAX_VALUE,1000));limit.setAlignmentX(LEFT_ALIGNMENT);limit.setMaximumSize(new Dimension(Integer.MAX_VALUE,28));add(limit);
		hint("0 keeps unlimited history. Lowering the limit removes the oldest collection entries; lifetime totals stay intact.");
		limit.addChangeListener(e->{if(!syncing) { changed.accept("historyLimit",limit.getValue()); }});updates.add(()->limit.setValue(Math.max(0,config.historyLimit())));
		if(reset!=null)
		{
			reset.setAlignmentX(LEFT_ALIGNMENT);reset.setMaximumSize(new Dimension(Integer.MAX_VALUE,28));add(reset);
			hint("Clears the current session's loot totals and collection history. Lifetime data is kept. New loot starts a fresh session.");
		}
		if(dataActions!=null) { dataActions.setAlignmentX(LEFT_ALIGNMENT);dataActions.setMaximumSize(new Dimension(Integer.MAX_VALUE,dataActions.getPreferredSize().height));add(dataActions); }
		section("Item counts");
		javax.swing.ButtonGroup styleGroup=new javax.swing.ButtonGroup();
		for(ItemDisplayStyle style:ItemDisplayStyle.values())
		{
			JLabel caption=new JLabel(style.title);caption.setForeground(new Color(173,169,160));caption.setAlignmentX(LEFT_ALIGNMENT);add(caption);
			javax.swing.JButton button=LootLuckPanel.stylePreview(style,previewIcons.apply(style.iconQuantities));styleGroup.add(button);add(button);add(javax.swing.Box.createVerticalStrut(6));
			button.addActionListener(e->{if(!syncing) { style.select(changed);sync(); }});
			updates.add(()->{
				boolean selected=ItemDisplayStyle.selected(config)==style;button.setSelected(selected);
				button.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(selected?new Color(223,192,113):new Color(65,65,65),2),javax.swing.BorderFactory.createEmptyBorder(4,2,4,2)));
				button.getAccessibleContext().setAccessibleDescription(selected?"Selected item style":"Select this item style");
			});
		}
		section("Background effects");
		hint("Applies to obtained items only. Select a preview to choose the effect.");
		javax.swing.ButtonGroup effectsGroup=new javax.swing.ButtonGroup();
		for(ItemEffect effect:ItemEffect.values())
		{
			javax.swing.JButton button=new EffectButton(effect,new Color(227,104,98));button.setLayout(new java.awt.BorderLayout(6,0));button.setBackground(new Color(30,30,30));
			JLabel icon=new JLabel(previewIcons.apply(false));button.add(icon,java.awt.BorderLayout.WEST);
			JLabel name=new JLabel(effect.title);name.setForeground(Color.LIGHT_GRAY);button.add(name,java.awt.BorderLayout.CENTER);
			button.setAlignmentX(LEFT_ALIGNMENT);button.setPreferredSize(new Dimension(210,44));button.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));button.getAccessibleContext().setAccessibleName("Preview effect: "+effect.title);LootLuckPanel.makeRowClickable(button,button);effectsGroup.add(button);add(button);add(javax.swing.Box.createVerticalStrut(4));
			button.addActionListener(event->{if(!syncing) { changed.accept("itemEffect",effect);sync(); }});
			updates.add(()->{boolean selected=config.itemEffect()==effect;button.setSelected(selected);button.setBorder(javax.swing.BorderFactory.createLineBorder(selected?new Color(223,192,113):new Color(65,65,65),2));});
		}
		sync();
	}
	private void section(String title)
	{
		JLabel heading=new JLabel(title);heading.setForeground(new Color(223,192,113));heading.setFont(net.runelite.client.ui.FontManager.getRunescapeFont());heading.setAlignmentX(LEFT_ALIGNMENT);
		heading.setBorder(javax.swing.BorderFactory.createEmptyBorder(12,0,5,0));add(heading);
	}
	private void hint(String text)
	{
		JLabel help=new JLabel("<html><body style='width:145px'>"+text+"</body></html>");help.setForeground(new Color(173,169,160));help.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());help.setAlignmentX(LEFT_ALIGNMENT);
		help.setBorder(javax.swing.BorderFactory.createEmptyBorder(0,8,8,0));help.setMaximumSize(new Dimension(Integer.MAX_VALUE,help.getPreferredSize().height));add(help);
	}
	private void toggle(String title,String key,BooleanSupplier value,String help,BiConsumer<String,Object> changed)
	{
		JCheckBox box=new JCheckBox("<html><body style='width:145px'>"+title+"</body></html>");box.getAccessibleContext().setAccessibleName(title);box.setOpaque(false);box.setForeground(Color.LIGHT_GRAY);box.setAlignmentX(LEFT_ALIGNMENT);box.setMaximumSize(new Dimension(Integer.MAX_VALUE,box.getPreferredSize().height+6));add(box);
		box.addActionListener(e->{
			if(syncing) { return; }
			if("wikiRefresh".equals(key)&&box.isSelected()&&JOptionPane.showConfirmDialog(this,"This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers","Allow Wiki refresh",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION) { box.setSelected(false);return; }
			changed.accept(key,box.isSelected());sync();
		});
		updates.add(()->box.setSelected(value.getAsBoolean()));if(help!=null&&!help.isBlank()) { hint(help); }
	}
	void sync()
	{
		syncing=true;try { updates.forEach(Runnable::run); }finally { syncing=false; }
	}
}
