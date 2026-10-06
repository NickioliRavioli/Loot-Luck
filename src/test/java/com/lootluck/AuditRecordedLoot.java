package com.lootluck;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lootluck.model.Ledger;
import com.lootluck.model.SourceIdentity;
import com.lootluck.rates.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Explicit offline development audit; never writes the supplied ledger snapshots. */
public final class AuditRecordedLoot
{
	public static void main(String[] args) throws Exception
	{
		Gson gson=new Gson();
		Map<String,List<RateRow>> rates=gson.fromJson(Files.readString(Path.of("src/main/resources/rates/catalogue.json")),new TypeToken<Map<String,List<RateRow>>>(){}.getType());
		WikiItemIndex items=new WikiItemIndex(gson);WikiEvidence evidence=new WikiEvidence(gson);WikiSourceIndex sources=new WikiSourceIndex(gson);
		com.google.gson.JsonObject witnesses=gson.fromJson(Files.readString(Path.of("tools/wiki-audit/evidence.json")),com.google.gson.JsonObject.class);
		rates.values().forEach(rows->{evidence.apply(rows);items.apply(rows);});
		List<Map<String,Object>> results=new ArrayList<>();int matched=0,unknown=0;
		int verified=0,notCompared=0,conflicts=0;
		for(String input:args)
		{
			Ledger ledger=gson.fromJson(Files.readString(Path.of(input)),Ledger.class);
			for(Ledger.Source source:ledger.sourceVariants())
			{
				SourceIdentity id=sources.resolve(source.identity);String owner=RewardSources.wikiOwner(id);
				TableResolver.Resolution table=TableResolver.resolve(id,rates.getOrDefault(owner,List.of()));
				for(Ledger.Item item:source.items.values())
				{
					List<RateRow> rows=ItemRateMatcher.item(item.id,item.id,item.name,id,table.rows);
					Map<String,Object> entry=new LinkedHashMap<>();entry.put("snapshot",Path.of(input).getFileName().toString());entry.put("source",owner);entry.put("context",id.variant);entry.put("itemId",item.id);entry.put("item",item.name);entry.put("quantity",item.quantity);entry.put("tableResolved",table.resolved);entry.put("reason",table.reason);entry.put("publishedRates",rows.stream().map(r->r.variant+": "+r.display()).distinct().collect(java.util.stream.Collectors.toList()));
					entry.put("status",rows.isEmpty()?"unknown":"matched");results.add(entry);if(rows.isEmpty()) { unknown++; }else { matched++; }
					List<Map<String,Object>> checks=new ArrayList<>();
					for(RateRow row:rows)
					{
						Map<String,Object> check=pageCheck(row,witnesses);checks.add(check);
						if("verified".equals(check.get("status"))) { verified++; }else if("conflict".equals(check.get("status"))) { conflicts++; }else { notCompared++; }
					}
					entry.put("pageChecks",checks);
				}
			}
		}
		Path output=Path.of("build/recorded-loot-audit.json");Files.writeString(output,gson.newBuilder().setPrettyPrinting().create().toJson(results));
		System.out.println("Recorded source/item contexts: "+results.size()+"; matched: "+matched+"; unknown: "+unknown+". Report: "+output);
		System.out.println("Independent rendered-page row checks: "+verified+" verified; "+notCompared+" not compared; "+conflicts+" conflicts.");
		results.stream().filter(row->"unknown".equals(row.get("status"))).forEach(System.out::println);
		if(conflicts>0) { throw new IllegalStateException("Recorded loot has conflicting page rates; inspect the report"); }
	}
	private static Map<String,Object> pageCheck(RateRow actual,com.google.gson.JsonObject witnesses)
	{
		List<String> expected=new ArrayList<>();com.google.gson.JsonObject page=witnesses.getAsJsonObject(actual.owner);
		if(page!=null&&page.has("renderedRows")) { for(com.google.gson.JsonElement value:page.getAsJsonArray("renderedRows"))
		{
			com.google.gson.JsonObject row=value.getAsJsonObject();boolean table=row.get("table").getAsString().equals(actual.variant);
			if(row.has("tables")) { for(com.google.gson.JsonElement candidate:row.getAsJsonArray("tables")) { table|=candidate.getAsString().equals(actual.variant); } }
			if(table&&ItemRateMatcher.normalize(row.get("item").getAsString()).equals(ItemRateMatcher.normalize(actual.itemName))&&quantity(row.get("quantity").getAsString()).equals(quantity(actual.quantity))) { expected.add(row.get("rate").getAsString()); }
		} }
		boolean agrees=expected.stream().anyMatch(raw->{RateRow row=new RateRow();row.rawRate=raw.replaceFirst("^\\d+\\s*[×x]\\s*","");Double p=row.publishedProbability(),q=actual.publishedProbability();return p!=null&&q!=null?Math.abs(p-q)<1e-12:ItemRateMatcher.normalize(raw).equals(ItemRateMatcher.normalize(actual.rawRate));});
		Map<String,Object> result=new LinkedHashMap<>();result.put("table",actual.variant);result.put("quantity",actual.quantity);result.put("publishedRate",actual.rawRate);result.put("pageRates",expected);result.put("status",expected.isEmpty()?"not-compared":agrees?"verified":"conflict");return result;
	}
	private static String quantity(String value)
	{
		return ItemRateMatcher.normalize(value.replace("\u00e2\u20ac\u201c","–").replace("\u00e2\u20ac\u201d","—").replace("\u00c2\u00a0"," "))
			.replaceAll("\\[[^]]*\\]", "").replaceAll("(?<=\\d),(?=\\d{3}(?:\\D|$))", "")
			.replaceAll("\\s*[,;]\\s*", ";").replace('-', '–').replace('—', '–').trim();
	}
}
