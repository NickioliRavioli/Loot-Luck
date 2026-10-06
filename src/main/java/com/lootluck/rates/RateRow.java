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
	public static final Color VARIES_COLOR=new Color(255,163,255);
	public String owner, variant, itemName, rawRate, quantity, conditions, fetchedAt, hash, url;
	public int itemId;
	/** Rebuilt from the separate public identity index; do not duplicate it in every snapshot. */
	public transient String gameName;
	public transient java.util.List<Integer> itemIds = new java.util.ArrayList<>();
	public boolean approximate;
	public Map<String, String> mechanics = new LinkedHashMap<>();
	public RateRow snapshot()
	{
		RateRow copy=new RateRow();copy.owner=owner;copy.variant=variant;copy.itemName=itemName;copy.rawRate=rawRate;copy.quantity=quantity;copy.conditions=conditions;copy.fetchedAt=fetchedAt;copy.hash=hash;copy.url=url;copy.itemId=itemId;copy.gameName=gameName;copy.itemIds.addAll(itemIds);copy.approximate=approximate;copy.mechanics.putAll(mechanics);return copy;
	}
	public Double publishedProbability()
	{
		try
		{
			if ("Always".equalsIgnoreCase(rawRate) || "Guaranteed".equalsIgnoreCase(rawRate)) { return 1.0; }
			if ("Never".equalsIgnoreCase(rawRate)) { return 0.0; }
			String text = rawRate.replace(",", "").trim();
			String[] parts = text.split("/", -1);
			double p;
			if (parts.length == 2) { p = new BigDecimal(parts[0].trim()).divide(new BigDecimal(parts[1].trim()), MathContext.DECIMAL128).doubleValue(); }
			else if (parts.length == 1 && text.endsWith("%")) { p = new BigDecimal(text.substring(0,text.length()-1).trim()).movePointLeft(2).doubleValue(); }
			else { return null; }
			return Double.isFinite(p) && p >= 0 && p <= 1 ? p : null;
		}
		catch (RuntimeException ex) { return null; }
	}
	public String display()
	{
		if(rawRate!=null&&"Varies".equalsIgnoreCase(rawRate.trim())) { return "Varies"; }
		Double p = publishedProbability();
		if (p == null)
		{
			if (rawRate != null && rawRate.matches("(?i)Common|Uncommon|Rare|Very rare")) { return rawRate + (hasUnresolvedConditions()?" *":""); }
			return hasUnresolvedConditions()?"*":"Rate unknown";
		}
		String marker=hasUnresolvedConditions()?" *":"";
		if (p == 1.0) { return "Always"+marker; }
		if (p == 0.0) { return "Never"+marker; }
		DecimalFormat f = new DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(Locale.US));
		return (approximate ? "~" : "") + "1/" + f.format(1 / p)+marker;
	}
	public Color color()
	{
		Double p = publishedProbability();
		if ((rawRate!=null&&"Varies".equalsIgnoreCase(rawRate.trim()))||(p==null&&hasUnresolvedConditions())) { return VARIES_COLOR; }
		if (p == null) { return new Color(170,170,170); }
		return p == 1 ? new Color(138,174,226) : p >= 1.0/25 ? new Color(130,184,117) :
			p >= 1.0/99.99 ? new Color(213,195,101) : p >= 1.0/999.99 ? new Color(213,148,86) : new Color(216,121,121);
	}
	public String tooltip()
	{
		return "Published rate: " + rawRate + "\nQuantity: " + quantity + " · Rolls: " + mechanics.getOrDefault("Rolls", "unknown") +
			(mechanics.containsKey("Structured export rate")?"\nSource-page correction; structured export reported "+mechanics.get("Structured export rate"):"")+
			(mechanics.containsKey("Wiki page revision")?"\nSource page revision: "+mechanics.get("Wiki page revision"):"")+
			(mechanics.containsKey("Source page conditions")?"\nSource page notes: "+mechanics.get("Source page conditions"):"")+
			(variant==null||variant.isBlank()?"":"\nTable: "+variant)+(conditions==null||conditions.isBlank()?"":"\nConditions: "+readableConditions())+
			"\nFetched: " + fetchedAt + "\nSource: " + url;
	}
	private static String stripWorldMarker(String text,String title,String marker)
	{
		return text.replaceAll("(?i)<sub\\b[^>]*title\\s*=\\s*[\"']"+title+"[\"'][^>]*>\\s*\\("+marker+"\\)\\s*</sub>","").trim();
	}
	private String withoutWorldMarkers()
	{
		return stripWorldMarker(stripWorldMarker(conditions==null?"":conditions,"Members-only","m"),"Free-to-play-only","f");
	}
	public String worldMarker()
	{
		String original=conditions==null?"":conditions.trim();
		boolean members=!stripWorldMarker(original,"Members-only","m").equals(original);
		boolean free=!stripWorldMarker(original,"Free-to-play-only","f").equals(original);
		return members==free?"":members?"(m)":"(f)";
	}
	private boolean hasUnresolvedConditions() { String other=withoutWorldMarkers();return !other.isBlank()&&!"Never".equalsIgnoreCase(other); }
	public boolean modelConditionsUnresolved() { return hasUnresolvedConditions() || !mechanics.getOrDefault("Source page conditions", "").isBlank(); }
	private String readableConditions()
	{
		String original=conditions==null?"":conditions.trim();String other=withoutWorldMarkers();
		String prefix="";
		if(!stripWorldMarker(original,"Members-only","m").equals(original)) { prefix="Members world only"; }
		if(!stripWorldMarker(original,"Free-to-play-only","f").equals(original)) { prefix+=(prefix.isEmpty()?"":"; ")+"Free-to-play world only"; }
		return prefix+(prefix.isEmpty()||other.isEmpty()?"":"; ")+other.replaceAll("<[^>]*>","");
	}
}
