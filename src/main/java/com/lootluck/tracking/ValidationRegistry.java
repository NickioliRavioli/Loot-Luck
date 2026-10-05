package com.lootluck.tracking;

import com.lootluck.model.Observation;

/** Intentionally no production certifications until recorded gameplay/mechanics evidence is reviewed. */
public final class ValidationRegistry implements AttemptAdapter
{
	@Override public Assessment assess(Observation observation)
	{
		return Assessment.unsupported(observation.reportedAmount != 1 ? "Batched or unknown attempt boundary" :
			"Attempt boundary and event probability require validation for this source/context");
	}
}
