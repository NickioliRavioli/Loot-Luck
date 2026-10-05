package com.lootluck.ui;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceWikiUrlTest
{
	@Test public void clueTiersUseCanonicalWikiCapitalization()
	{
		for(String tier:new String[]{"beginner","easy","medium","hard","elite","master"})
		{
			String expected="https://oldschool.runescape.wiki/w/Clue_scroll_("+tier+")";
			String title="Clue Scroll ("+tier.substring(0,1).toUpperCase(Locale.ROOT)+tier.substring(1)+")";
			assertEquals(expected,URLDecoder.decode(ViewBuilder.sourceWikiUrl(title),StandardCharsets.UTF_8));
			assertEquals(expected,URLDecoder.decode(ViewBuilder.sourceWikiUrl("Clue scroll ("+tier+")"),StandardCharsets.UTF_8));
		}
		assertEquals("https://oldschool.runescape.wiki/w/General_Graardor",ViewBuilder.sourceWikiUrl("General Graardor"));
	}
}
