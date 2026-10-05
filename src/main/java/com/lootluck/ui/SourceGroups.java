package com.lootluck.ui;

import com.lootluck.model.Ledger;
import com.lootluck.model.SourceIdentity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Display-only grouping. Persisted source identities and certified segments stay separate. */
final class SourceGroups
{
	private SourceGroups() { }
	static Map<String,Ledger.Source> build(Ledger ledger)
	{
		Map<String,List<Ledger.Source>> groups=new LinkedHashMap<>();
		for(Ledger.Source source:ledger.sources.values())
		{
			SourceIdentity id=source.identity;
			String key=new SourceIdentity(id.type,id.name,"display-group",id.context).key();
			groups.computeIfAbsent(key,k->new ArrayList<>()).add(source);
		}
		Map<String,Ledger.Source> display=new LinkedHashMap<>();
		groups.forEach((key,members)->display.put(key,members.size()==1?members.get(0):combine(members)));
		return display;
	}
	private static Ledger.Source combine(List<Ledger.Source> members)
	{
		Ledger.Source combined=new Ledger.Source();SourceIdentity first=members.get(0).identity;
		combined.identity=new SourceIdentity(first.type,first.name,"Combined "+members.size()+" variants: "+members.stream().map(s->s.identity.variant).collect(java.util.stream.Collectors.joining("; ")),first.context);
		for(Ledger.Source source:members)
		{
			combined.notifications=Math.addExact(combined.notifications,source.notifications);
			combined.reportedAmount=Math.addExact(combined.reportedAmount,source.reportedAmount);
			combined.excluded=Math.addExact(combined.excluded,source.excluded);
			combined.lastReceived=Math.max(combined.lastReceived,source.lastReceived);
			for(Ledger.Item original:source.items.values())
			{
				Ledger.Item item=combined.items.computeIfAbsent(original.id,id->{Ledger.Item row=new Ledger.Item();row.id=id;row.name=original.name;return row;});
				item.quantity=Math.addExact(item.quantity,original.quantity);item.occurrences=Math.addExact(item.occurrences,original.occurrences);item.lastReceived=Math.max(item.lastReceived,original.lastReceived);item.originalIds.addAll(original.originalIds);
				if(original.recordedRates!=null) { original.recordedRates.forEach((hash,history)->{
					Ledger.RecordedRate merged=item.recordedRates.computeIfAbsent(hash,h->{Ledger.RecordedRate r=new Ledger.RecordedRate();r.hash=h;r.firstReceived=history.firstReceived;history.rows.forEach(rate->r.rows.add(rate.snapshot()));return r;});
					merged.quantity=Math.addExact(merged.quantity,history.quantity);merged.occurrences=Math.addExact(merged.occurrences,history.occurrences);merged.firstReceived=Math.min(merged.firstReceived,history.firstReceived);merged.lastReceived=Math.max(merged.lastReceived,history.lastReceived);
				}); }
			}
		}
		// A combined view cannot assert one homogeneous probability model or dry streak.
		return combined;
	}
}
