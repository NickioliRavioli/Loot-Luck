package com.lootluck.ui;

import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.FontManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceHeaderLabelTest
{
	@Test public void namesStayOnOneLineAndEllipsizeOverflow()
	{
		java.awt.Graphics2D graphics=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			FontMetrics metrics=graphics.getFontMetrics(FontManager.getRunescapeSmallFont());
			assertEquals("Ghost",SourceHeaderLabel.ellipsize("Ghost",metrics,100));
			int width=metrics.stringWidth("Example: large");
			for(String text:java.util.List.of("Example: an extremely long source name with several more words","ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ"))
			{
				String line=SourceHeaderLabel.ellipsize(text,metrics,width);assertTrue(line.endsWith("..."));
				assertTrue(metrics.stringWidth(line)<=width);
			}
			assertEquals("",SourceHeaderLabel.ellipsize("Ghost",metrics,0));
		}
		finally { graphics.dispose(); }
	}
}
