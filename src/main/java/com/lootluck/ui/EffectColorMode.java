package com.lootluck.ui;

public enum EffectColorMode
{
	RARITY("Rarity"), ITEM_VALUE("Value per item"), TOTAL_VALUE("Total collected value");
	private final String title;
	EffectColorMode(String title) { this.title=title; }
	@Override public String toString() { return title; }
}
