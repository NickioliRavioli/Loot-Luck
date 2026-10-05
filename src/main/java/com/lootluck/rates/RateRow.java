package com.lootluck.rates;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.awt.Color;

public final class RateRow
{
	public String owner, variant, itemName, rawRate, quantity, conditions, fetchedAt, hash, url;
	public int itemId;
	public boolean approximate;
	public Map<String, String> mechanics = new LinkedHashMap<>();
	public RateRow snapshot()
	{
		RateRow copy=new RateRow();copy.owner=owner;copy.variant=variant;copy.itemName=itemName;copy.rawRate=rawRate;copy.quantity=quantity;copy.conditions=conditions;copy.fetchedAt=fetchedAt;copy.hash=hash;copy.url=url;copy.itemId=itemId;copy.approximate=approximate;copy.mechanics.putAll(mechanics);return copy;
	}
	public Double publishedProbability()
	{
		try
		{
			if ("Always".equalsIgnoreCase(rawRate) || "Guaranteed".equalsIgnoreCase(rawRate)) { return 1.0; }
			String[] parts = rawRate.replace(",", "").trim().split("/");
			if (parts.length != 2) { return null; }
			double p = new BigDecimal(parts[0].trim()).divide(new BigDecimal(parts[1].trim()), MathContext.DECIMAL128).doubleValue();
			return Double.isFinite(p) && p > 0 && p <= 1 ? p : null;
		}
		catch (RuntimeException ex) { return null; }
	}
	public String display()
	{
		if (hasUnresolvedConditions()) { return "Conditional"; }
		Double p = publishedProbability();
		if (p == null) { return conditions != null && !conditions.isBlank()?"Conditional":"Rate unknown"; }
		if (p == 1.0) { return "Always"; }
		DecimalFormat f = new DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(Locale.US));
		return (approximate ? "~" : "") + "1/" + f.format(1 / p);
	}
	public Color color()
	{
		Double p = publishedProbability();
		if (hasUnresolvedConditions()) { return new Color(205,143,183); }
		if (p == null) { return new Color(170,170,170); }
		return p == 1 ? new Color(138,174,226) : p >= 1.0/25 ? new Color(130,184,117) :
			p >= 1.0/99.99 ? new Color(213,195,101) : p >= 1.0/999.99 ? new Color(213,148,86) : new Color(216,121,121);
	}
	public String tooltip()
	{
		return "Published rate: " + rawRate + "\nQuantity: " + quantity + " · Rolls: " + mechanics.getOrDefault("Rolls", "unknown") +
			(variant==null||variant.isBlank()?"":"\nTable: "+variant)+(conditions==null||conditions.isBlank()?"":"\nConditions: "+readableConditions())+
			"\nFetched: " + fetchedAt + "\nSource: " + url;
	}
	private String withoutMembershipMarker()
	{
		return conditions==null?"":conditions.replaceAll("(?i)<sub\\b[^>]*title\\s*=\\s*[\"']Members-only[\"'][^>]*>\\s*\\(m\\)\\s*</sub>","").trim();
	}
	private boolean hasUnresolvedConditions() { String other=withoutMembershipMarker();return !other.isBlank()&&!"Never".equalsIgnoreCase(other); }
	private String readableConditions()
	{
		String other=withoutMembershipMarker();String prefix=other.equals(conditions.trim())?"":"Members world only";
		return prefix+(prefix.isEmpty()||other.isEmpty()?"":"; ")+other.replaceAll("<[^>]*>","");
	}
}
