package com.lootluck.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Synchronously copied event data, independent of mutable RuneLite collections. */
public final class Observation
{
	public final SourceIdentity source;
	public final long timestamp, reportedAmount;
	public final Map<Integer, Long> quantities;
	public final Map<Integer, String> names;
	/** original game ID -> explicit canonical (noted/unnoted) ID. */
	public final Map<Integer, Integer> originalIds;
	public Observation(SourceIdentity source, long timestamp, long amount, Map<Integer, Long> quantities,
		Map<Integer, String> names, Map<Integer, Integer> originalIds)
	{
		if (timestamp < 0 || amount < 0) { throw new IllegalArgumentException("Negative observation fields"); }
		this.source = source; this.timestamp = timestamp; this.reportedAmount = amount;
		Map<Integer, Long> clean = new LinkedHashMap<>();
		quantities.forEach((id, qty) -> { if (id > 0 && qty > 0) { clean.merge(id, qty, Math::addExact); } });
		this.quantities = Collections.unmodifiableMap(clean);
		this.names = Collections.unmodifiableMap(new LinkedHashMap<>(names));
		this.originalIds = Collections.unmodifiableMap(new LinkedHashMap<>(originalIds));
	}
}
