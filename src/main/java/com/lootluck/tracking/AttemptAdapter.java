package com.lootluck.tracking;

import com.lootluck.model.Observation;
import java.util.Map;

/** Certification requires an explicit source contract, not merely amount == 1. */
public interface AttemptAdapter
{
	Assessment assess(Observation observation);
	final class Assessment
	{
		public final boolean certified;
		public final String reason, modelVersion, rateHash;
		public final Map<Integer, Double> probabilities;
		private Assessment(boolean certified, String reason, String model, String hash, Map<Integer, Double> probabilities)
		{ this.certified = certified; this.reason = reason; modelVersion = model; rateHash = hash; this.probabilities = Map.copyOf(probabilities); }
		public static Assessment unsupported(String reason) { return new Assessment(false, reason, "", "", Map.of()); }
		public static Assessment certified(String model, String hash, Map<Integer, Double> probabilities)
		{
			if (model.isEmpty() || hash.isEmpty() || probabilities.isEmpty()) { throw new IllegalArgumentException("Missing certification provenance"); }
			probabilities.values().forEach(p -> { if (!Double.isFinite(p) || p < 0 || p > 1) { throw new IllegalArgumentException("Invalid probability"); } });
			return new Assessment(true, "", model, hash, probabilities);
		}
	}
}
