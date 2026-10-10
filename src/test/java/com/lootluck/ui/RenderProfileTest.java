package com.lootluck.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.Test;
import static org.junit.Assert.*;

public class RenderProfileTest
{
	@Test public void optionalProbeRecordsPaintStageScaleAndForegroundReuse() throws Exception
	{
		FlightRecorder.register(LootLuckProfileLauncher.PaintEvent.class);
		Path path=Path.of("build","render-probe-test.jfr");Files.createDirectories(path.getParent());
		try(Recording recording=new Recording())
		{
			recording.enable("lootluck.Paint");recording.start();
			SwingUtilities.invokeAndWait(()->{
				assertNull(EffectButton.observer);EffectButton.observer=new LootLuckProfileLauncher.PaintObserver();
				try
				{
					EffectButton button=new EffectButton(ItemEffect.RARITY_AURORA,Color.RED);button.setLayout(null);button.setSize(42,42);JLabel label=new JLabel("X");label.setBounds(2,2,38,38);button.add(label);
					BufferedImage image=new BufferedImage(60,60,BufferedImage.TYPE_INT_ARGB_PRE);
					for(int pass=0;pass<2;pass++) { Graphics2D graphics=image.createGraphics();try { graphics.scale(1.25,1.25);button.paint(graphics); }finally { graphics.dispose(); } }
				}
				finally { EffectButton.observer=null; }
			});
			recording.stop();recording.dump(path);
		}
		List<RecordedEvent> paints=RecordingFile.readAllEvents(path).stream().filter(event->event.getEventType().getName().equals("lootluck.Paint")).collect(Collectors.toList());
		assertEquals(4,paints.size());
		for(RecordedEvent event:paints) { assertEquals("RARITY_AURORA",event.getString("effect"));assertEquals(42,event.getInt("width"));assertEquals(1.25,event.getDouble("scaleX"),0);assertFalse(event.getDuration().isNegative()); }
		List<RecordedEvent> foregrounds=paints.stream().filter(event->event.getString("stage").equals("foreground")).collect(Collectors.toList());
		assertFalse(foregrounds.get(0).getBoolean("foregroundPresent"));assertTrue(foregrounds.get(1).getBoolean("foregroundPresent"));
		assertNull(EffectButton.observer);
	}
}
