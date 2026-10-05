package com.lootluck.stats;

import org.apache.commons.math3.special.Beta;

/** Exact binomial tails using directly evaluated beta functions, avoiding 1-CDF cancellation. */
public final class Statistics
{
	private Statistics() { }
	public static void validate(long n, long k, double p)
	{ if (n < 0 || k < 0 || k > n || !Double.isFinite(p) || p < 0 || p > 1) { throw new IllegalArgumentException("Invalid binomial model"); } }
	public static double expected(long n, double p) { validate(n, 0, p); return n * p; }
	public static double ratio(long n, long k, double p) { validate(n, k, p); return n == 0 || p == 0 ? Double.NaN : k / (n * p); }
	public static double upper(long n, long k, double p)
	{
		validate(n, k, p); if (k == 0) { return 1; } if (p == 0) { return 0; } if (p == 1) { return 1; }
		return Beta.regularizedBeta(p, k, n - k + 1.0);
	}
	public static double lower(long n, long k, double p)
	{
		validate(n, k, p); if (k == n) { return 1; } if (p == 0) { return 1; } if (p == 1) { return 0; }
		return Beta.regularizedBeta(1 - p, n - k, k + 1.0);
	}
	public static double dryProbability(long d, double p) { validate(d, 0, p); return d == 0 ? 1 : p == 1 ? 0 : Math.exp(d * Math.log1p(-p)); }
	public static double dryness(long d, double p) { validate(d, 0, p); return d == 0 ? 0 : p == 1 ? Double.POSITIVE_INFINITY : -d * Math.log1p(-p); }
	public static double multipleRollPresence(double q, int rolls)
	{ validate(0, 0, q); if (rolls < 1) { throw new IllegalArgumentException("Invalid rolls"); } return q == 1 ? 1 : -Math.expm1(rolls * Math.log1p(-q)); }
}
