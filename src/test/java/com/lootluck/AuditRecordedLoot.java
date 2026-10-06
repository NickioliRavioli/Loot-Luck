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
		rates.values().forEach(rows->{evidence.apply(rows);items.apply(rows);});
		List<Map<String,Object>> results=new ArrayList<>();int matched=0,unknown=0;
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
				}
			}
		}
		Path output=Path.of("build/recorded-loot-audit.json");Files.writeString(output,gson.newBuilder().setPrettyPrinting().create().toJson(results));
		System.out.println("Recorded source/item contexts: "+results.size()+"; matched: "+matched+"; unknown: "+unknown+". Report: "+output);
		results.stream().filter(row->"unknown".equals(row.get("status"))).forEach(System.out::println);
	}
}
