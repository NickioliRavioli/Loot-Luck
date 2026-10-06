package com.lootluck.ui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import javax.swing.JLabel;

/** Source names use at most two measured lines; summary fields own their space. */
final class SourceHeaderLabel extends JLabel
{
	private final IntSupplier availableWidth;
	SourceHeaderLabel(String text,IntSupplier availableWidth)
	{
		super(text);this.availableWidth=availableWidth;
	}
	@Override public Dimension getPreferredSize()
	{
		FontMetrics metrics=getFontMetrics(getFont());
		return new Dimension(1,lines(getText(),metrics,availableWidth.getAsInt()).size()*metrics.getHeight());
	}
	@Override protected void paintComponent(Graphics graphics)
	{
		graphics.setFont(getFont());graphics.setColor(getForeground());FontMetrics metrics=graphics.getFontMetrics();
		List<String> lines=lines(getText(),metrics,getWidth());int y=metrics.getAscent();
		for(String line:lines) { graphics.drawString(line,0,y);y+=metrics.getHeight(); }
	}
	static List<String> lines(String text,FontMetrics metrics,int width)
	{
		List<String> result=new ArrayList<>();String remaining=text;
		for(int line=0;line<2;line++)
		{
			if(metrics.stringWidth(remaining)<=width) { result.add(remaining);break; }
			String suffix=line==1?"...":"";int end=remaining.length();
			while(end>0&&metrics.stringWidth(remaining.substring(0,end)+suffix)>width) { end--; }
			if(line==1) { result.add(end==0&&metrics.stringWidth(suffix)>width?"":remaining.substring(0,end).stripTrailing()+suffix);break; }
			int space=remaining.lastIndexOf(' ',end);if(space>0) { end=space; }
			if(end==0) { result.add("");break; }
			result.add(remaining.substring(0,end).stripTrailing());remaining=remaining.substring(end).stripLeading();
		}
		return result;
	}
}
