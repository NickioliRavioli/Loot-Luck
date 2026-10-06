package com.lootluck.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class CountFormatTest
{
	@Test public void abbreviationsTruncateWithoutFloatingPointRounding()
	{
		assertEquals("999",CountFormat.compact(999));assertEquals("1K",CountFormat.compact(1000));
		assertEquals("12K",CountFormat.compact(12500));assertEquals("999K",CountFormat.compact(999999));
		assertEquals("1M",CountFormat.compact(1999999));assertEquals("1B",CountFormat.compact(1999999999));
		assertEquals("999B",CountFormat.compact(999_999_999_999L));assertEquals("1T",CountFormat.compact(1_999_999_999_999L));
		assertEquals("9223372T",CountFormat.compact(Long.MAX_VALUE));
		assertEquals("12K gp",LootLuckPanel.money(12500));assertEquals("1M gp",LootLuckPanel.money(1999999));
		assertEquals("9B gp",LootLuckPanel.money(9_410_000_000L));assertEquals("4T gp",LootLuckPanel.money(4_702_500_000_000L));
	}
}
