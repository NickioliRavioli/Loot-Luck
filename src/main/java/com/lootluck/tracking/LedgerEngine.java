package com.lootluck.tracking;

import com.lootluck.model.Ledger;
import com.lootluck.model.Observation;
import java.util.Objects;

/** Single-worker-owned aggregation. Raw observations never acquire retrospective certification. */
public final class LedgerEngine
{
	private final Ledger lifetime;
	private Ledger session = new Ledger();
	private int historyLimit=10000;
	public LedgerEngine(Ledger lifetime) { this.lifetime = Objects.requireNonNull(lifetime); }
	public Ledger lifetime() { return lifetime; }
	public Ledger session() { return session; }
	/** A logout, plugin stop, queue failure or confirmation change ends known coverage. */
	public void interruptCoverage()
	{
		for (Ledger ledger : java.util.List.of(lifetime, session)) { for (Ledger.Source source : ledger.sourceVariants())
		{
			if (source.activeSegment != null) { Ledger.Segment segment = source.segments.get(source.activeSegment); segment.streakValid = false; source.activeSegment = null; }
		} }
	}
	public void resetSession() { session = new Ledger(); }
	public void setHistoryLimit(int limit) { if(limit<0) { throw new IllegalArgumentException("Negative history limit"); }historyLimit=limit;trimHistory(lifetime);trimHistory(session); }
	private void trimHistory(Ledger ledger) { if(historyLimit>0&&ledger.history.size()>historyLimit) { ledger.history.subList(0,ledger.history.size()-historyLimit).clear(); } }
	public void observe(Observation o, AttemptAdapter.Assessment a)
	{
		observe(o,a,java.util.List.of());
	}
	public void observe(Observation o, AttemptAdapter.Assessment a, java.util.List<com.lootluck.rates.RateRow> knownRates)
	{
		apply(lifetime,o,a,knownRates);apply(session,o,a,knownRates);
	}
	private void apply(Ledger ledger, Observation o, AttemptAdapter.Assessment a, java.util.List<com.lootluck.rates.RateRow> knownRates)
	{
		Ledger.CollectionRecord record=new Ledger.CollectionRecord();record.source=o.source;record.timestamp=o.timestamp;record.reportedAmount=o.reportedAmount;record.quantities.putAll(o.quantities);record.names.putAll(o.names);ledger.history.add(record);if(ledger.historyStarted==0) { ledger.historyStarted=o.timestamp; }trimHistory(ledger);
		Ledger.Source family=ledger.sources.computeIfAbsent(o.source.family().key(),key->{Ledger.Source parent=new Ledger.Source();parent.identity=o.source.family();return parent;});
		Ledger.Source s = family.variants.computeIfAbsent(o.source.key(), key -> { Ledger.Source n = new Ledger.Source(); n.identity = o.source; return n; });
		s.notifications = Math.addExact(s.notifications, 1);
		s.reportedAmount = Math.addExact(s.reportedAmount, o.reportedAmount);
		s.lastReceived = Math.max(s.lastReceived, o.timestamp);
		o.quantities.forEach((id, qty) -> {
			Ledger.Item item = s.items.computeIfAbsent(id, key -> { Ledger.Item i = new Ledger.Item(); i.id = id; i.name = o.names.getOrDefault(id, "Item " + id); return i; });
			item.quantity = Math.addExact(item.quantity, qty); item.occurrences = Math.addExact(item.occurrences, 1);
			item.lastReceived = Math.max(item.lastReceived, o.timestamp);
			String observedName=o.names.getOrDefault(id,item.name);
			java.util.List<com.lootluck.rates.RateRow> candidates=com.lootluck.rates.ItemRateMatcher.item(id,o.originalIds.getOrDefault(id,id),observedName,o.source,knownRates);
			java.util.List<com.lootluck.rates.RateRow> matching=com.lootluck.rates.ItemRateMatcher.matchCollection(o.names.getOrDefault(id,item.name),candidates,qty,o.reportedAmount);
			String version=matching.isEmpty()?"unknown":com.lootluck.rates.WikiParser.hash(matching.stream().map(rate->rate.hash+"@"+rate.fetchedAt).sorted().collect(java.util.stream.Collectors.joining("|")));
			Ledger.RecordedRate recorded=item.recordedRates.computeIfAbsent(version,key->{Ledger.RecordedRate v=new Ledger.RecordedRate();v.hash=key;matching.forEach(rate->v.rows.add(rate.snapshot()));v.firstReceived=o.timestamp;return v;});
			recorded.quantity=Math.addExact(recorded.quantity,qty);recorded.occurrences=Math.addExact(recorded.occurrences,1);recorded.lastReceived=o.timestamp;
			item.originalIds.add(id);
			o.originalIds.forEach((original, canonical) -> { if (canonical.equals(id)) { item.originalIds.add(original); } });
		});
		if (!a.certified || o.reportedAmount != 1)
		{
			s.excluded = Math.addExact(s.excluded, 1);
			if (s.activeSegment != null) { s.segments.get(s.activeSegment).streakValid = false; s.activeSegment = null; }
			return;
		}
		Ledger.Segment segment = s.activeSegment == null ? null : s.segments.get(s.activeSegment);
		if (segment == null || !a.modelVersion.equals(segment.modelVersion) || !a.rateHash.equals(segment.rateHash)
			|| !a.probabilities.equals(segment.probabilities))
		{
			if (segment != null) { segment.ended = o.timestamp; }
			segment = new Ledger.Segment(); segment.started = o.timestamp; segment.modelVersion = a.modelVersion;
			segment.rateHash = a.rateHash; segment.probabilities.putAll(a.probabilities);
			s.activeSegment = "segment-" + s.segments.size(); s.segments.put(s.activeSegment, segment);
		}
		segment.attempts = Math.addExact(segment.attempts, 1);
		for (Integer id : segment.probabilities.keySet())
		{
			if (o.quantities.containsKey(id))
			{
				segment.occurrences.merge(id, 1L, Math::addExact); segment.quantities.merge(id, o.quantities.get(id), Math::addExact); segment.dry.put(id, 0L);
			}
			else { segment.dry.merge(id, 1L, Math::addExact); }
		}
	}
}
