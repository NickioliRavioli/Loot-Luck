package com.lootluck;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lootluck.rates.RateRow;
import com.lootluck.rates.WikiParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Development-only offline generator. Input: preserved primary Bucket API responses. */
public final class GenerateRates
{
	public static void main(String[] args) throws Exception
	{
		if(args.length!=3) { throw new IllegalArgumentException("input-directory output-file fetched-at-ISO8601"); }
		Gson gson=new GsonBuilder().setPrettyPrinting().create();
		Map<String,List<RateRow>> sources=new LinkedHashMap<>();
		Path full=Path.of(args[0],"full-catalogue.json");
		if(Files.exists(full))
		{
			com.google.gson.JsonObject export=gson.fromJson(Files.readString(full),com.google.gson.JsonObject.class);
			Map<String,com.google.gson.JsonArray> groups=new LinkedHashMap<>();
			for(com.google.gson.JsonElement entry:export.getAsJsonArray("bucket")) { String owner=entry.getAsJsonObject().get("page_name").getAsString();groups.computeIfAbsent(owner,k->new com.google.gson.JsonArray()).add(entry); }
			for(Map.Entry<String,com.google.gson.JsonArray> group:groups.entrySet()) { com.google.gson.JsonObject body=new com.google.gson.JsonObject();body.add("bucket",group.getValue());sources.put(group.getKey(),WikiParser.parse(gson,body.toString(),group.getKey(),export.get("fetchedAt").getAsString())); }
		}
		else
		{
		for(String owner:List.of("Large salvage","Goblin","Vorkath","Zulrah"))
		{
			List<RateRow> rows=WikiParser.parse(gson,Files.readString(Path.of(args[0],owner.replace(' ','-')+".json")),owner,args[2]);
			sources.put(owner,rows);System.out.println(owner+": "+rows.size()+" retained rows");
		}
		}
		com.lootluck.rates.WikiEvidence evidence=new com.lootluck.rates.WikiEvidence(gson);
		com.lootluck.rates.WikiItemIndex items=new com.lootluck.rates.WikiItemIndex(gson);
		sources.values().forEach(rows->{evidence.apply(rows);items.apply(rows);});
		Path target=Path.of(args[1]);Files.createDirectories(target.getParent());Files.writeString(target,gson.toJson(sources));
		System.out.println("Catalogue: "+sources.size()+" sources, "+sources.values().stream().mapToInt(List::size).sum()+" rows");
	}
}
