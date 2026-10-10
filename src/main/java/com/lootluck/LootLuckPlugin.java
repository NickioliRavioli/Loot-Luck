package com.lootluck;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.lootluck.model.Ledger;
import com.lootluck.model.Observation;
import com.lootluck.model.SourceIdentity;
import com.lootluck.rates.WikiRateProvider;
import com.lootluck.storage.LedgerStore;
import com.lootluck.tracking.LedgerEngine;
import com.lootluck.tracking.ValidationRegistry;
import com.lootluck.ui.LootLuckPanel;
import com.lootluck.ui.PanelState;
import com.lootluck.ui.ViewBuilder;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginDescriptor(name="Loot Luck",internalName="loot-luck",description="Local loot observations and source-aware drop-rate analysis",tags={"loot","rate","luck","salvage"})
public class LootLuckPlugin extends Plugin
{
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ItemManager itemManager;
	@Inject private ConfigManager configManager;
	@Inject private LootLuckConfig config;
	@Inject private Gson gson;
	@Inject private OkHttpClient http;
	@Inject private ClientToolbar toolbar;
	@Inject private PluginManager pluginManager;
	@Inject private java.util.concurrent.ScheduledExecutorService executor;
	private LootLuckPanel panel;
	private NavigationButton navigation;
	private static final class RuntimeData
	{
		WikiRateProvider rates;
		LedgerStore store;
		LedgerEngine engine;
		ValidationRegistry validation;
		String profile,error="";
	}
	private volatile RuntimeData runtime;
	private java.util.concurrent.CompletableFuture<Void> ioTail=java.util.concurrent.CompletableFuture.completedFuture(null);
	private final java.util.concurrent.atomic.AtomicInteger pendingTasks=new java.util.concurrent.atomic.AtomicInteger();
	private volatile String activeProfile;
	private volatile Boolean membersWorld;
	private volatile boolean running,session,all,lootTrackerEnabled;
	private final AtomicLong generation=new AtomicLong();
	private final Map<String,Integer> identities=new ConcurrentHashMap<>();
	private int scanCursor;
	private boolean scanning;
	private final com.lootluck.tracking.MysteryBoxTracker mysteryBoxes=new com.lootluck.tracking.MysteryBoxTracker();
	private final com.lootluck.tracking.MazeRewardTracker mazeRewards=new com.lootluck.tracking.MazeRewardTracker();
	private net.runelite.api.coords.WorldPoint mazeTarget;
	private final com.lootluck.tracking.PickpocketTracker pickpockets=new com.lootluck.tracking.PickpocketTracker();
	private final java.util.List<PendingPickpocket> pendingPickpockets=new java.util.ArrayList<>();
	private static final class PendingPickpocket
	{
		SourceIdentity source;
		java.util.List<ItemStack> items;
		long amount;
		com.lootluck.tracking.PickpocketTracker.Success success;
	}
	private final com.lootluck.tracking.NpcDeathContexts npcDeaths=new com.lootluck.tracking.NpcDeathContexts();
	private java.util.Collection<ItemStack> pendingNpcItems;
	private String pendingNpcContext="";

