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
