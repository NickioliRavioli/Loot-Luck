package com.lootluck;

import com.lootluck.tracking.MysteryBoxTracker;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class MysteryBoxTrackerTest
{
	@Test public void boxOpeningRequiresConfirmationAndConsumptionAndRecordsOnlyOnce()
	{
		MysteryBoxTracker tracker=new MysteryBoxTracker();Map<Integer,Long> before=Map.of(ItemID.MACRO_QUIZ_MYSTERY_BOX,1L,ItemID.COINS,100L);
		tracker.arm(before,10);assertTrue(tracker.poll(Map.of(ItemID.COINS,100L,ItemID.BUCKET_EMPTY,1L),11).isEmpty());
		tracker.message("Inside the box you find a bucket! Better luck next time!");
		assertTrue(tracker.poll(before,11).isEmpty());assertEquals(Map.of(ItemID.BUCKET_EMPTY,1L),tracker.poll(Map.of(ItemID.COINS,100L,ItemID.BUCKET_EMPTY,1L),11));
		assertFalse(tracker.pending());assertTrue(tracker.poll(Map.of(ItemID.BUCKET_EMPTY,1L),12).isEmpty());
	}
	@Test public void cancelledExpiredAndUnrelatedRewardsDoNotCount()
	{
		MysteryBoxTracker tracker=new MysteryBoxTracker();tracker.message("Inside the box you find a bucket!");assertTrue(tracker.poll(Map.of(ItemID.BUCKET_EMPTY,1L),1).isEmpty());
		tracker.arm(Map.of(ItemID.MACRO_QUIZ_MYSTERY_BOX,1L),1);tracker.message("Your reward is: 1 x Mystery box.");assertTrue(tracker.poll(Map.of(ItemID.BUCKET_EMPTY,1L),2).isEmpty());
		tracker.message("Inside the box you find a bucket!");assertTrue(tracker.poll(Map.of(ItemID.BUCKET_EMPTY,1L),7).isEmpty());assertFalse(tracker.pending());
		tracker.arm(Map.of(ItemID.MACRO_QUIZ_MYSTERY_BOX,1L),10);tracker.message("Inside the box you find a bucket!");tracker.clear();assertTrue(tracker.poll(Map.of(ItemID.BUCKET_EMPTY,1L),11).isEmpty());
	}
}
