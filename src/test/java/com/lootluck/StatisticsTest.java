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
	@Test public void wikiDryCalculatorRecurrenceMatchesInclusiveTails()
	{
		// Independently evaluated recurrence from the preserved Module:Dry calc,
		// converting its strict fewer/more results to our explicitly inclusive labels.
		for(int n:new int[]{128,1000,5000}) { for(int k:new int[]{0,1,3,7})
		{
			double p=1.0/128,term=Math.pow(1-p,n),less=0;
			for(int x=0;x<k;x++) { less+=term;term*=((double)(n-x)/(x+1))*p/(1-p); }
			assertEquals(less+term,Statistics.lower(n,k,p),1e-12);
			assertEquals(1-less,Statistics.upper(n,k,p),1e-12);
		} }
	}
	@Test public void wikiVisageExampleAndMultiRollBoundary()
	{
		// Drop rate page: one 1/5,000 item over 5,000 kills gives about 63.2% presence.
		assertEquals(Math.pow(4999.0/5000,5000),Statistics.dryProbability(5000,1.0/5000),1e-13);
		assertEquals(.6322,1-Statistics.dryProbability(5000,1.0/5000),.00005);
		assertEquals(0,Statistics.multipleRollPresence(0,2),0);assertEquals(1,Statistics.multipleRollPresence(1,2),0);
	}
}
