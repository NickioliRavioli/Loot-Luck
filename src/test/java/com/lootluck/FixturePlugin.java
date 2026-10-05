package com.lootluck;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lootluck.model.SourceIdentity;
import com.lootluck.rates.RateProvider;
import com.lootluck.rates.RateRow;
import com.lootluck.ui.LootLuckPanel;
import com.lootluck.ui.PanelState;
import com.lootluck.ui.ViewBuilder;
import java.util.List;
import java.util.Map;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.callback.ClientThread;

/** Development fixture sidebar. No subscriptions, profile access, networking or storage. */
@PluginDescriptor(name="Loot Luck · FIXTURES ONLY",internalName="loot-luck-fixtures",description="Synthetic observations; no account data access")
public class FixturePlugin extends Plugin
{
	@Inject private Gson gson;
	@Inject private ItemManager items;
	@Inject private ClientThread clientThread;
	@Inject private ClientToolbar toolbar;
	private NavigationButton navigation;
	@Override protected void startUp() throws Exception
	{
		Map<String,List<RateRow>> catalogue;
		try(InputStreamReader reader=new InputStreamReader(getClass().getResourceAsStream("/rates/catalogue.json"),StandardCharsets.UTF_8))
		{ catalogue=gson.fromJson(reader,new TypeToken<Map<String,List<RateRow>>>(){}.getType()); }
		RateProvider provider=new RateProvider() { public List<RateRow> rows(SourceIdentity s) { return catalogue.getOrDefault(s.name,List.of()); }public void refresh(SourceIdentity s,boolean force) { } };
		Map<String,Integer> identities=Map.of("cotton seed",Fixtures.COTTON,"rune longsword",net.runelite.api.gameval.ItemID.RUNE_LONGSWORD,"superior dragon bones",net.runelite.api.gameval.ItemID.DRAGON_BONES_SUPERIOR,"magic fang",net.runelite.api.gameval.ItemID.MAGIC_FANG,"zulrah's scales",net.runelite.api.gameval.ItemID.SNAKEBOSS_SCALE);
		com.lootluck.tracking.LedgerEngine engine=Fixtures.reviewEngine();
		PanelState state=ViewBuilder.build(engine.lifetime(),provider,false,identities);
		state.notice="SYNTHETIC FIXTURES · no account storage";state.dataStatus="Fixture probability is not certified gameplay";
		clientThread.invoke(()->{
			SwingUtilities.invokeLater(()->{
				boolean[] session={false},all={false};LootLuckPanel[] holder=new LootLuckPanel[1];
				Runnable update=()->{PanelState view=ViewBuilder.build(session[0]?engine.session():engine.lifetime(),provider,all[0],identities);view.notice=state.notice;view.dataStatus=state.dataStatus;attachIcons(view,()->holder[0].repaint());holder[0].update(view);};
				LootLuckPanel panel=new LootLuckPanel(id->null,value->{session[0]=value;update.run();},value->{all[0]=value;update.run();},()->{engine.resetSession();update.run();},()->{});holder[0]=panel;attachIcons(state,panel::repaint);panel.update(state);
				BufferedImage glyph=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
				java.awt.Graphics2D graphics=glyph.createGraphics();
				graphics.setColor(new java.awt.Color(218,184,95));graphics.setFont(new java.awt.Font("SansSerif",java.awt.Font.BOLD,11));graphics.drawString("LL",1,12);graphics.dispose();
				navigation=NavigationButton.builder().tooltip("Loot Luck fixtures only").icon(glyph).panel(panel).priority(1).build();toolbar.addNavigation(navigation);
			});
		});
	}
	private void attachIcons(PanelState state,Runnable repaint)
	{
		for(PanelState.Source source:state.sources) { for(PanelState.Item item:source.items) { if(item.id<=0) { continue; }net.runelite.client.util.AsyncBufferedImage image=items.getImage(item.id,(int)Math.max(1,Math.min(Integer.MAX_VALUE,item.quantity)),state.iconQuantities&&item.quantity>0);if(image!=null) { item.icon=new ImageIcon(image);image.onLoaded(()->SwingUtilities.invokeLater(repaint)); } } }
	}
	@Override protected void shutDown() { if(navigation!=null) { toolbar.removeNavigation(navigation); } }
}

