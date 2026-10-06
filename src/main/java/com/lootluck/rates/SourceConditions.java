package com.lootluck.rates;

import com.lootluck.model.ObservationContext;
import com.lootluck.model.SourceIdentity;
import java.util.List;
import java.util.stream.Collectors;

/** Display groups reflect verified table/area conditions, never cosmetic NPC IDs. */
public final class SourceConditions
{
	private SourceConditions() { }
	public static boolean hasVariants(List<RateRow> rows)
	{ return rows.stream().map(row->row.variant).distinct().count()>1||rows.stream().anyMatch(row->row.mechanics.containsKey("Required area")); }
	public static String label(SourceIdentity source,List<RateRow> allRows)
	{
		TableResolver.Resolution table=TableResolver.resolve(source,allRows);
		String label="Unknown conditions";
		if(table.resolved)
		{
			List<String> anchors=table.rows.stream().map(row->row.variant).filter(v->v!=null&&v.contains("#")).distinct().collect(Collectors.toList());
			label=anchors.size()==1?anchors.get(0).substring(anchors.get(0).indexOf('#')+1):"Published table";
		}
		if(allRows.stream().anyMatch(row->row.mechanics.containsKey("Required area")))
		{
			Boolean wilderness=ObservationContext.from(source).wilderness;
			label+=" · "+(wilderness==null?"Unknown location":wilderness?"Wilderness":"Outside Wilderness");
		}
		return label;
	}
}
