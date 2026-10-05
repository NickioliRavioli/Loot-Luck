package com.lootluck.ui;

import com.lootluck.LootLuckConfig;
import java.awt.Color;
import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemDisplayStyleTest
{
	@Test public void previewsSelectPersistAndSyncLegacySettings() throws Exception
	{
		SwingUtilities.invokeAndWait(()->{
			Map<String,Object> saved=new HashMap<>();
			LootLuckConfig config=new LootLuckConfig() {
				@Override public boolean iconQuantities() { return (boolean)saved.getOrDefault("iconQuantities",true); }
				@Override public boolean stackCounts() { return (boolean)saved.getOrDefault("stackCounts",true); }
			};
			LootLuckSettingsPanel panel=new LootLuckSettingsPanel(config,saved::put,flag->null);
			Map<String,JButton> buttons=new HashMap<>();
			for(Component child:panel.getComponents()) { if(child instanceof JButton) { JButton button=(JButton)child;if(!button.getAccessibleContext().getAccessibleName().startsWith("Preview effect:")) { buttons.put(button.getAccessibleContext().getAccessibleName(),button); } } }
			assertEquals(ItemDisplayStyle.values().length,buttons.size());
			assertTrue(buttons.get(ItemDisplayStyle.RIGHT_COUNTS.title).isSelected());
			for(ItemDisplayStyle style:ItemDisplayStyle.values())
			{
				JButton button=buttons.get(style.title);button.doClick();assertEquals(style,ItemDisplayStyle.selected(config));
				assertEquals(style.iconQuantities,saved.get("iconQuantities"));assertEquals(style.stackCounts,saved.get("stackCounts"));
				assertEquals(1,buttons.values().stream().filter(JButton::isSelected).count());
				assertEquals(new Color(223,192,113),((LineBorder)((CompoundBorder)button.getBorder()).getOutsideBorder()).getLineColor());
			}
			saved.put("stackCounts",true);saved.put("iconQuantities",true);panel.sync();assertTrue(buttons.get(ItemDisplayStyle.RIGHT_COUNTS.title).isSelected());
		});
	}
}
