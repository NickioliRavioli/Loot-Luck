package com.lootluck.ui;

public enum ItemEffect
{
	NONE("None"), SHIMMER("Golden shimmer"), SPARKLES("Floating sparkles"), PRISMATIC("Prismatic aura"), STARBURST("Starburst"), CELEBRATION("Drop celebration"), RARITY("Rarity color aura");
	public final String title;
	ItemEffect(String title) { this.title=title; }
	@Override public String toString() { return title; }
}
