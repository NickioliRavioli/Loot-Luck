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
		setOpaque(false);setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
		toggle("Allow Wiki refresh","wikiRefresh",config::wikiRefresh,"Refresh public drop tables from the OSRS Wiki.",changed);
		toggle("Include unobtained","includeUnobtained",config::includeUnobtained,"Driest sorting always includes unobtained items.",changed);
		toggle("Show all known rates","showAllRates",config::showAllRates,"Show published rate alternatives beneath item names.",changed);
		toggle("Show source Wiki links","showWikiLinks",config::showWikiLinks,"Show Wiki buttons in source headers.",changed);
		JLabel styles=new JLabel("Item style");styles.setForeground(Color.LIGHT_GRAY);styles.setAlignmentX(LEFT_ALIGNMENT);add(styles);
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
		JLabel history=new JLabel("Collection history limit");history.setForeground(Color.LIGHT_GRAY);history.setAlignmentX(LEFT_ALIGNMENT);add(history);
		JSpinner limit=new JSpinner(new SpinnerNumberModel(Math.max(0,config.historyLimit()),0,Integer.MAX_VALUE,1000));limit.setAlignmentX(LEFT_ALIGNMENT);limit.setMaximumSize(new Dimension(Integer.MAX_VALUE,28));limit.setToolTipText("0 means unlimited. Lowering this prunes oldest history; lifetime totals stay intact.");add(limit);
		limit.addChangeListener(e->{if(!syncing) { changed.accept("historyLimit",limit.getValue()); }});updates.add(()->limit.setValue(Math.max(0,config.historyLimit())));
		toggle("Luck colors","luckColors",config::luckColors,"Color actual/expected ratios; colors do not indicate statistical significance.",changed);
		toggle("Show GE values","showGe",config::showGe,"Current GE totals in source headers and expanded items.",changed);
		toggle("Show HA values","showHa",config::showHa,"High Alchemy totals in expanded items, excluding rune costs.",changed);
		sync();
	}
	private void toggle(String title,String key,BooleanSupplier value,String help,BiConsumer<String,Object> changed)
	{
		JCheckBox box=new JCheckBox("<html><body style='width:165px'>"+title+"</body></html>");box.getAccessibleContext().setAccessibleName(title);box.setOpaque(false);box.setForeground(Color.LIGHT_GRAY);box.setToolTipText(help);box.setAlignmentX(LEFT_ALIGNMENT);box.setMaximumSize(new Dimension(Integer.MAX_VALUE,box.getPreferredSize().height+6));add(box);
		box.addActionListener(e->{
			if(syncing) { return; }
			if("wikiRefresh".equals(key)&&box.isSelected()&&JOptionPane.showConfirmDialog(this,"This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers","Allow Wiki refresh",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION) { box.setSelected(false);return; }
			changed.accept(key,box.isSelected());
		});
		updates.add(()->box.setSelected(value.getAsBoolean()));
	}
	void sync()
	{
		syncing=true;try { updates.forEach(Runnable::run); }finally { syncing=false; }
	}
}
