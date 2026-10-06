package com.lootluck.ui;

import java.awt.Dimension;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import javax.swing.JLabel;

/** Single-line source name with its count immediately following the visible name. */
final class SourceHeaderLabel extends JLabel
{
	private final String count;
	private final Color countColor;
	SourceHeaderLabel(String text,String count,Color countColor)
	{
		super(text);this.count=count;this.countColor=countColor;
	}
	@Override public Dimension getPreferredSize()
	{
		FontMetrics metrics=getFontMetrics(getFont());
		return new Dimension(1,metrics.getHeight());
	}
	@Override protected void paintComponent(Graphics graphics)
	{
		graphics.setFont(getFont());graphics.setColor(getForeground());FontMetrics metrics=graphics.getFontMetrics();
		int countWidth=metrics.stringWidth(count),gap=4;
		String name=ellipsize(getText(),metrics,Math.max(0,getWidth()-countWidth-gap));
		int y=(getHeight()-metrics.getHeight())/2+metrics.getAscent();
		graphics.drawString(name,0,y);
		graphics.setColor(countColor);graphics.drawString(count,metrics.stringWidth(name)+gap,y);
	}
	static String ellipsize(String text,FontMetrics metrics,int width)
	{
		if(metrics.stringWidth(text)<=width) { return text; }
		String suffix="...";if(metrics.stringWidth(suffix)>width) { return ""; }
		int end=text.length();
		while(end>0&&metrics.stringWidth(text.substring(0,end)+suffix)>width) { end--; }
		return text.substring(0,end).stripTrailing()+suffix;
	}
}
