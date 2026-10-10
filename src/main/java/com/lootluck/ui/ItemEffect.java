package com.lootluck.ui;

public enum ItemEffect
{
	NONE("None"),
	RARITY("Color aura"), RARITY_PRISM("Prism"), RARITY_AURORA("Aurora"), RARITY_OPAL("Opal"), RARITY_FADE("Fade"), RARITY_PULSE("Fade pulse"), RARITY_DUST("Fade particles"), RARITY_SHEEN("Fade shimmer"),
	SHIMMER("Golden shimmer"), SPARKLES("Floating sparkles"), PRISMATIC("Prismatic aura"), STARBURST("Starburst"), CELEBRATION("Drop celebration"), FIRE("Fire"), RAIN("Rain");
	public final String title;
	ItemEffect(String title) { this.title=title; }
	@Override public String toString() { return title; }
}
