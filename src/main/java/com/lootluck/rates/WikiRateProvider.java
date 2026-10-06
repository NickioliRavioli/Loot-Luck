package com.lootluck.rates;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lootluck.model.SourceIdentity;
import net.runelite.client.util.Filepath;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Broad offline catalogue, serialized background networking and last-good fallback. */
public final class WikiRateProvider implements RateProvider, AutoCloseable
{
	private static final Logger log = LoggerFactory.getLogger(WikiRateProvider.class);
	private static final long TTL = TimeUnit.DAYS.toMillis(7);
	private static final int LIMIT = 4*1024*1024;
	private final Gson gson;
	private final WikiEvidence evidence;
	private final WikiItemIndex itemIndex;
	private final OkHttpClient http;
	private final Filepath cache;
	private final BooleanSupplier enabled;
	private final Runnable changed;
	private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r,"loot-luck-wiki"); t.setDaemon(true); return t; });
	private final Map<String,List<RateRow>> catalogue = new ConcurrentHashMap<>();
	private final Set<String> pending = ConcurrentHashMap.newKeySet();
	private final Set<ScheduledFuture<?>> futures = ConcurrentHashMap.newKeySet();
	private volatile Call currentCall;
	private volatile boolean closed;
	private long nextRequest;
	private static final String FULL="__full_catalogue__";
	private final Map<String,List<RateRow>> staging=new java.util.LinkedHashMap<>();
	private int catalogueOffset;
	private String catalogueFetched;
	private volatile boolean resetCatalogue;
	private volatile long catalogueRetryAfter;
	private final Map<String,Long> lookupRetryAfter=new ConcurrentHashMap<>();
	public volatile String status = "Bundled Wiki data";
	public WikiRateProvider(Gson gson, OkHttpClient http, Filepath cache, BooleanSupplier enabled, Runnable changed)
	{
		this.gson=gson; this.http=http.newBuilder().callTimeout(15,TimeUnit.SECONDS).build(); this.cache=cache; this.enabled=enabled; this.changed=changed;
		evidence = new WikiEvidence(gson); itemIndex = new WikiItemIndex(gson);
	}
	public void initialize() throws IOException
	{
		cache.createDirectories();
		try (Reader reader = new InputStreamReader(java.util.Objects.requireNonNull(getClass().getResourceAsStream("/rates/catalogue.json")),StandardCharsets.UTF_8))
		{
			Map<String,List<RateRow>> bundle = gson.fromJson(reader,new TypeToken<Map<String,List<RateRow>>>(){}.getType());
			bundle.forEach((owner,rows) -> catalogue.put(owner, Collections.unmodifiableList(rows)));
		}
		Filepath complete=cache.joinSegment("catalogue.json");
		if(complete.exists()&&complete.size()<=64L*1024*1024)
		{
			try(Reader reader=complete.openReader())
			{
				Map<String,List<RateRow>> saved=gson.fromJson(reader,new TypeToken<Map<String,List<RateRow>>>(){}.getType());
				validateCatalogue(saved);saved.forEach((owner,rows)->catalogue.put(owner,Collections.unmodifiableList(rows)));
			}
			catch(RuntimeException|IOException ex) { log.debug("Ignoring invalid catalogue cache",ex); }
		}
		for(String owner: new ArrayList<>(catalogue.keySet()))
		{
			Filepath path = cache.joinSegment(WikiParser.hash(owner)+".json");
			if(!path.exists()) { continue; }
			try(Reader r=path.openReader())
			{
				if(path.size()>LIMIT) { continue; }
				CacheEntry entry=gson.fromJson(r,CacheEntry.class);
				List<RateRow> rows=WikiParser.parse(gson,entry.body,owner,entry.fetchedAt);
				List<RateRow> existing=catalogue.get(owner);
				if(existing==null||existing.isEmpty()||Instant.parse(rows.get(0).fetchedAt).isAfter(Instant.parse(existing.get(0).fetchedAt))) { catalogue.put(owner,Collections.unmodifiableList(rows)); }
			}
			catch(RuntimeException|IOException ex) { log.debug("Ignoring invalid Wiki cache for {}",owner,ex); }
		}
		catalogue.values().forEach(this::enrich);
		refreshCatalogue(false);
	}
	private void enrich(List<RateRow> rows) { evidence.apply(rows); itemIndex.apply(rows); }
	public static String owner(SourceIdentity source)
	{
		if("PLAYER".equals(source.type)) { return null; }
		return source.name==null||source.name.isBlank()||source.name.length()>200||source.name.chars().anyMatch(Character::isISOControl)?null:RewardSources.wikiOwner(source);
	}
	public void refreshCatalogue(boolean force)
	{
		if(closed||!enabled.getAsBoolean()) { return; }
		if(!force&&System.currentTimeMillis()<catalogueRetryAfter) { return; }
		long oldest=catalogue.values().stream().filter(rows->!rows.isEmpty()).mapToLong(rows->Instant.parse(rows.get(0).fetchedAt).toEpochMilli()).min().orElse(0);
		if(!force&&System.currentTimeMillis()-oldest<TTL) { return; }
		if(pending.add(FULL)) { schedule(FULL,0,0); }
	}
	@Override public List<RateRow> rows(SourceIdentity source) { String owner=owner(source); return owner==null?List.of():catalogue.getOrDefault(owner,List.of()); }
	@Override public void refresh(SourceIdentity source,boolean force)
	{
		refreshCatalogue(false);
		String owner=owner(source);
		if(owner==null||closed||!enabled.getAsBoolean()||pending.size()>=32) { return; }
		if(!force&&System.currentTimeMillis()<lookupRetryAfter.getOrDefault(owner,0L)) { return; }
		List<RateRow> rows=catalogue.get(owner);
		if(!force && rows!=null && !rows.isEmpty() && System.currentTimeMillis()-Instant.parse(rows.get(0).fetchedAt).toEpochMilli()<TTL) { return; }
		if(pending.add(owner)) { schedule(owner,0,0); }
	}
	private void schedule(String owner,int retry,long delay)
	{
		if(closed) { pending.remove(owner); return; }
		futures.removeIf(java.util.concurrent.Future::isDone);
		try { futures.add(worker.schedule(()->request(owner,retry),delay,TimeUnit.MILLISECONDS)); }
		catch(java.util.concurrent.RejectedExecutionException stopped) { pending.remove(owner); }
	}
	private void request(String owner,int retry)
	{
		if(closed||!enabled.getAsBoolean()) { pending.remove(owner); return; }
		long wait=nextRequest-System.currentTimeMillis();
		if(wait>0) { schedule(owner,retry,wait); return; }
		nextRequest=System.currentTimeMillis()+1000;
		if(FULL.equals(owner)) { requestCataloguePage(retry);return; }
		String escaped=owner.replace("\\","\\\\").replace("'","\\'");
		String query="bucket('dropsline').select('page_name','page_name_sub','item_name','drop_json','rare_drop_table').where('page_name','"+escaped+"').limit(500).run()";
		HttpUrl url=HttpUrl.parse("https://oldschool.runescape.wiki/api.php").newBuilder().addQueryParameter("action","bucket").addQueryParameter("format","json").addQueryParameter("query",query).build();
		Call call=http.newCall(new Request.Builder().url(url).header("User-Agent","LootLuck/0.1.0 RuneLite plugin; read-only Wiki rate lookup").build()); currentCall=call;
		boolean retryScheduled=false;
		try(Response response=call.execute())
		{
			if(!response.isSuccessful())
			{
				if((response.code()==429||response.code()>=500)&&retry<2)
				{
					long backoff=(long)Math.pow(2,retry)*5000;
					try { backoff=Math.max(backoff,Math.min(600000,Long.parseLong(response.header("Retry-After","0"))*1000)); } catch(NumberFormatException ignored) { }
					status="Wiki busy; using last-good data"; schedule(owner,retry+1,backoff);retryScheduled=true; return;
				}
				throw new IOException("Wiki HTTP "+response.code());
			}
			if(response.body()==null || response.body().contentLength()>LIMIT) { throw new IOException("Missing/oversized Wiki body"); }
			okio.Buffer buffer=new okio.Buffer(); long count=0,n;
			while((n=response.body().source().read(buffer,Math.min(8192,LIMIT+1-count)))!=-1) { count+=n; if(count>LIMIT) { throw new IOException("Wiki response limit"); } }
			String body=buffer.readString(StandardCharsets.UTF_8), fetched=Instant.now().toString();
			List<RateRow> rows=WikiParser.parse(gson,body,owner,fetched);
			enrich(rows);
			if(rows.size()==500) { throw new IOException("Potentially truncated source table"); }
			if(closed||!enabled.getAsBoolean()) { return; }
			CacheEntry entry=new CacheEntry();entry.body=body;entry.fetchedAt=fetched;
			Filepath temp=cache.createTempFile("wiki-",".tmp");
			try { temp.write(gson.toJson(entry));temp.moveTo(cache.joinSegment(WikiParser.hash(owner)+".json"),StandardCopyOption.REPLACE_EXISTING); }
			finally { temp.deleteIfExists(); }
			// The source response is already persisted above. Do not rewrite 40k rows for one lookup.
			catalogue.put(owner,Collections.unmodifiableList(rows));status="Wiki refreshed "+owner;changed.run();
			lookupRetryAfter.remove(owner);
		}
		catch(IOException|RuntimeException ex)
		{
			status="Wiki unavailable; using last-good data";log.debug("Wiki refresh failed for {}",owner,ex);
			if(ex instanceof IOException && !closed && enabled.getAsBoolean() && retry<2) { schedule(owner,retry+1,(long)Math.pow(2,retry)*5000);retryScheduled=true; }
			else { lookupRetryAfter.put(owner,System.currentTimeMillis()+TimeUnit.MINUTES.toMillis(15)); }
			changed.run();
		}
		finally { currentCall=null;if(!retryScheduled) { pending.remove(owner); } }
	}
	private void requestCataloguePage(int retry)
	{
		boolean again=false,received=false;
		try
		{
			if(resetCatalogue) { catalogueOffset=0;staging.clear();resetCatalogue=false; }
			if(catalogueOffset==0) { staging.clear();catalogueFetched=Instant.now().toString(); }
			String query="bucket('dropsline').select('page_name','page_name_sub','item_name','drop_json','rare_drop_table').orderBy('page_name','asc').limit(500).offset("+catalogueOffset+").run()";
			HttpUrl url=HttpUrl.parse("https://oldschool.runescape.wiki/api.php").newBuilder().addQueryParameter("action","bucket").addQueryParameter("format","json").addQueryParameter("query",query).build();
			Call call=http.newCall(new Request.Builder().url(url).header("User-Agent","LootLuck/0.1.0 read-only Wiki catalogue refresh").build());currentCall=call;
			com.google.gson.JsonArray page;
			try(Response response=call.execute())
			{
				if(!response.isSuccessful()||response.body()==null||response.body().contentLength()>LIMIT) { throw new IOException("Catalogue response rejected"); }
				okio.Buffer buffer=new okio.Buffer();long count=0,n;
				while((n=response.body().source().read(buffer,Math.min(8192,LIMIT+1-count)))!=-1) { count+=n;if(count>LIMIT) { throw new IOException("Catalogue response limit"); } }
				com.google.gson.JsonObject body=gson.fromJson(buffer.readString(StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
				if(body==null||!body.has("bucket")||!body.get("bucket").isJsonArray()) { throw new IOException("Malformed catalogue page"); }
				page=body.getAsJsonArray("bucket");if(page.size()>500) { throw new IOException("Oversized catalogue page"); }
			}
			received=true;
			Map<String,com.google.gson.JsonArray> groups=new java.util.LinkedHashMap<>();
			for(com.google.gson.JsonElement row:page) { String owner=row.getAsJsonObject().get("page_name").getAsString();groups.computeIfAbsent(owner,k->new com.google.gson.JsonArray()).add(row); }
			for(Map.Entry<String,com.google.gson.JsonArray> group:groups.entrySet()) { com.google.gson.JsonObject body=new com.google.gson.JsonObject();body.add("bucket",group.getValue());staging.computeIfAbsent(group.getKey(),k->new ArrayList<>()).addAll(WikiParser.parse(gson,body.toString(),group.getKey(),catalogueFetched)); }
			catalogueOffset+=page.size();if(catalogueOffset>=100000) { throw new IOException("Catalogue row bound"); }
			if(closed||!enabled.getAsBoolean()) { return; }
			if(page.size()==500) { status="Catalogue refresh: "+catalogueOffset+" rows";again=true;schedule(FULL,0,1000); }
			else
			{
				int prior=catalogue.values().stream().mapToInt(List::size).sum();
				if(catalogueOffset<prior*0.8) { throw new IOException("Catalogue unexpectedly shrank; last-good retained for review"); }
				validateCatalogue(staging);staging.values().forEach(this::enrich);saveCatalogue(staging);
				staging.forEach((owner,rows)->catalogue.put(owner,Collections.unmodifiableList(new ArrayList<>(rows))));
				status="Wiki catalogue refreshed: "+staging.size()+" sources";
				catalogueRetryAfter=0;
			}
		}
		catch(IOException|RuntimeException ex) { catalogueRetryAfter=System.currentTimeMillis()+TimeUnit.MINUTES.toMillis(15);status="Catalogue refresh failed; last-good data retained";log.debug(status,ex);if(ex instanceof IOException&&!received&&!closed&&enabled.getAsBoolean()&&retry<2) { again=true;schedule(FULL,retry+1,5000L*(1L<<retry)); } }
		finally { currentCall=null;if(!again) { pending.remove(FULL);catalogueOffset=0;staging.clear(); }changed.run(); }
	}
	private static void validateCatalogue(Map<String,List<RateRow>> data)
	{
		if(data==null||data.isEmpty()||data.size()>20000) { throw new IllegalArgumentException("Invalid catalogue"); }
		data.forEach((owner,rows)->{if(owner==null||rows==null||rows.isEmpty()||rows.size()>10000) { throw new IllegalArgumentException("Invalid catalogue owner"); }for(RateRow row:rows) { if(row==null||!owner.equals(row.owner)||row.itemName==null||row.rawRate==null||row.hash==null||row.mechanics==null) { throw new IllegalArgumentException("Invalid catalogue row"); }Instant.parse(row.fetchedAt); }});
	}
	private void saveCatalogue(Map<String,List<RateRow>> data) throws IOException
	{
		String json=gson.toJson(data);if(json.getBytes(StandardCharsets.UTF_8).length>64L*1024*1024) { throw new IOException("Catalogue cache limit"); }
		Filepath temp=cache.createTempFile("catalogue-",".tmp");
		try { temp.write(json);temp.moveTo(cache.joinSegment("catalogue.json"),StandardCopyOption.REPLACE_EXISTING); }finally { temp.deleteIfExists(); }
	}
	public void cancelRequests() { resetCatalogue=true;Call call=currentCall;if(call!=null) { call.cancel(); } futures.forEach(f->f.cancel(false));futures.clear();pending.clear(); }
	@Override public void close() { closed=true;cancelRequests();worker.shutdownNow(); }
	private static final class CacheEntry { String fetchedAt,body; }
}
