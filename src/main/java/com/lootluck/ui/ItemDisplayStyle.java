package com.lootluck.ui;

import com.lootluck.LootLuckConfig;
import java.util.function.BiConsumer;

/** Registry of selectable row styles, retaining the existing saved config keys. */
enum ItemDisplayStyle
{
	ICON_QUANTITY("Quantity on icon",true,false),
	RIGHT_COUNTS("Quantity and occurrences",false,true),
	RIGHT_QUANTITY("Quantity on right",false,false);

	final String title;
	final boolean iconQuantities;
	final boolean stackCounts;
	ItemDisplayStyle(String title,boolean iconQuantities,boolean stackCounts)
	{
		this.title=title;this.iconQuantities=iconQuantities;this.stackCounts=stackCounts;
	}
	static ItemDisplayStyle selected(LootLuckConfig config)
	{
		boolean icons=config.iconQuantities()&&!config.stackCounts();
		for(ItemDisplayStyle style:values()) { if(style.iconQuantities==icons&&style.stackCounts==config.stackCounts()) { return style; } }
		return RIGHT_QUANTITY;
	}
	void select(BiConsumer<String,Object> changed)
	{
		changed.accept("iconQuantities",iconQuantities);
		changed.accept("stackCounts",stackCounts);
	}
}
