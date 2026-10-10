package com.lootluck;

import com.lootluck.model.SourceIdentity;
import com.lootluck.rates.RewardSources;
import com.lootluck.tracking.MazeRewardTracker;
import com.lootluck.tracking.MazeRewardTracker.Reward;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;
import static org.junit.Assert.*;

public class MazeRewardTrackerTest
{
	@Test public void onlyMazeObjectsAndTheirRewardOptionsArmTracking()
	{
		assertEquals(Reward.CHEST,MazeRewardTracker.interaction(ObjectID.MACRO_MAZE_CHEST,"Open"));
		assertEquals(Reward.COMPLETION,MazeRewardTracker.interaction(ObjectID.MACRO_MAZE_COMPLETE,"Touch"));
		assertNull(MazeRewardTracker.interaction(ObjectID.MACRO_MAZE_CHESTEMPTY,"Open"));
		assertNull(MazeRewardTracker.interaction(ObjectID.MACRO_MAZE_CHEST,"Examine"));
	}
	@Test public void chestRecordsStackIncreaseOnce()
	{
		MazeRewardTracker tracker=new MazeRewardTracker();tracker.arm(Reward.CHEST,Map.of(ItemID.AIRRUNE,100L),1,true);
		assertTrue(tracker.poll(Map.of(ItemID.AIRRUNE,100L),2,true,id->id).isEmpty());
		assertEquals(Map.of(ItemID.AIRRUNE,15L),tracker.poll(Map.of(ItemID.AIRRUNE,115L),3,true,id->id));
		assertFalse(tracker.pending());assertTrue(tracker.poll(Map.of(ItemID.AIRRUNE,115L),4,true,id->id).isEmpty());
		tracker.arm(Reward.CHEST,Map.of(ItemID.AIRRUNE,115L),5,true);
		assertEquals(Map.of(ItemID.AIRRUNE,15L),tracker.poll(Map.of(ItemID.AIRRUNE,130L),6,true,id->id));
	}
	@Test public void completionSurvivesTeleportAndCombinesRepeatedRolls()
	{
		MazeRewardTracker tracker=new MazeRewardTracker();tracker.arm(Reward.COMPLETION,Map.of(ItemID.COINS,100L),1,true);
		tracker.loading(2);
		assertEquals(Map.of(ItemID.COINS,9000L,ItemID.CHAOSRUNE,300L),
			tracker.poll(Map.of(ItemID.COINS,9100L,ItemID.CHAOSRUNE,300L),3,false,id->id));
		assertFalse(tracker.pending());
	}
	@Test public void notedOreKeepsItsObservedId()
	{
		MazeRewardTracker tracker=new MazeRewardTracker();tracker.arm(Reward.COMPLETION,Map.of(),1,true);
		// Synthetic noted ID deliberately exercises canonicalization without hardcoding a game ID.
		assertEquals(Map.of(-1,35L),tracker.poll(Map.of(-1,35L),2,true,id->ItemID.MITHRIL_ORE));
	}
	@Test public void walkingUpdatesBaselineAndUnrelatedChangesAreRejected()
	{
		MazeRewardTracker tracker=new MazeRewardTracker();tracker.arm(Reward.CHEST,Map.of(),1,false);
		assertTrue(tracker.poll(Map.of(ItemID.COINS,100L),2,false,id->id).isEmpty());
		assertEquals(Map.of(ItemID.IRON_ARROW,15L),tracker.poll(Map.of(ItemID.COINS,100L,ItemID.IRON_ARROW,15L),3,true,id->id));
		tracker.arm(Reward.CHEST,Map.of(),4,true);
		assertTrue(tracker.poll(Map.of(ItemID.AIRRUNE,15L,ItemID.COINS,100L),5,true,id->id).isEmpty());
		assertFalse(tracker.pending());
		tracker.arm(Reward.CHEST,Map.of(),6,true);
		assertTrue(tracker.poll(Map.of(ItemID.AIRRUNE,14L),7,true,id->id).isEmpty());
	}
	@Test public void cancelledEmptyExpiredAndDisconnectedSelectionsDoNotRecord()
	{
		MazeRewardTracker tracker=new MazeRewardTracker();tracker.arm(Reward.CHEST,Map.of(),1,true);tracker.clear();
		assertTrue(tracker.poll(Map.of(ItemID.AIRRUNE,15L),2,true,id->id).isEmpty());
		tracker.arm(Reward.CHEST,Map.of(),1,true);tracker.loading(2);assertFalse(tracker.pending());
		tracker.arm(Reward.COMPLETION,Map.of(),1,false);tracker.loading(2);assertFalse(tracker.pending());
		tracker.arm(Reward.COMPLETION,Map.of(),1,true);tracker.loading(2);
		assertTrue(tracker.poll(Map.of(),3,false,id->id).isEmpty());
		assertTrue(tracker.poll(Map.of(ItemID.COINS,100L),8,false,id->id).isEmpty());assertFalse(tracker.pending());
		tracker.arm(Reward.CHEST,Map.of(),1,true);
		assertTrue(tracker.poll(Map.of(ItemID.AIRRUNE,15L),102,true,id->id).isEmpty());
	}
	@Test public void completionAndChestsKeepSeparateWikiOwners()
	{
		SourceIdentity completion=new SourceIdentity("EVENT",Reward.COMPLETION.name,"","",Reward.COMPLETION.wikiOwner);
		SourceIdentity chest=new SourceIdentity("EVENT",Reward.CHEST.name,"","",Reward.CHEST.wikiOwner);
		assertEquals("Strange shrine",RewardSources.wikiOwner(completion));assertEquals("Maze reward",RewardSources.displayName(completion));
		assertEquals("Chest (Maze)",RewardSources.wikiOwner(chest));assertNotEquals(completion.family().key(),chest.family().key());
	}
}
