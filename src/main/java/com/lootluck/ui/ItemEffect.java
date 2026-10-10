package com.lootluck.ui;

public enum ItemEffect
{
	NONE("None"),
	VALUE_FADE("Value fade"), VALUE_DUST("Value fade particles"), VALUE_AURORA("Value aurora"),
	RARITY("Color aura"), RARITY_PRISM("Prism"), RARITY_AURORA("Aurora"), RARITY_OPAL("Opal"), RARITY_FADE("Fade"), RARITY_PULSE("Fade pulse"), RARITY_DUST("Fade particles"), RARITY_SHEEN("Fade shimmer"),
	SHIMMER("Golden shimmer"), SPARKLES("Floating sparkles"), PRISMATIC("Prismatic aura"), STARBURST("Starburst"), CELEBRATION("Drop celebration"), FIRE("Fire"), RAIN("Rain");
	public final String title;
	ItemEffect(String title) { this.title=title; }
	boolean valueScaled() { return this==VALUE_FADE||this==VALUE_DUST||this==VALUE_AURORA; }
	boolean animated(int tier) { return this!=NONE&&this!=RARITY_FADE&&this!=VALUE_FADE&&(!valueScaled()||tier>0); }
	@Override public String toString() { return title; }
}
