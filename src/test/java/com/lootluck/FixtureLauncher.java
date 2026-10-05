package com.lootluck;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class FixtureLauncher
{
	public static void main(String[] args) throws Exception { ExternalPluginManager.loadBuiltin(FixturePlugin.class);RuneLite.main(args); }
}
