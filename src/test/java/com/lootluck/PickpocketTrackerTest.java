package com.lootluck;

import com.lootluck.tracking.PickpocketTracker;
import org.junit.Test;
import static org.junit.Assert.*;

public class PickpocketTrackerTest
{
	@Test public void successCanBeClaimedBeforeItsDoublingMessageArrives()
	{
		PickpocketTracker tracker=new PickpocketTracker();
		assertNull(tracker.message("You attempt to pick the Master Farmer's pocket.",10,3));
		assertNull(tracker.message("You pick the Master Farmer's pocket.",10,3));
		PickpocketTracker.Success success=tracker.claim("Master Farmer",10);assertNotNull(success);
		tracker.message("You steal 1 Hammerstone hop seed.",10,3);tracker.message(PickpocketTracker.ROGUE_MESSAGE,10,3);
		assertEquals(";rogueDoubled=true;roguePieces=3",success.metadata());assertNull(tracker.claim("Master Farmer",10));
	}
	@Test public void apostrophesArticlesAndNamesEndingInSAreSupported()
	{
		PickpocketTracker tracker=new PickpocketTracker();
		assertEquals("Master Farmer",tracker.message("You fail to pick the Master Farmer\u2019s pocket.",1,0));
		assertEquals("James",tracker.message("You fail to pick James' pocket.",2,0));
		tracker.message("You pick the knight of Ardougne's pocket.",3,0);
		assertNotNull(tracker.claim("Knight Of Ardougne",3));
		assertNull(tracker.message("You've been stunned!",3,0));
		assertNull(tracker.message("You don't have enough inventory space to do that.",3,0));
	}
	@Test public void hamAliasAndUnrelatedOrExpiredRewardsStaySeparate()
	{
		PickpocketTracker tracker=new PickpocketTracker();tracker.selected("H.A.M. Member");
		assertEquals("H.A.M. Member",tracker.message("You fail to pick the woman's pocket.",1,0));
		tracker.message("You pick the man's pocket.",2,0);assertNull(tracker.claim("Guard",2));
		assertNotNull(tracker.claim("H.A.M. Member",2));
		tracker.message("You pick the Guard's pocket.",3,0);assertNull(tracker.claim("Guard",6));
		tracker.message("You pick the Guard's pocket.",7,0);tracker.clear();assertNull(tracker.claim("Guard",7));
	}
	@Test public void repeatedTicksKeepBonusesSeparateAndSameTickBatchesStayUnknown()
	{
		PickpocketTracker tracker=new PickpocketTracker();tracker.message("You pick the Guard's pocket.",1,2);
		tracker.message(PickpocketTracker.ROGUE_MESSAGE,1,2);assertTrue(tracker.claim("Guard",1).metadata().contains("rogueDoubled=true"));
		tracker.message("You pick the Guard's pocket.",2,2);assertTrue(tracker.claim("Guard",2).metadata().contains("rogueDoubled=false"));
		tracker.message("You pick the Guard's pocket.",3,2);PickpocketTracker.Success first=tracker.claim("Guard",3);
		tracker.message("You pick the Guard's pocket.",3,2);tracker.message(PickpocketTracker.ROGUE_MESSAGE,3,2);
		assertEquals(";rogueDoubled=unknown",first.metadata());assertEquals(";rogueDoubled=unknown",tracker.claim("Guard",3).metadata());
	}
	@Test public void interruptionsDoNotTurnMissingBonusMessagesIntoConfirmedNormalLoot()
	{
		PickpocketTracker tracker=new PickpocketTracker();tracker.message("You pick the Guard's pocket.",1,3);
		PickpocketTracker.Success success=tracker.claim("Guard",1);assertTrue(success.metadata(false).contains("rogueDoubled=unknown"));
		tracker.message(PickpocketTracker.ROGUE_MESSAGE,1,3);assertTrue(success.metadata(false).contains("rogueDoubled=true"));
	}
}
