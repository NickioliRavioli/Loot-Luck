package com.lootluck.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;

/** Ordinary Swing HTML content, with no text editor, caret or input focus. */
final class ItemInformationPopup extends JPopupMenu
{
	ItemInformationPopup(PanelState.Item item)
	{
		setLayout(new BorderLayout());
		JLabel content=new JLabel(html(item));content.setVerticalAlignment(JLabel.TOP);
		content.setOpaque(true);content.setBackground(new Color(30,30,30));content.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
		JScrollPane scroll=new JScrollPane(content);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);scroll.setPreferredSize(new Dimension(300,Math.min(300,content.getPreferredSize().height+2)));
		add(scroll);
	}
	static String html(PanelState.Item item)
	{
		StringBuilder html=new StringBuilder("<html><body style='font-family:sans-serif;font-size:10px;color:#c0c0c0'><table width='260' cellspacing='0' cellpadding='0'><tr><td>");
		html.append("<b><font color='#dfc071'>").append(escape(item.name)).append("</font></b><br><br>");
		String information=(item.rateTooltip==null?"":item.rateTooltip)+(PanelState.LUCK_STATISTICS_VISIBLE?"\n\nStatistics\n"+item.reason:"");
		for(String line:information.split("\n",-1))
		{
			if(java.util.Set.of("Current Wiki data","Recorded history","Statistics").contains(line))
			{
				html.append("<b><font color='#dfc071'>").append(escape(line)).append("</font></b>");
			}
			else
			{
				Color color=null;
				for(PanelState.RateOption option:item.rateOptions)
				{
					if(line.startsWith("Published rate: ")&&option.tooltip!=null&&option.tooltip.split("\n",2)[0].equals(line)) { color=option.color;break; }
				}
				int separator=line.indexOf(": ");
				if(separator>=0) { html.append("<b>").append(escape(line.substring(0,separator+1))).append("</b> "); }
				if(color!=null) { html.append("<font color='").append(String.format(java.util.Locale.ROOT,"#%06x",color.getRGB()&0xffffff)).append("'>"); }
				html.append(escape(separator>=0?line.substring(separator+2):line));
				if(color!=null) { html.append("</font>"); }
			}
			html.append("<br>");
		}
		return html.append("</td></tr></table></body></html>").toString();
	}
	private static String escape(String text)
	{
		return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
	}
}
