package com.lootluck;

import com.google.gson.Gson;
import com.lootluck.model.Ledger;
import com.lootluck.storage.LedgerStore;
import com.lootluck.rates.WikiParser;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class LedgerStoreTest
{
	@Test public void legacySchemaUpgradesWithoutInventingRateHistory() throws Exception
	{
		Filepath path=Filepath.Unchecked.getRooted(folder.getRoot().toPath());
		path.joinSegment(WikiParser.hash("legacy")+".json").write("{\"schema\":1,\"sources\":{}}");
		LedgerStore store=new LedgerStore(path,new Gson());Ledger ledger=store.load("legacy");assertEquals(2,ledger.schema);assertTrue(ledger.sources.isEmpty());store.save("legacy",ledger);assertEquals(2,store.load("legacy").schema);
	}
	@Rule public TemporaryFolder folder=new TemporaryFolder();
	@Test public void isolatedProfilesAndCorruptionRecovery() throws Exception
	{
		Filepath path=Filepath.Unchecked.getRooted(folder.getRoot().toPath());LedgerStore store=new LedgerStore(path,new Gson());
		store.save("A",Fixtures.engine().lifetime());store.save("A",Fixtures.engine().lifetime());store.save("B",new Ledger());
		assertEquals(1,store.load("A").sources.size());assertTrue(store.load("B").sources.isEmpty());
		path.joinSegment(WikiParser.hash("A")+".json").write("{broken");assertEquals(1,store.load("A").sources.size());
		store.save("A",store.load("A"));assertEquals(1,store.load("A").sources.size());
	}
	@Test(expected=java.io.IOException.class) public void corruptWithoutBackupDoesNotEraseHistory() throws Exception
	{
		Filepath path=Filepath.Unchecked.getRooted(folder.getRoot().toPath());path.joinSegment(WikiParser.hash("A")+".json").write("null");new LedgerStore(path,new Gson()).load("A");
	}
}
