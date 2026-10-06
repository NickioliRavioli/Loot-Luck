package com.lootluck;

import com.lootluck.model.SourceIdentity;
import com.lootluck.tracking.NpcDeathContexts;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class NpcDeathContextsTest
{
	@Test public void capturesDeathLocationBeforePlayerMovesAndRejectsAmbiguity()
	{
		NpcDeathContexts deaths=new NpcDeathContexts();WorldPoint point=new WorldPoint(3272,3664,0);
		deaths.record(1,85,10,point,true);
		assertTrue(deaths.consume(85,12).contains("wilderness=true"));assertEquals("",deaths.consume(85,12));
		deaths.record(1,85,13,point,true);deaths.record(2,85,13,new WorldPoint(2900,9819,0),false);
		assertEquals("",deaths.consume(85,14));assertEquals("",deaths.consume(85,14));
		deaths.record(1,85,15,point,true);assertEquals("",deaths.consume(85,21));
	}
	@Test public void recognizedAreasResolveButBoundaryInstancesAndSafeAreasDoNot()
	{
		assertEquals(Boolean.FALSE,NpcDeathContexts.wilderness(new WorldPoint(2900,9819,0),new WorldPoint(2901,9819,0),false,false));
		assertEquals(Boolean.TRUE,NpcDeathContexts.wilderness(new WorldPoint(3272,3664,0),new WorldPoint(3271,3664,0),true,false));
		assertNull(NpcDeathContexts.wilderness(new WorldPoint(3100,3520,0),new WorldPoint(3100,3519,0),false,false));
		assertNull(NpcDeathContexts.wilderness(new WorldPoint(3130,3620,0),new WorldPoint(3130,3621,0),true,false));
		assertNull(NpcDeathContexts.wilderness(new WorldPoint(3272,3664,0),new WorldPoint(3271,3664,0),true,true));
		assertNull(NpcDeathContexts.wilderness(new WorldPoint(3000,10000,0),new WorldPoint(3000,10001,0),true,false));
	}
	@Test public void rawPositionsNeverCreateExtraSourceOrApprovalKeys()
	{
		SourceIdentity a=new SourceIdentity("NPC","Ghost","combat=19;metadata=85;wilderness=true","[MEMBERS]","","3272,3664,0");
		SourceIdentity b=new SourceIdentity("NPC","Ghost",a.variant,a.context,"","3275,3664,0");
		assertEquals(a.key(),b.key());assertEquals(a.family().key(),b.family().key());
	}
}