	@Override protected void startUp()
	{
		running=true;generation.incrementAndGet();RuntimeData run=new RuntimeData();runtime=run;
		// The injected executor belongs to RuneLite. A serial future chain preserves old-profile writes across restarts.
		SwingUtilities.invokeLater(()->{
			if(!running||run!=runtime) { return; }
			panel=new LootLuckPanel((id,quantity)->observationIcon(id,quantity),value->{session=value;all=panel.driest();publish();},value->{configManager.setConfiguration(LootLuckConfig.GROUP,"includeUnobtained",value);publish();},this::resetSession,this::refresh);
			panel.setDataAction(this::manageData);
			panel.setValidationAction(this::confirmGameplay);
			panel.setSettings(config,(key,value)->configManager.setConfiguration(LootLuckConfig.GROUP,key,value),this::previewIcon);
			BufferedImage image=ImageUtil.loadImageResource(LootLuckPlugin.class,"icon.png");
			navigation=NavigationButton.builder().tooltip("Loot Luck").icon(image).priority(6).panel(panel).build();toolbar.addNavigation(navigation);publish();
		});
		submit(r->{
			try
			{
				// Fresh ledger namespace: pre-audit data is not migrated into validated tracking.
				Filepath directory=getPluginDirectory();r.store=new LedgerStore(directory.joinSegment("profiles-wiki-v1"),gson);
				r.validation=new ValidationRegistry(gson);
				r.rates=new WikiRateProvider(gson,http,directory.joinSegment("wiki-cache"),config::wikiRefresh,()->{if(r==runtime&&running) { publish(); }});r.rates.initialize();
				if(!running||r!=runtime) { r.rates.close(); }
			}
			catch(Exception ex) { r.error="Storage/rate initialization failed; tracking paused.";log.warn(r.error,ex); }
		});
		clientThread.invoke(()->{if(running&&run==runtime) { updateWorldType();checkPrerequisite();activate(configManager.getRSProfileKey());startIdentityScan(); }});
	}
	private void activate(String key)
	{
		mysteryBoxes.clear();
		mazeRewards.clear();mazeTarget=null;
		pickpockets.clear();pendingPickpockets.clear();
		npcDeaths.clear();pendingNpcItems=null;pendingNpcContext="";
		activeProfile=key;long token=generation.incrementAndGet();RuntimeData run=runtime;if(run!=null&&run.rates!=null) { run.rates.cancelRequests(); }
		submit(r->{
			r.profile=key;r.engine=null;
			try { if(key!=null&&r.store!=null) { r.engine=new LedgerEngine(r.store.load(key));r.engine.interruptCoverage();r.engine.setHistoryLimit(config.historyLimit());r.error=""; } }
			catch(Exception ex) { r.error="Profile data unavailable. Use Data settings to delete old test data or import a current export.";log.warn(r.error,ex); }
			publishOnWorker(r,token);
		});
	}
	@Subscribe public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) { activate(event.getNewProfile()); }
	@Subscribe public void onGameStateChanged(GameStateChanged event)
	{
		updateWorldType();
		if(event.getGameState()!=GameState.LOGGED_IN) { flushPickpockets(false);pickpockets.clear(); }
		if(event.getGameState()==GameState.LOADING) { mazeRewards.loading(client.getTickCount()); }
		else if(event.getGameState()!=GameState.LOGGED_IN) { mazeRewards.clear();mazeTarget=null; }
		if(event.getGameState()!=GameState.LOGGED_IN) { mysteryBoxes.clear();npcDeaths.clear();pendingNpcItems=null;pendingNpcContext=""; }
		if(event.getGameState()==GameState.LOGIN_SCREEN||event.getGameState()==GameState.HOPPING||event.getGameState()==GameState.CONNECTION_LOST)
		{ submit(r->{if(r.engine!=null) { r.engine.interruptCoverage(); }}); }
		if(event.getGameState()==GameState.LOGGED_IN||event.getGameState()==GameState.LOGIN_SCREEN) { checkPrerequisite();startIdentityScan();publish(); }
	}
	private void updateWorldType()
	{
		if(client.getGameState()==GameState.LOGGED_IN) { membersWorld=client.getWorldType().contains(net.runelite.api.WorldType.MEMBERS); }
		else if(client.getGameState()==GameState.LOGIN_SCREEN) { membersWorld=null; }
	}
	@Subscribe public void onPluginChanged(PluginChanged event) { checkPrerequisite();if(!lootTrackerEnabled) { submit(r->{if(r.engine!=null) { r.engine.interruptCoverage(); }}); }publish(); }
	private void checkPrerequisite() { lootTrackerEnabled=pluginManager.getPlugins().stream().anyMatch(p->p instanceof LootTrackerPlugin&&pluginManager.isPluginEnabled(p)); }
	@Subscribe public void onConfigChanged(ConfigChanged event)
	{
		if(LootLuckConfig.GROUP.equals(event.getGroup())&&"historyLimit".equals(event.getKey())) { submit(r->{if(r.engine!=null&&r.store!=null&&r.profile!=null) { r.engine.setHistoryLimit(config.historyLimit());try { r.store.save(r.profile,r.engine.lifetime()); }catch(Exception ex) { r.error="History setting save failed.";log.warn(r.error,ex); } }publishOnWorker(r,generation.get());}); }
		if(LootLuckConfig.GROUP.equals(event.getGroup())) { RuntimeData run=runtime;if(run!=null&&run.rates!=null) { if(!config.wikiRefresh()) { run.rates.cancelRequests(); }else { run.rates.refreshCatalogue(false); } }publish(); }
	}
	@Subscribe public void onLootReceived(LootReceived event)
	{
		if(!running||activeProfile==null||event.getType()==null||"PLAYER".equals(event.getType().name())||event.getName()==null||event.getName().isBlank()) { return; }
		String metadata=event.getMetadata() instanceof Number?String.valueOf(event.getMetadata()):"unresolved";
		String deathContext="NPC".equals(event.getType().name())&&event.getItems()==pendingNpcItems?pendingNpcContext:"";
		pendingNpcItems=null;pendingNpcContext="";
		java.util.regex.Matcher location=java.util.regex.Pattern.compile(";deathLocation=([^;]+)").matcher(deathContext);
		String deathLocation=location.find()?location.group(1):"";
		SourceIdentity source=new SourceIdentity(event.getType().name(),event.getName(),"combat="+event.getCombatLevel()+";metadata="+metadata+deathContext.replaceAll(";deathLocation=[^;]+",""),client.getWorldType().toString(),"",deathLocation);
		RuntimeData current=runtime;if(current!=null&&current.rates!=null) { source=current.rates.resolveSource(source); }
		if("Mystery box".equals(event.getName())) { mysteryBoxes.clear(); }
		if("PICKPOCKET".equals(source.type))
		{
			PendingPickpocket pending=new PendingPickpocket();pending.source=source;pending.amount=Math.max(0,event.getAmount());pending.items=new java.util.ArrayList<>();
			if(event.getItems()!=null) { for(ItemStack item:event.getItems()) { pending.items.add(new ItemStack(item.getId(),item.getQuantity())); } }
			pending.success=pickpockets.claim(event.getName(),client.getTickCount());pendingPickpockets.add(pending);return;
		}
		recordLoot(source,event.getItems(),Math.max(0,event.getAmount()));
	}
	private void flushPickpockets(boolean complete)
	{
		for(PendingPickpocket pending:pendingPickpockets)
		{
			SourceIdentity source=pending.source;
			String modifier=pending.success==null?";rogueDoubled=unknown":pending.success.metadata(complete);
			recordLoot(new SourceIdentity(source.type,source.name,source.variant+modifier,source.context,source.wikiOwner),pending.items,pending.amount);
		}
		pendingPickpockets.clear();
	}
	private Integer roguePieces()
	{
		net.runelite.api.ItemContainer equipment=client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN);
		return equipment==null?null:com.lootluck.rates.RogueOutfit.countPieces(java.util.Arrays.stream(equipment.getItems()).mapToInt(net.runelite.api.Item::getId).toArray());
	}
	private void recordPickpocketFailure(String name)
	{
		String profile=activeProfile;long token=generation.get(),timestamp=System.currentTimeMillis();
		SourceIdentity identity=new SourceIdentity("PICKPOCKET",name,"outcome=failed",client.getWorldType().toString());
		RuntimeData current=runtime;if(current!=null&&current.rates!=null) { identity=current.rates.resolveSource(identity); }
		SourceIdentity captured=identity;
		submit(r->{
			if(r.engine==null||!profile.equals(r.profile)) { return; }
			try { r.engine.failPickpocket(captured,timestamp);r.store.save(profile,r.engine.lifetime()); }
			catch(Exception ex) { r.error="Pickpocket failure save failed; tracking paused. Check RuneLite logs.";log.warn(r.error,ex);r.engine=null; }
			publishOnWorker(r,token);
		});
	}
	@Subscribe public void onActorDeath(net.runelite.api.events.ActorDeath event)
	{
		if(!running||activeProfile==null||!(event.getActor() instanceof net.runelite.api.NPC)) { return; }
		net.runelite.api.NPC npc=(net.runelite.api.NPC)event.getActor();net.runelite.api.NPCComposition composition=npc.getTransformedComposition();
		if(composition==null) { return; }
		net.runelite.api.Player local=client.getLocalPlayer();net.runelite.api.coords.WorldPoint point=npc.getWorldLocation(),player=local==null?null:local.getWorldLocation();
		boolean unknownView=local==null||npc.getWorldView()==null||npc.getWorldView()!=local.getWorldView()||npc.getWorldView().isInstance();
		Boolean wild=com.lootluck.tracking.NpcDeathContexts.wilderness(point,player,client.getVarbitValue(net.runelite.api.gameval.VarbitID.INSIDE_WILDERNESS)==1,unknownView);
		npcDeaths.record(npc.getIndex(),composition.getId(),client.getTickCount(),point,wild);
	}
	@Subscribe(priority=1) public void onServerNpcLoot(net.runelite.client.events.ServerNpcLoot event)
	{
		if(!running||activeProfile==null) { return; }
		// Runs before core Loot Tracker emits its nested processed event. Identity is the event's
		// collection object, never its contents, so repeated genuine rewards remain distinct.
		pendingNpcItems=event.getItems();pendingNpcContext=npcDeaths.consume(event.getComposition().getId(),client.getTickCount());
	}
	private void recordLoot(SourceIdentity source,java.util.Collection<ItemStack> items,long amount)
	{
		String profile=activeProfile;long token=generation.get();
		Map<Integer,Long> quantities=new LinkedHashMap<>();Map<Integer,String> itemNames=new LinkedHashMap<>();Map<Integer,Integer> originals=new LinkedHashMap<>();
		if(items!=null) { for(ItemStack stack:items)
		{
			if(stack.getId()<=0||stack.getQuantity()<=0) { continue; }
			ItemComposition original=itemManager.getItemComposition(stack.getId());
			int id=stack.getId();boolean noted=original.getNote()!=-1;
			ItemComposition canonical=noted?itemManager.getItemComposition(original.getLinkedNoteId()):original;String name=canonical.getName()+(noted?" (noted)":"");
			quantities.merge(id,(long)stack.getQuantity(),Math::addExact);itemNames.put(id,name);originals.put(stack.getId(),noted?original.getLinkedNoteId():id);
			identities.putIfAbsent(ViewBuilder.normalize(name),id);
		} }
		Observation observation=new Observation(source,System.currentTimeMillis(),amount,quantities,itemNames,originals);
		RuntimeData current=runtime;java.util.List<com.lootluck.rates.RateRow> modelRates=current==null||current.rates==null?java.util.List.of():current.rates.rows(source);
		java.util.List<com.lootluck.rates.RateRow> recordedRates=com.lootluck.rates.TableResolver.resolve(source,modelRates).rows;
		submit(r->{
			// Ordered activation precedes its events; a queued old-profile event cannot write into the new profile.
			if(r.engine==null||!profile.equals(r.profile)) { return; }
			try
			{
				r.engine.observe(observation,r.validation.assess(observation,modelRates,approval(profile,source)),recordedRates);r.store.save(profile,r.engine.lifetime());
			}
			catch(Exception ex) { r.error="Observation save failed; tracking paused. Check RuneLite logs.";log.warn(r.error,ex);r.engine=null; }
			if(r.rates!=null) { r.rates.refresh(source,false); }publishOnWorker(r,token);
		});
	}
	private String approval(String profile,SourceIdentity source)
	{
		return configManager.getConfiguration(LootLuckConfig.GROUP,"verified-"+com.lootluck.rates.WikiParser.hash(profile+"|"+source.family().key()));
	}
	private void confirmGameplay(String sourceKey)
	{
		String profile=activeProfile;RuntimeData selected=runtime;long token=generation.get();
		if(profile==null||selected==null) { return; }
		int choice=javax.swing.JOptionPane.showConfirmDialog(panel,
			"Confirm only after testing this source in-game:\nCount several kills against recorded outcomes, including empty loot.\nCheck item quantities, repeats, NPC variants and world type.\n\nEnable its reviewed luck models for future attempts?",
			"Confirm gameplay validation",javax.swing.JOptionPane.YES_NO_OPTION);
		if(choice!=javax.swing.JOptionPane.YES_OPTION) { return; }
		submit(r->{
			if(r!=selected||token!=generation.get()||r.engine==null||!profile.equals(r.profile)) { return; }
			Ledger.Source family=r.engine.lifetime().sources.get(sourceKey);
			if(family==null) { return; }
			String contract=r.validation.contract(family.identity,r.rates.rows(family.identity));
			if(contract.isEmpty()) { return; }
			configManager.setConfiguration(LootLuckConfig.GROUP,"verified-"+com.lootluck.rates.WikiParser.hash(profile+"|"+family.identity.key()),contract);
			r.engine.interruptCoverage();publishOnWorker(r,token);
		});
	}
	@Subscribe public void onMenuOptionClicked(net.runelite.api.events.MenuOptionClicked event)
	{
		if(!running||activeProfile==null||client.getGameState()!=GameState.LOGGED_IN) { return; }
		if("Pickpocket".equals(event.getMenuOption())&&event.getMenuAction().name().startsWith("NPC_")) { pickpockets.selected(net.runelite.client.util.Text.removeTags(event.getMenuTarget())); }
		collectMazeReward();
		// Chest reward dialogues can require a Continue action before inventory delivery.
		if(event.getMenuAction()==net.runelite.api.MenuAction.WIDGET_CONTINUE||"Continue".equals(event.getMenuOption())) { return; }
		com.lootluck.tracking.MazeRewardTracker.Reward mazeReward=
			event.getMenuAction()==net.runelite.api.MenuAction.GAME_OBJECT_FIRST_OPTION?
			com.lootluck.tracking.MazeRewardTracker.interaction(event.getId(),event.getMenuOption()):null;
		// A new player action cancels an unfulfilled selection, including a distant/cancelled click.
		mazeRewards.clear();mazeTarget=null;
		if(mazeReward!=null&&client.getLocalPlayer()!=null)
		{
			mazeTarget=net.runelite.api.coords.WorldPoint.fromScene(client.getLocalPlayer().getWorldView(),event.getParam0(),event.getParam1(),client.getLocalPlayer().getWorldLocation().getPlane());
			mazeRewards.arm(mazeReward,inventorySnapshot(),client.getTickCount(),nearMazeTarget());
		}
		if(event.getItemId()==net.runelite.api.gameval.ItemID.MACRO_QUIZ_MYSTERY_BOX&&"Open".equals(event.getMenuOption()))
		{
			collectMysteryBox();mysteryBoxes.arm(inventorySnapshot(),client.getTickCount());
		}
	}
	@Subscribe(priority=1) public void onChatMessage(net.runelite.api.events.ChatMessage event)
	{
		if(!running||activeProfile==null) { return; }
		if(event.getType()==net.runelite.api.ChatMessageType.GAMEMESSAGE||event.getType()==net.runelite.api.ChatMessageType.SPAM)
		{
			String message=net.runelite.client.util.Text.removeTags(event.getMessage());mysteryBoxes.message(message);
			String failed=pickpockets.message(message,client.getTickCount(),roguePieces());
			if(failed!=null) { recordPickpocketFailure(failed); }
		}
	}
	@Subscribe public void onGameTick(net.runelite.api.events.GameTick event) { flushPickpockets(true);collectMysteryBox();collectMazeReward(); }
	private boolean nearMazeTarget()
	{
		return mazeTarget!=null&&client.getLocalPlayer()!=null&&mazeTarget.distanceTo(client.getLocalPlayer().getWorldLocation())<=2;
	}
	private void collectMazeReward()
	{
		if(!running||activeProfile==null||client.getGameState()!=GameState.LOGGED_IN||!mazeRewards.pending()) { return; }
		com.lootluck.tracking.MazeRewardTracker.Reward reward=mazeRewards.reward();
		Map<Integer,Long> gained=mazeRewards.poll(inventorySnapshot(),client.getTickCount(),nearMazeTarget(),id->{
			ItemComposition item=itemManager.getItemComposition(id);return item.getNote()==-1?id:item.getLinkedNoteId();
		});
		if(!mazeRewards.pending()) { mazeTarget=null; }
		if(!gained.isEmpty())
		{
			java.util.List<ItemStack> items=new java.util.ArrayList<>();gained.forEach((id,quantity)->items.add(new ItemStack(id,Math.toIntExact(quantity))));
			recordLoot(new SourceIdentity("EVENT",reward.name,"manual-interaction;inventory-confirmed",client.getWorldType().toString(),reward.wikiOwner),items,1);
		}
	}
	private Map<Integer,Long> inventorySnapshot()
	{
		Map<Integer,Long> items=new LinkedHashMap<>();net.runelite.api.ItemContainer inventory=client.getItemContainer(net.runelite.api.gameval.InventoryID.INV);
		if(inventory!=null) { for(net.runelite.api.Item item:inventory.getItems()) { if(item.getId()>0&&item.getQuantity()>0) { items.merge(item.getId(),(long)item.getQuantity(),Math::addExact); } } }
		return items;
	}
	private void collectMysteryBox()
	{
		if(!running||activeProfile==null||!mysteryBoxes.pending()) { return; }
		Map<Integer,Long> gained=mysteryBoxes.poll(inventorySnapshot(),client.getTickCount());
		if(gained.size()>1) { log.debug("Ambiguous mystery box inventory change; skipping observation");return; }
		if(!gained.isEmpty())
		{
			java.util.List<ItemStack> items=new java.util.ArrayList<>();gained.forEach((id,quantity)->items.add(new ItemStack(id,Math.toIntExact(quantity))));
			recordLoot(new SourceIdentity("EVENT","Mystery box","manual-open;inventory-confirmed",client.getWorldType().toString()),items,1);
		}
	}
	private void startIdentityScan()
	{
		if(scanning||scanCursor>=client.getItemCount()||client.getGameState().ordinal()<GameState.LOGIN_SCREEN.ordinal()) { return; }
		scanning=true;clientThread.invokeLater(()->{
			if(!running) { scanning=false;return true; }
			int end=Math.min(scanCursor+256,client.getItemCount());
			for(;scanCursor<end;scanCursor++)
			{
				ItemComposition item=client.getItemDefinition(scanCursor);
				if(item==null||item.getPlaceholderTemplateId()!=-1||item.getName()==null||"null".equals(item.getName())) { continue; }
				identities.putIfAbsent(ViewBuilder.normalize(item.getName()+(item.getNote()!=-1?" (noted)":"")),scanCursor);
			}
			if(scanCursor>=client.getItemCount()) { scanning=false;publish();return true; }return false;
		});
	}
	private javax.swing.Icon previewIcon(boolean quantity)
	{
		net.runelite.client.util.AsyncBufferedImage image=itemManager.getImage(net.runelite.api.gameval.ItemID.COINS,12500,false);
		if(image==null) { return null; }
		image.onLoaded(()->SwingUtilities.invokeLater(()->{if(panel!=null) { panel.repaint(); }}));return new ImageIcon(image);
	}
	private javax.swing.Icon observationIcon(int id,long quantity)
	{
		net.runelite.client.util.AsyncBufferedImage image=itemManager.getImage(id,(int)Math.max(1,Math.min(Integer.MAX_VALUE,quantity)),false);if(image==null) { return null; }image.onLoaded(()->SwingUtilities.invokeLater(()->{if(panel!=null) { panel.repaint(); }}));return new ImageIcon(image);
	}
	private void resetSession()
	{
		if(!SwingUtilities.isEventDispatchThread()) { throw new IllegalStateException("Swing thread required"); }
		String profile=activeProfile;RuntimeData selected=runtime;long token=generation.get();
		if(profile==null||selected==null) { return; }
		if(javax.swing.JOptionPane.showConfirmDialog(panel,"Clear this account's current session loot totals and collection history?\nLifetime data will be kept. New loot starts a fresh session.","Reset session",javax.swing.JOptionPane.OK_CANCEL_OPTION,javax.swing.JOptionPane.WARNING_MESSAGE)!=javax.swing.JOptionPane.OK_OPTION) { return; }
		submit(r->{
			if(r!=selected||token!=generation.get()||!profile.equals(activeProfile)||!profile.equals(r.profile)||r.engine==null) { return; }
			r.engine.resetSession();publishOnWorker(r,token);
		});
	}
	private void manageData(String action)
	{
		if(!SwingUtilities.isEventDispatchThread()) { throw new IllegalStateException("Swing thread required"); }
		String profile=activeProfile;RuntimeData selected=runtime;long token=generation.get();
		if(profile==null||selected==null) { return; }
		boolean importing="Import data".equals(action),deleting="Delete data".equals(action);
		if(importing||deleting)
		{
			String message=importing?"Replace this account's lifetime observations with an export?\nSession counters will reset. Existing data is backed up before replacement.\nThe configured history limit still applies.":"Delete this account's lifetime observations and collection history?\nSession counters will reset. Exported files are kept.\nExport first if you want to restore this test data.";
			if(javax.swing.JOptionPane.showConfirmDialog(panel,message,action,javax.swing.JOptionPane.OK_CANCEL_OPTION,javax.swing.JOptionPane.WARNING_MESSAGE)!=javax.swing.JOptionPane.OK_OPTION) { return; }
		}
		Filepath target=null;
		if(!deleting)
		{
			Filepath.Chooser chooser=new Filepath.Chooser().setAcceptsFiles().setDialogTitle(action).addExtensionFilter("Loot Luck JSON","json");
			if(importing) { chooser.setIsOpen(); }else { chooser.setIsSave().setDefaultExtension("json").setFileName("loot-luck-export.json"); }
			java.util.List<Filepath> files=chooser.showDialog(panel);if(files==null||files.isEmpty()) { return; }target=files.get(0);
			if(!importing&&javax.swing.JOptionPane.showConfirmDialog(panel,"Export to "+target.getFileName()+"?\nAn existing file at that location will be replaced.",action,javax.swing.JOptionPane.OK_CANCEL_OPTION)!=javax.swing.JOptionPane.OK_OPTION) { return; }
		}
		Filepath chosen=target;
		submit(r->{
			if(r!=selected||token!=generation.get()||!profile.equals(activeProfile)||!profile.equals(r.profile)) { return; }
			try
			{
				if(r.store==null) { throw new java.io.IOException("Storage is not ready."); }
				if(importing)
				{
					Ledger imported=r.store.importData(profile,chosen);
					LedgerEngine replacement=new LedgerEngine(imported);replacement.setHistoryLimit(config.historyLimit());
					r.store.backupBeforeImport(profile,r.engine==null?null:r.engine.lifetime());
					r.store.save(profile,imported);r.engine=replacement;r.error="";
				}
				else if(deleting)
				{
					r.engine=null;r.store.deleteData(profile);r.engine=new LedgerEngine(new Ledger());r.engine.setHistoryLimit(config.historyLimit());r.error="";
				}
				else
				{
					if(r.engine==null) { throw new java.io.IOException("No loaded observations to export."); }
					r.store.exportData(profile,r.engine.lifetime(),chosen);
				}
				publishOnWorker(r,token);dataMessage(token,action,deleting?"Account observations deleted.":importing?"Data imported. Session reset.":"Data exported to "+chosen.getFileName()+".",false);
			}
			catch(Exception ex) { if(deleting) { r.error="Data deletion failed; tracking paused. Retry or import an export.";publishOnWorker(r,token); }log.debug("Loot Luck data action failed: {}",action,ex);dataMessage(token,action,ex.getMessage()==null?"Data operation failed. See RuneLite logs.":ex.getMessage(),true); }
		});
	}
	private void dataMessage(long token,String title,String message,boolean error)
	{
		SwingUtilities.invokeLater(()->{if(running&&token==generation.get()&&panel!=null) { javax.swing.JOptionPane.showMessageDialog(panel,message,title,error?javax.swing.JOptionPane.ERROR_MESSAGE:javax.swing.JOptionPane.INFORMATION_MESSAGE); }});
	}
	private void refresh()
	{
		submit(r->{
			if(r.rates!=null&&r.engine!=null)
			{
				if(!config.wikiRefresh()) { r.rates.status="Refresh disabled: enable Allow Wiki refresh in settings"; }
				else
				{
					r.rates.refreshCatalogue(true);
					r.rates.status="Wiki catalogue refresh queued";
					r.engine.lifetime().sourceVariants().forEach(s->r.rates.refresh(s.identity,true));
				}
			}
			publishOnWorker(r,generation.get());
		});
	}
	private void publish() { long token=generation.get();submit(r->publishOnWorker(r,token)); }
	private void publishOnWorker(RuntimeData r,long token)
	{
		if(r!=runtime || token!=generation.get()) { return; }
		PanelState view=r.engine==null||r.rates==null?new PanelState():ViewBuilder.build(session?r.engine.session():r.engine.lifetime(),r.rates,all||config.includeUnobtained(),new LinkedHashMap<>(identities),config.hideUnobtainedOtherWorld(),membersWorld,config.separateConditionVariants(),config.includeFailedPickpockets());
		view.showGe=config.showGe();view.showHa=config.showHa();view.luckColors=config.luckColors();view.showAllRates=config.showAllRates();
		view.showWikiLinks=config.showWikiLinks();
		view.includeUnobtained=config.includeUnobtained();
		view.groupByDropTable=config.groupByDropTable();
		view.hideUnobtainedOtherWorld=config.hideUnobtainedOtherWorld();
		view.gridView=config.gridView();
		view.itemEffect=config.itemEffect();
		view.canManageData=r.store!=null&&activeProfile!=null&&activeProfile.equals(r.profile);
		view.stackCounts=config.stackCounts();view.iconQuantities=config.iconQuantities()&&!config.stackCounts()&&!view.stackCounts;
		view.notice=!r.error.isEmpty()?r.error:!lootTrackerEnabled?"Enable RuneLite Loot Tracker to receive loot.":activeProfile==null?"Log in to select an account profile.":view.sources.isEmpty()?"No loot recorded yet.":"";
		view.dataStatus=r.rates==null?"Rate catalogue loading":r.rates.status+(config.wikiRefresh()?" · refresh enabled":" · offline mode");
		if(r.rates!=null&&r.validation!=null) { for(PanelState.Source source:view.sourceRows())
		{
			Ledger.Source family=r.engine==null?null:r.engine.lifetime().sources.get(source.familyKey);
			if(family==null) { continue; }
			String contract=r.validation.contract(family.identity,r.rates.rows(family.identity));
			source.validationAvailable=!contract.isEmpty();source.validationApproved=source.validationAvailable&&contract.equals(approval(r.profile,family.identity));
			if(!view.sources.contains(source)) { source.validationAvailable=false; }
		} }
		clientThread.invoke(()->{
			if(!running||token!=generation.get()) { return; }
			for(PanelState.Source source:view.sourceRows())
			{
				long total=0;
				for(PanelState.Item item:source.items)
				{
					if(item.id<=0) { continue; }if(item.quantity==0) { item.valueAvailable=true;continue; }ItemComposition comp=itemManager.getItemComposition(item.id);if(comp.getNote()!=-1) { comp=itemManager.getItemComposition(comp.getLinkedNoteId()); }
					try { item.geEach=itemManager.getItemPrice(item.id);item.haEach=comp.getHaPrice();item.ge=Math.multiplyExact(item.geEach,item.quantity);item.ha=Math.multiplyExact(item.haEach,item.quantity);total=Math.addExact(total,item.ge);item.valueAvailable=true; }
					catch(ArithmeticException ex) { item.valueAvailable=false; }
				}
				source.value=LootLuckPanel.money(total);
			}
			SwingUtilities.invokeLater(()->{if(running&&token==generation.get()&&panel!=null) { panel.update(view); }});
		});
	}
	private synchronized void submit(java.util.function.Consumer<RuntimeData> task)
	{
		RuntimeData r=runtime;if(r==null||!running) { return; }
		if(pendingTasks.incrementAndGet()>1024) { pendingTasks.decrementAndGet();r.error="Processing queue full; tracking paused. Restart plugin after resolving disk issues.";running=false;log.warn(r.error);return; }
		ioTail=ioTail.handle((ignored,failure)->null).thenRunAsync(()->{try { task.accept(r); } finally { pendingTasks.decrementAndGet(); }},executor);
	}
	@Override protected void shutDown()
	{
		flushPickpockets(false);pickpockets.clear();
		mysteryBoxes.clear();
		mazeRewards.clear();mazeTarget=null;
		npcDeaths.clear();pendingNpcItems=null;pendingNpcContext="";
		running=false;generation.incrementAndGet();RuntimeData run=runtime;if(run!=null&&run.rates!=null) { run.rates.close(); }
		// Already captured immutable observations finish on the storage worker; never block the client thread.
		// Shared executor is not shut down; only previously accepted immutable observations drain.
		NavigationButton oldNavigation=navigation;LootLuckPanel oldPanel=panel;SwingUtilities.invokeLater(()->{if(oldNavigation!=null) { toolbar.removeNavigation(oldNavigation); }if(panel==oldPanel) { panel=null; }});
	}
	@Provides LootLuckConfig provideConfig(ConfigManager manager) { return manager.getConfig(LootLuckConfig.class); }
}



