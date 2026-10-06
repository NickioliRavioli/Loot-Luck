package com.lootluck.rates;

import com.lootluck.model.ObservationContext;
import com.lootluck.model.SourceIdentity;

/** Unknown eligibility is distinct from an eligible failure. */
public final class ItemEligibility
{
	private ItemEligibility() { }
	public static Boolean eligible(RateRow row,SourceIdentity source)
	{
		if("Wilderness".equals(row.mechanics.get("Required area"))) { return ObservationContext.from(source).wilderness; }
		return row.modelConditionsUnresolved()?null:Boolean.TRUE;
	}
}
