package com.lootluck.ui;

/** Integer-only abbreviated counts; never round a displayed amount upward. */
final class CountFormat
{
	private CountFormat() { }
	static String compact(long count)
	{
		if(count>=1_000_000_000_000L) { return count/1_000_000_000_000L+"T"; }
		if(count>=1_000_000_000) { return count/1_000_000_000+"B"; }
		if(count>=1_000_000) { return count/1_000_000+"M"; }
		if(count>=1_000) { return count/1_000+"K"; }
		return Long.toString(count);
	}
}
