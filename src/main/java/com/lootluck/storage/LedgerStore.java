package com.lootluck.storage;

import com.google.gson.Gson;
import com.lootluck.model.Ledger;
import com.lootluck.rates.WikiParser;
import net.runelite.client.util.Filepath;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

/** Worker-thread-only Filepath persistence; a profile key is captured before scheduling writes. */
public final class LedgerStore
{
	private final Filepath directory;
	private final Gson gson;
	public LedgerStore(Filepath directory, Gson gson) throws IOException { this.directory = directory; this.gson = gson; directory.createDirectories(); }
	private Filepath file(String key) { return directory.joinSegment(WikiParser.hash(key) + ".json"); }
	public Ledger load(String key) throws IOException
	{
		Filepath main = file(key), backup = directory.joinSegment(WikiParser.hash(key)+".backup.json");
		if (!main.exists() && !backup.exists()) { return new Ledger(); }
		try { return read(main); } catch (IOException | RuntimeException ex)
		{
			if (backup.exists()) { return read(backup); }
			throw new IOException("Snapshot invalid; original retained. Tracking disabled for this profile until recovery.", ex);
		}
	}
	private Ledger read(Filepath file) throws IOException
	{
		if (!file.exists()) { throw new IOException("Snapshot missing"); }
		try (Reader r = file.openReader()) { Ledger ledger = gson.fromJson(r, Ledger.class); validate(ledger); return ledger; }
	}
	public static void validate(Ledger ledger)
	{
		if (ledger == null || (ledger.schema != 1&&ledger.schema!=2) || ledger.sources == null) { throw new IllegalArgumentException("Unknown snapshot schema"); }
		if(ledger.history==null) { ledger.history=new java.util.ArrayList<>(); }
		for(Ledger.CollectionRecord record:ledger.history) { if(record==null||record.source==null||record.timestamp<0||record.reportedAmount<0||record.quantities==null||record.names==null) { throw new IllegalArgumentException("Invalid collection history"); }record.quantities.forEach((id,quantity)->{if(id<=0||quantity<=0) { throw new IllegalArgumentException("Invalid collection quantity"); }}); }
		ledger.sources.forEach((key,s) -> {
			if (s == null || s.identity == null || !key.equals(s.identity.key()) || s.notifications < 0 || s.reportedAmount < 0 || s.excluded < 0 || s.items == null || s.segments == null) { throw new IllegalArgumentException("Invalid source aggregate"); }
			s.items.forEach((id,i) -> {
				if(i == null || id != i.id || i.quantity < 0 || i.occurrences < 0 || i.occurrences > s.notifications || i.originalIds == null) { throw new IllegalArgumentException("Invalid item aggregate"); }
				if(i.recordedRates==null) { i.recordedRates=new java.util.LinkedHashMap<>(); }
				i.recordedRates.forEach((hash,v)->{if(v==null||!hash.equals(v.hash)||v.rows==null||v.quantity<0||v.occurrences<0||v.quantity>i.quantity||v.occurrences>i.occurrences||v.lastReceived<v.firstReceived) { throw new IllegalArgumentException("Invalid recorded rate history"); }});
			});
			s.segments.forEach((id,seg) -> { if (seg == null || seg.attempts < 0 || seg.probabilities == null || seg.occurrences == null || seg.dry == null || seg.quantities == null) { throw new IllegalArgumentException("Invalid segment"); } seg.probabilities.forEach((item,p)->com.lootluck.stats.Statistics.validate(seg.attempts,seg.occurrences.getOrDefault(item,0L),p)); });
			if (s.activeSegment != null && !s.segments.containsKey(s.activeSegment)) { throw new IllegalArgumentException("Missing active segment"); }
		});
		ledger.schema=2;
	}
	public void save(String key, Ledger ledger) throws IOException
	{
		validate(ledger); Filepath main = file(key), backup = directory.joinSegment(WikiParser.hash(key)+".backup.json");
		Filepath temp = directory.createTempFile("snapshot-", ".tmp");
		try
		{
			temp.write(gson.toJson(ledger));
			if (main.exists()) { try { read(main); main.copyTo(backup, StandardCopyOption.REPLACE_EXISTING); } catch (RuntimeException | IOException invalid) { /* Never overwrite last-good backup with corruption. */ } }
			try { temp.moveTo(main, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
			catch (AtomicMoveNotSupportedException ex) { temp.moveTo(main,StandardCopyOption.REPLACE_EXISTING); }
		}
		finally { temp.deleteIfExists(); }
	}
}
