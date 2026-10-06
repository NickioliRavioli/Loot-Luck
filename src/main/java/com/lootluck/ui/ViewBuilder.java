package com.lootluck.ui;

import com.lootluck.model.Ledger;
import com.lootluck.rates.RateProvider;
import com.lootluck.rates.RateRow;
import com.lootluck.stats.Statistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.awt.Color;

public final class ViewBuilder
{
	private ViewBuilder() { }
	static String sourceWikiUrl(String owner)
	{
		String normalized=owner.toLowerCase(java.util.Locale.ROOT);
		if(normalized.matches("clue scroll \\((beginner|easy|medium|hard|elite|master)\\)"))
		{
			owner="Clue scroll"+normalized.substring("clue scroll".length());
		}
		return "https://oldschool.runescape.wiki/w/"+java.net.URLEncoder.encode(owner.replace(' ','_'),java.nio.charset.StandardCharsets.UTF_8).replace("+","%20");
	}
	public static PanelState build(Ledger ledger,RateProvider rates,boolean unobtained,Map<String,Integer> identities)
	{
		return build(ledger,rates,unobtained,identities,false,null);
	}
	public static PanelState build(Ledger ledger,RateProvider rates,boolean unobtained,Map<String,Integer> identities,boolean hideOtherWorld,Boolean membersWorld)
	{
		return build(ledger,rates,unobtained,identities,hideOtherWorld,membersWorld,false);
	}
	public static PanelState build(Ledger ledger,RateProvider rates,boolean unobtained,Map<String,Integer> identities,boolean hideOtherWorld,Boolean membersWorld,boolean separateVariants)
	{
		PanelState state=new PanelState();state.notice="";
		for(Map.Entry<String,Ledger.Source> entry:SourceGroups.build(ledger).entrySet())
		{
			Ledger.Source s=entry.getValue();PanelState.Source family=sourceView(entry.getKey(),s,rates,unobtained,identities,hideOtherWorld,membersWorld);
			if(com.lootluck.rates.SourceConditions.hasVariants(rates.rows(s.identity)))
			{
				Map<String,Ledger.Source> groups=SourceGroups.conditions(ledger,entry.getKey(),rates);
				boolean multipleWorlds=groups.values().stream().map(value->value.identity.context).distinct().count()>1;
				for(Map.Entry<String,Ledger.Source> group:groups.entrySet())
				{
					Ledger.Source member=group.getValue();PanelState.Source child=sourceView(group.getKey(),member,rates,unobtained,identities,hideOtherWorld,membersWorld);
					String label=com.lootluck.rates.SourceConditions.label(member.identity,rates.rows(member.identity));
					if(multipleWorlds) { label+=" / "+("[MEMBERS]".equals(member.identity.context)?"Members":"[]".equals(member.identity.context)?"Free-to-play":"Special/unknown world"); }
					child.name=separateVariants?family.name+" ("+label+")":label;family.children.add(child);
				}
				family.children.sort(Comparator.comparingLong((PanelState.Source value)->value.lastReceived).reversed());
			}
			if(separateVariants&&!family.children.isEmpty()) { state.sources.addAll(family.children); }else { state.sources.add(family); }
		}
		state.sources.sort(Comparator.comparingLong((PanelState.Source s)->s.lastReceived).reversed());
		Map<Integer,PanelState.Item> totals=new LinkedHashMap<>();
		for(Ledger.Source source:ledger.sourceVariants()) { for(Ledger.Item item:source.items.values()) { PanelState.Item total=totals.computeIfAbsent(item.id,id->{PanelState.Item i=new PanelState.Item();i.id=id;i.key="all:"+id;i.name=item.name;return i;});total.quantity=Math.addExact(total.quantity,item.quantity);total.occurrences=Math.addExact(total.occurrences,item.occurrences);total.lastReceived=Math.max(total.lastReceived,item.lastReceived); } }
		state.allItems.addAll(totals.values());state.allItems.sort(Comparator.comparingLong((PanelState.Item i)->i.lastReceived).reversed());
		for(int index=ledger.history.size()-1;index>=0;index--)
		{
			Ledger.CollectionRecord record=ledger.history.get(index);PanelState.Source collection=new PanelState.Source();collection.key="collection:"+index;collection.name=com.lootluck.rates.RewardSources.displayName(record.source);collection.count=record.reportedAmount;collection.lastReceived=record.timestamp;collection.countTooltip="One recorded notification; reported amount "+record.reportedAmount+". Not automatically individual attempts.";
			for(Map.Entry<Integer,Long> item:record.quantities.entrySet()) { PanelState.Item row=new PanelState.Item();row.id=item.getKey();row.key=collection.key+":"+row.id;row.name=record.names.getOrDefault(row.id,"Item "+row.id);row.quantity=item.getValue();row.occurrences=1;row.lastReceived=record.timestamp;collection.items.add(row); }
			state.collections.add(collection);
		}
		state.collections.sort(Comparator.comparingLong((PanelState.Source s)->s.lastReceived).reversed());return state;
	}
	private static PanelState.Source sourceView(String sourceKey,Ledger.Source s,RateProvider rates,boolean unobtained,Map<String,Integer> identities,boolean hideOtherWorld,Boolean membersWorld)
	{
			PanelState.Source source=new PanelState.Source();source.key=sourceKey;source.familyKey=s.identity.family().key();source.name=com.lootluck.rates.RewardSources.displayName(s.identity);
			com.lootluck.rates.TableResolver.Resolution resolution=com.lootluck.rates.TableResolver.resolve(s.identity,rates.rows(s.identity));
			List<RateRow> sourceRates=resolution.rows;
			String wikiOwner=sourceRates.isEmpty()?com.lootluck.rates.RewardSources.wikiOwner(s.identity):sourceRates.get(0).owner;
			source.wikiUrl=sourceWikiUrl(wikiOwner==null?com.lootluck.rates.RewardSources.wikiOwner(s.identity):wikiOwner);
			source.count=s.notifications;source.lastReceived=s.lastReceived;
			source.countTooltip="Recorded loot notifications: "+s.notifications+"; client-reported amount: "+s.reportedAmount+"; excluded notifications: "+s.excluded+". Not automatically verified kills/openings. Context: "+s.identity.context+"; variant: "+s.identity.variant;
			Map<String,List<RateRow>> byName=new LinkedHashMap<>();
			for(RateRow r:sourceRates) { byName.computeIfAbsent(normalize(r.itemName+(com.lootluck.rates.ItemRateMatcher.notedRow(r)?" (noted)":"")),key->new ArrayList<>()).add(r); }
			Map<String,PanelState.Item> display=new LinkedHashMap<>();
			for(Ledger.Item item:s.items.values())
			{
				PanelState.Item row=new PanelState.Item();row.id=item.id;row.key=source.key+":"+item.id;row.name=item.name;row.quantity=item.quantity;row.occurrences=item.occurrences;row.lastReceived=item.lastReceived;
				boolean ambiguousNotes=item.originalIds.stream().anyMatch(original->original!=item.id)&&!com.lootluck.rates.ItemRateMatcher.notedName(item.name);
				List<RateRow> matching=com.lootluck.rates.ItemRateMatcher.item(item.id,item.id,item.name,s.identity,sourceRates);
				if(ambiguousNotes) { matching=sourceRates.stream().filter(rate->normalize(rate.itemName).equals(normalize(item.name))).collect(java.util.stream.Collectors.toList()); }
				applyRate(row,matching);row.rateTooltip+="\n\n"+resolution.reason;applySegment(row,s);display.put("id:"+item.id,row);
				if(ambiguousNotes) { row.rateTooltip+="\n\nLegacy aggregate may mix noted and unnoted loot; quantities cannot be split retrospectively."; }
				if(item.recordedRates!=null&&!item.recordedRates.isEmpty())
				{
					row.rateTooltip="Current Wiki data\n"+row.rateTooltip+"\n\nRecorded history\nRates known at collection; not verified game-effective dates.\n\n"+item.recordedRates.values().stream().map(v->v.occurrences+" outcomes · x"+v.quantity+"\n"+historyDate(v.firstReceived)+" — "+historyDate(v.lastReceived)+"\n"+(v.rows.isEmpty()?"Rate unknown at collection":v.rows.stream().map(RateRow::tooltip).collect(java.util.stream.Collectors.joining("\n\n")))).collect(java.util.stream.Collectors.joining("\n\n"));
				}
				long versioned=item.recordedRates==null?0:item.recordedRates.values().stream().mapToLong(v->v.occurrences).sum();
				if(versioned<item.occurrences) { row.rateTooltip+="\n\nOlder history: "+(item.occurrences-versioned)+" outcomes\nCollection-time rates unavailable."; }
				applyRecordedBreakdown(row,item);
			}
			if(unobtained)
			{
				for(Map.Entry<String,List<RateRow>> group:byName.entrySet())
				{
					RateRow identityRow=group.getValue().get(0);
					if(Boolean.FALSE.equals(com.lootluck.rates.ItemEligibility.eligible(identityRow,s.identity))) { continue; }
					String gameKey=normalize((identityRow.gameName==null?identityRow.itemName:identityRow.gameName)+(com.lootluck.rates.ItemRateMatcher.notedRow(identityRow)?" (noted)":""));
					Integer id=identities.getOrDefault(gameKey,identities.get(group.getKey()));
					if(id==null&&!com.lootluck.rates.ItemRateMatcher.notedRow(identityRow)&&!identityRow.itemIds.isEmpty()) { id=identityRow.itemIds.get(0); }
					final Integer resolvedId=id;
					boolean obtained=display.values().stream().anyMatch(i->(resolvedId!=null&&i.id==resolvedId)||!com.lootluck.rates.ItemRateMatcher.item(i.id,i.id,i.name,s.identity,group.getValue()).isEmpty());
					if(obtained) { continue; }
					PanelState.Item row=new PanelState.Item();row.id=id==null?0:id;RateRow first=group.getValue().get(0);row.name=(first.gameName==null?first.itemName:first.gameName)+(com.lootluck.rates.ItemRateMatcher.notedRow(first)?" (noted)":"");row.key=source.key+":rate:"+group.getKey();
					applyRate(row,group.getValue());
					if(hideOtherWorld&&membersWorld!=null&&(membersWorld?"(f)":"(m)").equals(row.worldMarker)) { continue; }
					applySegment(row,s);display.put("rate:"+group.getKey(),row);
				}
			}
			Map<String,Integer> tables=new LinkedHashMap<>(),positions=new LinkedHashMap<>();
			for(int index=0;index<sourceRates.size();index++) { RateRow rate=sourceRates.get(index);tables.computeIfAbsent(com.lootluck.rates.DropTableGroups.table(rate),key->tables.size());positions.putIfAbsent(com.lootluck.rates.ItemRateMatcher.lookupName(rate.itemName+(com.lootluck.rates.ItemRateMatcher.notedRow(rate)?" (noted)":"")),index); }
			for(PanelState.Item item:display.values()) { item.tableOrder=tables.getOrDefault(item.dropTable,Integer.MAX_VALUE);item.wikiOrder=positions.getOrDefault(com.lootluck.rates.ItemRateMatcher.lookupName(item.name,s.identity),Integer.MAX_VALUE); }
			source.items.addAll(display.values());return source;
	}
	public static String normalize(String name) { return com.lootluck.rates.ItemRateMatcher.normalize(name); }
	private static String historyDate(long time) { return java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(time)); }
	private static void applyRate(PanelState.Item row,List<RateRow> rates)
	{
		row.rarity=new Color(160,160,160);row.rate="Rate unknown";row.rateTooltip="No resolved Wiki drop rate. Unknown is not zero.";
		if(rates==null||rates.isEmpty()) { return; }
		row.dropTable=com.lootluck.rates.DropTableGroups.table(rates.get(0));
		row.publishedRarity=rates.stream().map(RateRow::publishedProbability).filter(java.util.Objects::nonNull).max(Double::compare).orElse(null);
		String marker=rates.get(0).worldMarker();
		if(rates.stream().allMatch(rate->marker.equals(rate.worldMarker()))) { row.worldMarker=marker; }
		for(RateRow rate:rates) { addRateOption(row,rate,false); }
		row.rateTooltip=rates.stream().map(RateRow::tooltip).collect(java.util.stream.Collectors.joining("\n\n"));
		if(rates.size()==1) { row.rate=rates.get(0).display();row.rarity=rates.get(0).color(); }
		else
		{
			boolean same=rates.stream().allMatch(r->r.display().equals(rates.get(0).display()));
			boolean numeric=rates.stream().allMatch(r->r.display().startsWith("1/")||r.display().equals("Always"));
			List<String> alternatives=rates.stream().map(RateRow::display).distinct().collect(java.util.stream.Collectors.toList());
			row.rate=same?rates.get(0).display():numeric&&alternatives.size()<=3?"Variants: "+String.join(", ",alternatives):numeric?"Multiple published rates":"Multiple published rates *";
			if(!same&&rates.stream().anyMatch(rate->"Varies".equals(rate.display()))&&rates.stream().allMatch(rate->rate.publishedProbability()!=null||"Varies".equals(rate.display()))) { row.rate="Varies"; }
			boolean sameColor=rates.stream().allMatch(r->r.color().equals(rates.get(0).color()));
			row.rarity=same||numeric&&sameColor?rates.get(0).color():numeric?new Color(160,160,160):new Color(205,143,183);
			if(!same) { row.rateTooltip="Different source tables; applicable variant unresolved.\n\n"+row.rateTooltip; }
		}
	}
	private static String rateKey(RateRow rate) { return rate.variant+"|"+rate.rawRate+"|"+rate.quantity+"|"+rate.conditions+"|"+rate.mechanics.getOrDefault("Rolls","unknown"); }
	private static PanelState.RateOption addRateOption(PanelState.Item item,RateRow rate,boolean historical)
	{
		String key=rateKey(rate);for(PanelState.RateOption option:item.rateOptions) { if(key.equals(option.key)) { return option; } }
		PanelState.RateOption option=new PanelState.RateOption();option.key=key;String variant=rate.variant==null?"":rate.variant;int anchor=variant.indexOf('#');option.table=(anchor>=0?variant.substring(anchor+1):variant.isBlank()?"Published table":variant)+(com.lootluck.rates.ItemRateMatcher.notedRow(rate)?" (noted)":"");option.dropQuantity=rate.quantity;option.rate=rate.display();option.color=rate.color();option.tooltip=rate.tooltip();option.historical=historical;item.rateOptions.add(option);return option;
	}
	private static void applyRecordedBreakdown(PanelState.Item row,Ledger.Item item)
	{
		long attributedQuantity=0,attributedOccurrences=0;
		if(item.recordedRates!=null) { for(Ledger.RecordedRate history:item.recordedRates.values())
		{
			for(RateRow rate:history.rows) { addRateOption(row,rate,true); }
			if(history.rows.size()==1)
			{
				PanelState.RateOption option=addRateOption(row,history.rows.get(0),true);option.quantity=Math.addExact(option.quantity==null?0:option.quantity,history.quantity);option.occurrences=Math.addExact(option.occurrences==null?0:option.occurrences,history.occurrences);attributedQuantity=Math.addExact(attributedQuantity,history.quantity);attributedOccurrences=Math.addExact(attributedOccurrences,history.occurrences);
			}
		} }
		row.unresolvedQuantity=item.quantity-attributedQuantity;row.unresolvedOccurrences=item.occurrences-attributedOccurrences;
	}
	private static void applySegment(PanelState.Item row,Ledger.Source source)
	{
		row.reason="Attempt boundary and event probability are not certified for this source/context.";
		long n=0,k=0;double expected=0;Double common=null;boolean homogeneous=true;int segments=0;
		for(Ledger.Segment segment:source.segments.values())
		{
			if(!segment.probabilities.containsKey(row.id)||segment.attempts==0) { continue; }
			double p=segment.probabilities.get(row.id);long successes=segment.occurrences.getOrDefault(row.id,0L);
			if((p==0&&successes>0)||(p==1&&successes!=segment.attempts)) { row.reason="Probability model / detection mismatch.";return; }
			n=Math.addExact(n,segment.attempts);k=Math.addExact(k,successes);expected+=Statistics.expected(segment.attempts,p);segments++;
			if(common==null) { common=p; }else if(Double.compare(common,p)!=0) { homogeneous=false; }
		}
		if(n==0) { return; }
		row.attempts=n;row.expected=expected;row.observed=(double)k/n;row.ratio=expected==0?null:k/expected;
		if(homogeneous) { row.probability=common;row.upper=Statistics.upper(n,k,common);row.lower=Statistics.lower(n,k,common); }
		row.reason="Overall luck uses "+segments+" validated segments ("+k+" successful attempts). Expected successes sum each segment's applicable probability; raw totals may include excluded outcomes."+
			(homogeneous?"":" Per-attempt chance varies; a single chance and binomial tails are withheld.");
		Ledger.Segment active=source.activeSegment==null?null:source.segments.get(source.activeSegment);
		if(active!=null&&active.streakValid&&active.probabilities.containsKey(row.id))
		{
			double p=active.probabilities.get(row.id);long d=active.dry.getOrDefault(row.id,0L);
			row.dry=d;row.noDrop=Statistics.dryProbability(d,p);row.dryness=Statistics.dryness(d,p);
			row.reason+=" Dry streak covers only the current validated segment, since its last recorded drop or tracking start; earlier attempts are unknown. Model "+active.modelVersion+"; rate hash "+active.rateHash+".";
		}
	}
}



