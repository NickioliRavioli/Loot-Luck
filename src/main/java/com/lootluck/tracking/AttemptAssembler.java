package com.lootluck.tracking;

import com.lootluck.model.Observation;
import java.util.LinkedHashMap;
import java.util.Map;

/** Source adapters can union split rewards using an actual completion identity before certification.
 * No production adapter uses this until its completion contract is validated. */
public final class AttemptAssembler
{
	private final Map<String, Observation> pending = new LinkedHashMap<>();
	public void part(String completionId, Observation part)
	{
		if (pending.size() >= 64 && !pending.containsKey(completionId)) { throw new IllegalStateException("Pending completion limit"); }
		Observation previous = pending.get(completionId);
		if (previous == null) { pending.put(completionId, part); return; }
		if (!previous.source.key().equals(part.source.key())) { throw new IllegalArgumentException("Cross-source completion"); }
		Map<Integer, Long> qty = new LinkedHashMap<>(previous.quantities); part.quantities.forEach((id, n) -> qty.merge(id, n, Math::addExact));
		Map<Integer, String> names = new LinkedHashMap<>(previous.names); names.putAll(part.names);
		Map<Integer, Integer> originals = new LinkedHashMap<>(previous.originalIds); originals.putAll(part.originalIds);
		pending.put(completionId, new Observation(part.source, Math.max(previous.timestamp, part.timestamp), 1, qty, names, originals));
	}
	public Observation complete(String completionId) { return pending.remove(completionId); }
	public void clear() { pending.clear(); }
}
