package com.lootluck;

import com.lootluck.rates.RateRow;
import java.awt.Color;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorldMarkerRatesTest
{
	@Test public void brutusFreeToPlayCoinsKeepPublishedRatesAndWorldTooltip()
	{
		String marker="<sub title=\"Free-to-play-only\" style=\"cursor:help; margin-left:3px;\">(f)</sub>";
		for(String rate:new String[]{"15/81","5/81"})
		{
			RateRow row=new RateRow();row.rawRate=rate;row.conditions=marker;
			assertEquals("15/81".equals(rate)?"1/5.4":"1/16.2",row.display());
			assertEquals("(f)",row.worldMarker());
			assertEquals(new Color(130,184,117),row.color());
			assertTrue(row.tooltip().contains("Conditions: Free-to-play world only"));assertFalse(row.tooltip().contains("<sub"));
			row.conditions=marker+" Requires a quest";assertEquals("Conditional",row.display());assertTrue(row.tooltip().contains("Free-to-play world only; Requires a quest"));
		}
	}
	@Test public void membersMarkerAndUnknownConditionsRetainTheirMeaning()
	{
		RateRow row=new RateRow();row.rawRate="1/128";row.conditions="<sub title='Members-only'>(m)</sub>";
		assertEquals("1/128",row.display());assertTrue(row.tooltip().contains("Members world only"));
		assertEquals("(m)",row.worldMarker());
		row.conditions="<sub title='Unrecognized'>(f)</sub>";assertEquals("Conditional",row.display());
		assertEquals("",row.worldMarker());
	}
}
