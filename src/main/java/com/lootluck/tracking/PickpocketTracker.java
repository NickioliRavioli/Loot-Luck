package com.lootluck.tracking;

import com.lootluck.rates.ItemRateMatcher;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Game-message evidence; a reward is never classified from nearby NPC deaths or stack size. */
public final class PickpocketTracker
{
	private static final Pattern OUTCOME=Pattern.compile("^You (attempt to pick|fail to pick|pick) (?:the )?(.+?)[\u0027\u2019]s? pocket[.!].*$");
	public static final String ROGUE_MESSAGE="Your rogue clothing allows you to steal twice as much loot!";
	public static final class Success
	{
		public final String target;
		public final int tick;
		public final Integer pieces;
		private boolean doubled,claimed,ambiguous;
		private Success(String target,int tick,Integer pieces) { this.target=target;this.tick=tick;this.pieces=pieces; }
		public String metadata() { return metadata(true); }
		public String metadata(boolean complete) { return ";rogueDoubled="+(ambiguous||!complete&&!doubled?"unknown":doubled)+(pieces==null||ambiguous?"":";roguePieces="+pieces); }
	}
	private final Deque<Success> successes=new ArrayDeque<>();
	private Success latest;
	private String selected="";
	public void selected(String name) { selected=name; }
	/** Returns a failed target, or null. Attempt, stun and bonus messages are not failures. */
	public String message(String message,int tick,Integer pieces)
	{
		expire(tick);
		if(ROGUE_MESSAGE.equals(message))
		{
			if(latest!=null&&latest.tick==tick) { latest.doubled=true; }
			return null;
		}
		Matcher outcome=OUTCOME.matcher(message);if(!outcome.matches()) { return null; }
		String target=capitalize(outcome.group(2));
		// Core Loot Tracker uses the selected H.A.M. Member when chat calls it Man/Woman.
		if("h.a.m. member".equals(ItemRateMatcher.normalize(selected))&&
			("man".equals(ItemRateMatcher.normalize(target))||"woman".equals(ItemRateMatcher.normalize(target)))) { target=selected; }
		if("pick".equals(outcome.group(1)))
		{
			latest=new Success(target,tick,pieces);successes.addLast(latest);
			for(Success previous:successes) { if(previous!=latest&&previous.tick==tick) { previous.ambiguous=true;latest.ambiguous=true; } }
			while(successes.size()>32) { successes.removeFirst(); }
		}
		else { latest=null; }
		return "fail to pick".equals(outcome.group(1))?target:null;
	}
	public Success claim(String target,int tick)
	{
		expire(tick);
		for(Success success:successes)
		{
			if(!success.claimed&&ItemRateMatcher.normalize(success.target).equals(ItemRateMatcher.normalize(target))) { success.claimed=true;return success; }
		}
		return null;
	}
	private void expire(int tick)
	{
		while(!successes.isEmpty()&&tick-successes.peekFirst().tick>2) { Success old=successes.removeFirst();if(latest==old) { latest=null; } }
	}
	private static String capitalize(String text)
	{
		StringBuilder result=new StringBuilder();boolean start=true;
		for(char ch:text.toCharArray()) { result.append(start?Character.toTitleCase(ch):ch);start=Character.isWhitespace(ch); }
		return result.toString();
	}
	public void clear() { successes.clear();latest=null;selected=""; }
}
