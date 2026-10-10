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
	@Rule public TemporaryFolder folder=new TemporaryFolder();
	@Test public void cachedRecoveryUsesImmutablePreviousSnapshotEvenAfterMainCorruption() throws Exception
	{
		Filepath path=Filepath.Unchecked.getRooted(folder.getRoot().toPath());LedgerStore store=new LedgerStore(path,new Gson());Ledger ledger=new Ledger();
		store.save("A",ledger);
		Ledger.CollectionRecord record=new Ledger.CollectionRecord();record.source=new com.lootluck.model.SourceIdentity("NPC","Cow","","");record.timestamp=1;ledger.history.add(record);store.save("A",ledger);
		Ledger.CollectionRecord second=new Ledger.CollectionRecord();second.source=record.source;second.timestamp=2;ledger.history.add(second);
		path.joinSegment(WikiParser.hash("A")+".json").write("{broken");store.save("A",ledger);
		assertEquals(2,store.load("A").history.size());path.joinSegment(WikiParser.hash("A")+".json").write("{broken");
		Ledger recovered=new LedgerStore(path,new Gson()).load("A");assertEquals(1,recovered.history.size());assertEquals(1,recovered.history.get(0).timestamp);
	}
	@Test public void coldSaveCannotReplaceGoodBackupWithCorruptMain() throws Exception
	{
		Filepath path=Filepath.Unchecked.getRooted(folder.getRoot().toPath());LedgerStore store=new LedgerStore(path,new Gson());Ledger ledger=Fixtures.engine().lifetime();store.save("A",ledger);store.save("A",ledger);
		path.joinSegment(WikiParser.hash("A")+".json").write("{broken");new LedgerStore(path,new Gson()).save("A",new Ledger());
		path.joinSegment(WikiParser.hash("A")+".json").write("{broken");assertEquals(ledger.history.size(),new LedgerStore(path,new Gson()).load("A").history.size());
	}
	@Test public void exportsRoundTripAndRejectOtherProfilesOrMalformedFiles() throws Exception
	{
		Filepath path=Filepath.Unchecked.getRooted(folder.getRoot().toPath());LedgerStore store=new LedgerStore(path,new Gson());Filepath export=path.joinSegment("export.json");
		Ledger original=Fixtures.engine().lifetime();store.save("A",original);store.exportData("A",original,export);
		try(java.io.Reader reader=export.openReader()) { StringBuilder text=new StringBuilder();char[] buffer=new char[1024];int count;while((count=reader.read(buffer))!=-1) { text.append(buffer,0,count); }assertTrue(text.toString().contains("\n  \"format\": 1"));assertTrue(text.toString().contains("\n  \"ledger\": {")); }
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

