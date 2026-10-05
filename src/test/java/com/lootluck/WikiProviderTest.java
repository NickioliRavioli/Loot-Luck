package com.lootluck;

import com.google.gson.Gson;
import com.lootluck.model.SourceIdentity;
import com.lootluck.rates.WikiRateProvider;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.MediaType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class WikiProviderTest
{
	@Test public void rewardAliasesResolveBundledTablesWithoutChangingHistory() throws Exception
	{
		try(WikiRateProvider provider=new WikiRateProvider(new Gson(),new OkHttpClient(),Filepath.Unchecked.getRooted(folder.getRoot().toPath()),()->false,()->{}))
		{
			provider.initialize();
			java.util.Map<String,String> aliases=new java.util.LinkedHashMap<>();
			for(String tier:new String[]{"beginner","easy","medium","hard","elite","master"}) { aliases.put("Clue Scroll ("+tier.substring(0,1).toUpperCase(java.util.Locale.ROOT)+tier.substring(1)+")","Reward casket ("+tier+")"); }
			aliases.put("Reward cart (Wintertodt)","Reward Cart");aliases.put("Reward pool (Tempoross)","Reward pool");aliases.put("Casket (Tempoross)","Casket (Reward pool)");aliases.put("Guardians of the Rift","Rewards Guardian");
			for(java.util.Map.Entry<String,String> alias:aliases.entrySet())
			{
				SourceIdentity reward=new SourceIdentity("EVENT",alias.getKey(),"","normal");
				assertEquals(alias.getValue(),WikiRateProvider.owner(reward));assertFalse(provider.rows(reward).isEmpty());assertTrue(provider.rows(reward).stream().allMatch(row->alias.getValue().equals(row.owner)));
				com.lootluck.model.Ledger ledger=new com.lootluck.model.Ledger();com.lootluck.tracking.LedgerEngine engine=new com.lootluck.tracking.LedgerEngine(ledger);
				engine.observe(new com.lootluck.model.Observation(reward,1,1,java.util.Map.of(1,1L),java.util.Map.of(1,"Test item"),java.util.Map.of(1,1)),com.lootluck.tracking.AttemptAdapter.Assessment.unsupported("pending"));
				com.lootluck.ui.PanelState view=com.lootluck.ui.ViewBuilder.build(engine.lifetime(),provider,false,java.util.Map.of());
				assertEquals(alias.getValue(),view.sources.get(0).name);assertEquals(alias.getValue(),view.collections.get(0).name);assertEquals(alias.getKey(),engine.lifetime().source(reward).identity.name);assertEquals(1,view.sources.get(0).count);
				assertEquals(alias.getValue().replace(' ','_'),java.net.URLDecoder.decode(view.sources.get(0).wikiUrl.substring("https://oldschool.runescape.wiki/w/".length()),java.nio.charset.StandardCharsets.UTF_8));
			}
			for(String name:new String[]{"Intricate pouch","Extra supply crate","Supply crate (Wintertodt)","Supply crate (Mahogany Homes)","Casket"}) { assertEquals(name,WikiRateProvider.owner(new SourceIdentity("EVENT",name,"","normal"))); }
			assertFalse(provider.rows(new SourceIdentity("EVENT","Intricate pouch","","normal")).isEmpty());
			assertEquals("Clue Scroll (Easy)",WikiRateProvider.owner(new SourceIdentity("NPC","Clue Scroll (Easy)","","normal")));
			assertNull(WikiRateProvider.owner(new SourceIdentity("PLAYER","Guardians of the Rift","","normal")));
		}
	}
	@Test public void broadOfflineCoverageAndMalformedWholeRefresh() throws Exception
	{
		CompletableFuture<Void> done=new CompletableFuture<>();
		OkHttpClient http=new OkHttpClient.Builder().addInterceptor(chain->new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create(MediaType.parse("application/json"),"{\"bucket\":[]}")).build()).build();
		try(WikiRateProvider provider=new WikiRateProvider(new Gson(),http,Filepath.Unchecked.getRooted(folder.getRoot().toPath()),()->true,()->done.complete(null)))
		{
			provider.initialize();SourceIdentity small=new SourceIdentity("EVENT","Small salvage","","normal");assertFalse(provider.rows(small).isEmpty());String hash=provider.rows(small).get(0).hash;
			assertEquals("Small salvage",WikiRateProvider.owner(small));provider.refreshCatalogue(true);done.get(5,TimeUnit.SECONDS);assertEquals(hash,provider.rows(small).get(0).hash);assertTrue(provider.status.contains("last-good"));
		}
	}
	@Rule public TemporaryFolder folder=new TemporaryFolder();
	private final SourceIdentity source=new SourceIdentity("EVENT","Large salvage","","normal");
	@Test public void offlineCoalescingRefreshAndCacheRecovery() throws Exception
	{
		AtomicBoolean enabled=new AtomicBoolean(false);AtomicInteger calls=new AtomicInteger();
		CompletableFuture<Void> started=new CompletableFuture<>(),release=new CompletableFuture<>(),done=new CompletableFuture<>();
		String body=Files.readString(Path.of("tools/wiki-inputs/Large-salvage.json"));
		OkHttpClient http=new OkHttpClient.Builder().addInterceptor(chain->{
			assertEquals("oldschool.runescape.wiki",chain.request().url().host());assertFalse(Thread.currentThread().getName().contains("AWT"));
			assertEquals("bucket",chain.request().url().queryParameter("action"));calls.incrementAndGet();started.complete(null);
			try { release.get(5,TimeUnit.SECONDS); } catch(Exception e) { throw new java.io.IOException(e); }
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create(MediaType.parse("application/json"),body)).build();
		}).build();
		Filepath cache=Filepath.Unchecked.getRooted(folder.getRoot().toPath());
		try(WikiRateProvider provider=new WikiRateProvider(new Gson(),http,cache,enabled::get,()->done.complete(null)))
		{
			provider.initialize();assertEquals(23,provider.rows(source).size());provider.refresh(source,true);assertEquals(0,calls.get());
			enabled.set(true);provider.refresh(source,true);started.get(5,TimeUnit.SECONDS);provider.refresh(source,true);release.complete(null);done.get(5,TimeUnit.SECONDS);assertEquals(1,calls.get());
			provider.refresh(source,false);assertEquals(1,calls.get());
		}
		try(WikiRateProvider provider=new WikiRateProvider(new Gson(),http,cache,()->false,()->{})) { provider.initialize();assertEquals(23,provider.rows(source).size());assertTrue(provider.rows(source).get(0).fetchedAt.startsWith(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString())); }
	}
	@Test public void schemaFailureKeepsLastGoodRates() throws Exception
	{
		CompletableFuture<Void> done=new CompletableFuture<>();
		OkHttpClient http=new OkHttpClient.Builder().addInterceptor(chain->new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create(MediaType.parse("application/json"),"{\"bucket\":[]}")).build()).build();
		try(WikiRateProvider provider=new WikiRateProvider(new Gson(),http,Filepath.Unchecked.getRooted(folder.getRoot().toPath()),()->true,()->done.complete(null)))
		{ provider.initialize();String hash=provider.rows(source).get(0).hash;provider.refresh(source,true);done.get(5,TimeUnit.SECONDS);assertEquals(hash,provider.rows(source).get(0).hash);assertTrue(provider.status.contains("last-good")); }
	}
}
