package com.lootluck.ui;

import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.FontManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceHeaderLabelTest
{
	@Test public void namesWrapAtWordsAndEllipsizeOnlyBeyondTwoLines()
	{
		java.awt.Graphics2D graphics=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			FontMetrics metrics=graphics.getFontMetrics(FontManager.getRunescapeSmallFont());
			assertEquals(java.util.List.of("Ghost"),SourceHeaderLabel.lines("Ghost",metrics,100));
			int width=metrics.stringWidth("Example: large");
			assertEquals(java.util.List.of("Example: large","quantities"),SourceHeaderLabel.lines("Example: large quantities",metrics,width));
			for(String text:java.util.List.of("Example: an extremely long source name with several more words","ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMNOPQRSTUVWXYZ"))
			{
				java.util.List<String> lines=SourceHeaderLabel.lines(text,metrics,width);assertEquals(2,lines.size());assertTrue(lines.get(1).endsWith("..."));
				for(String line:lines) { assertTrue(metrics.stringWidth(line)<=width); }
			}
		}
		finally { graphics.dispose(); }
	}
}
