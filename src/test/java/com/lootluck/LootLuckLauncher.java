package com.lootluck;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class LootLuckLauncher
{
	public static void main(String[] args) throws Exception { ExternalPluginManager.loadBuiltin(LootLuckPlugin.class);RuneLite.main(args); }
}
