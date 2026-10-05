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
	@Test public void exportsRoundTripAndRejectOtherProfilesOrMalformedFiles() throws Exception
	{
		Filepath path=Filepath.Unchecked.getRooted(folder.getRoot().toPath());LedgerStore store=new LedgerStore(path,new Gson());Filepath export=path.joinSegment("export.json");
		Ledger original=Fixtures.engine().lifetime();store.save("A",original);store.exportData("A",original,export);
		Ledger imported=store.importData("A",export);assertEquals(original.sources.size(),imported.sources.size());assertEquals(original.history.size(),imported.history.size());
		try { store.importData("B",export);fail("Wrong profile accepted"); }catch(java.io.IOException expected) { assertTrue(expected.getMessage().contains("account")); }
		store.backupBeforeImport("A",original);assertTrue(path.joinSegment(WikiParser.hash("A")+".before-import.json").exists());
		export.write("{broken");try { store.importData("A",export);fail("Malformed import accepted"); }catch(java.io.IOException expected) { assertEquals(original.sources.size(),store.load("A").sources.size()); }
	}
	@Test public void deletionCannotRecoverOldBackupAndLeavesOtherAccountsAndExports() throws Exception
	{
		Filepath path=Filepath.Unchecked.getRooted(folder.getRoot().toPath());LedgerStore store=new LedgerStore(path,new Gson());Ledger original=Fixtures.engine().lifetime();
		store.save("A",original);store.save("A",original);store.save("B",original);Filepath export=path.joinSegment("export.json");store.exportData("A",original,export);
		store.deleteData("A");assertTrue(store.load("A").sources.isEmpty());assertFalse(path.joinSegment(WikiParser.hash("A")+".backup.json").exists());assertEquals(original.sources.size(),store.load("B").sources.size());assertEquals(original.sources.size(),store.importData("A",export).sources.size());
		store.save("A",store.importData("A",export));assertEquals(original.sources.size(),store.load("A").sources.size());
	}
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
