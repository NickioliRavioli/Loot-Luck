package com.lootluck.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.Set;

/** JSON observation aggregates and optionally bounded collection history; no account names. */
public final class Ledger
{
	public int schema = 3;
	public Map<String, Source> sources = new LinkedHashMap<>();
	public java.util.List<CollectionRecord> history=new java.util.ArrayList<>();
	public long historyStarted;
	/** Schema 3 stores one source family, retaining each original context beneath it. */
	public java.util.List<Source> sourceVariants()
	{
		java.util.List<Source> variants=new java.util.ArrayList<>();
		for(Source source:sources.values()) { variants.addAll(source.variants.values()); }
		return variants;
	}
	public Source source(SourceIdentity identity)
	{
		Source family=sources.get(identity.family().key());return family==null?null:family.variants.get(identity.key());
	}
	public static final class CollectionRecord
	{
		public boolean pickpocketFailure;
		public SourceIdentity source;
		public long timestamp,reportedAmount;
		public Map<Integer,Long> quantities=new LinkedHashMap<>();
		public Map<Integer,String> names=new LinkedHashMap<>();
	}
	public static final class Source
	{
		public long pickpocketFailures,lastPickpocketFailure;
		public SourceIdentity identity;
		public long notifications, reportedAmount, excluded, lastReceived;
		public Map<Integer, Item> items = new LinkedHashMap<>();
		public Map<String, Segment> segments = new LinkedHashMap<>();
		public String activeSegment;
		/** Only populated on a source-family container; leaves hold observations. */
		public Map<String,Source> variants=new LinkedHashMap<>();
	}
	public static final class Item
	{
		public int id;
		public String name;
		public long quantity, occurrences, lastReceived;
		public Set<Integer> originalIds = new TreeSet<>();
		/** Immutable published-rate versions; never certified retrospectively. */
		public Map<String, RecordedRate> recordedRates = new LinkedHashMap<>();
	}
	public static final class RecordedRate
	{
		public String hash;
		public java.util.List<com.lootluck.rates.RateRow> rows = new java.util.ArrayList<>();
		public long quantity, occurrences, firstReceived, lastReceived;
	}
	public static final class Segment
	{
		public String modelVersion, rateHash;
		public long attempts, started, ended;
		public boolean streakValid = true;
		public Map<Integer, Double> probabilities = new LinkedHashMap<>();
		public Map<Integer, Long> occurrences = new LinkedHashMap<>(), quantities = new LinkedHashMap<>(), dry = new LinkedHashMap<>();
	}
}
