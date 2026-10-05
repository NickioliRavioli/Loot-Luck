package com.lootluck;

import com.lootluck.stats.Statistics;
import org.junit.Test;
import static org.junit.Assert.*;

public class StatisticsTest
{
	@Test public void independentlyVerifiedExample()
	{
		double p=5.0/2218;
		assertEquals(2.254283138,Statistics.expected(1000,p),1e-9);
		assertEquals(3.1052,Statistics.ratio(1000,7,p),1e-4);
		assertEquals(.00837305118144,Statistics.upper(1000,7,p),1e-12);
		assertEquals(.99773949,Statistics.lower(1000,7,p),1e-8);
		assertEquals(.10468202,Statistics.dryProbability(1000,p),1e-8);
	}
	@Test public void boundaryAndRareEvents()
	{
		assertEquals(1,Statistics.upper(0,0,.5),0);assertEquals(1,Statistics.lower(0,0,.5),0);
		assertTrue(Double.isNaN(Statistics.ratio(0,0,.5)));
		assertEquals(0,Statistics.upper(10,1,0),0);assertEquals(1,Statistics.lower(10,0,0),0);
		assertEquals(0,Statistics.lower(10,9,1),0);assertEquals(1,Statistics.upper(10,10,1),0);
		assertEquals(1,Statistics.dryProbability(0,1),0);assertEquals(0,Statistics.dryProbability(1,1),0);
		assertEquals(.5,Statistics.lower(1,0,.5),1e-14);
		assertTrue(Statistics.upper(1000000,20,1e-6)>0);
		assertEquals(2047.0/1048576,Statistics.multipleRollPresence(1.0/1024,2),1e-15);
		assertEquals(0,Statistics.dryness(0,1),0);
	}
	@Test(expected=IllegalArgumentException.class) public void rejectsMoreSuccessesThanAttempts() { Statistics.upper(1,2,.5); }
	@Test(expected=IllegalArgumentException.class) public void rejectsNaN() { Statistics.dryProbability(3,Double.NaN); }
}
