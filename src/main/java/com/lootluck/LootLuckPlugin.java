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
import java.awt.Color;
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
		String profile,error="";
	}
	private volatile RuntimeData runtime;
	private java.util.concurrent.CompletableFuture<Void> ioTail=java.util.concurrent.CompletableFuture.completedFuture(null);
	private final java.util.concurrent.atomic.AtomicInteger pendingTasks=new java.util.concurrent.atomic.AtomicInteger();
	private volatile String activeProfile;
	private volatile boolean running,session,all,lootTrackerEnabled;
	private final AtomicLong generation=new AtomicLong();
	private final ValidationRegistry validation=new ValidationRegistry();
	private final Map<String,Integer> identities=new ConcurrentHashMap<>();
	private int scanCursor;
	private boolean scanning;

	@Override protected void startUp()
	{
		running=true;generation.incrementAndGet();RuntimeData run=new RuntimeData();runtime=run;
		// The injected executor belongs to RuneLite. A serial future chain preserves old-profile writes across restarts.
		SwingUtilities.invokeLater(()->{
			if(!running||run!=runtime) { return; }
			panel=new LootLuckPanel((id,quantity)->observationIcon(id,quantity),value->{session=value;all=panel.driest();publish();},value->{configManager.setConfiguration(LootLuckConfig.GROUP,"includeUnobtained",value);publish();},this::resetSession,this::refresh);
			panel.setDataAction(this::manageData);
			BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D g=image.createGraphics();g.setColor(new Color(223,192,113));g.drawString("LL",0,12);g.dispose();
			navigation=NavigationButton.builder().tooltip("Loot Luck").icon(image).priority(6).panel(panel).build();toolbar.addNavigation(navigation);publish();
		});
		submit(r->{
			try
			{
				Filepath directory=getPluginDirectory();r.store=new LedgerStore(directory.joinSegment("profiles"),gson);
				r.rates=new WikiRateProvider(gson,http,directory.joinSegment("wiki-cache"),config::wikiRefresh,()->{if(r==runtime&&running) { publish(); }});r.rates.initialize();
				if(!running||r!=runtime) { r.rates.close(); }
			}
			catch(Exception ex) { r.error="Storage/rate initialization failed; tracking paused.";log.warn(r.error,ex); }
		});
		clientThread.invoke(()->{if(running&&run==runtime) { checkPrerequisite();activate(configManager.getRSProfileKey());startIdentityScan(); }});
	}
	private void activate(String key)
	{
		activeProfile=key;long token=generation.incrementAndGet();RuntimeData run=runtime;if(run!=null&&run.rates!=null) { run.rates.cancelRequests(); }
		submit(r->{
			r.profile=key;r.engine=null;
			try { if(key!=null&&r.store!=null) { r.engine=new LedgerEngine(r.store.load(key));r.engine.setHistoryLimit(config.historyLimit());r.error=""; } }
			catch(Exception ex) { r.error="Profile snapshot could not be recovered; tracking paused.";log.warn(r.error,ex); }
			publishOnWorker(r,token);
		});
	}
	@Subscribe public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) { activate(event.getNewProfile()); }
	@Subscribe public void onGameStateChanged(GameStateChanged event)
	{
		if(event.getGameState()==GameState.LOGGED_IN||event.getGameState()==GameState.LOGIN_SCREEN) { checkPrerequisite();startIdentityScan();publish(); }
	}
	@Subscribe public void onPluginChanged(PluginChanged event) { checkPrerequisite();publish(); }
	private void checkPrerequisite() { lootTrackerEnabled=pluginManager.getPlugins().stream().anyMatch(p->p instanceof LootTrackerPlugin&&pluginManager.isPluginEnabled(p)); }
	@Subscribe public void onConfigChanged(ConfigChanged event)
	{
		if(LootLuckConfig.GROUP.equals(event.getGroup())&&"historyLimit".equals(event.getKey())) { submit(r->{if(r.engine!=null&&r.store!=null&&r.profile!=null) { r.engine.setHistoryLimit(config.historyLimit());try { r.store.save(r.profile,r.engine.lifetime()); }catch(Exception ex) { r.error="History setting save failed.";log.warn(r.error,ex); } }publishOnWorker(r,generation.get());}); }
		if(LootLuckConfig.GROUP.equals(event.getGroup())) { RuntimeData run=runtime;if(run!=null&&run.rates!=null) { if(!config.wikiRefresh()) { run.rates.cancelRequests(); }else { run.rates.refreshCatalogue(false); } }publish(); }
	}
	@Subscribe public void onLootReceived(LootReceived event)
	{
		if(!running||activeProfile==null||event.getType()==null||"PLAYER".equals(event.getType().name())||event.getName()==null||event.getName().isBlank()) { return; }
		String profile=activeProfile;long token=generation.get();
		String metadata=event.getMetadata() instanceof Number?String.valueOf(event.getMetadata()):"unresolved";
		SourceIdentity source=new SourceIdentity(event.getType().name(),event.getName(),"combat="+event.getCombatLevel()+";metadata="+metadata,client.getWorldType().toString());
		Map<Integer,Long> quantities=new LinkedHashMap<>();Map<Integer,String> itemNames=new LinkedHashMap<>();Map<Integer,Integer> originals=new LinkedHashMap<>();
		if(event.getItems()!=null) { for(ItemStack stack:event.getItems())
		{
			if(stack.getId()<=0||stack.getQuantity()<=0) { continue; }
			ItemComposition original=itemManager.getItemComposition(stack.getId());
			int id=stack.getId();boolean noted=original.getNote()!=-1;
			ItemComposition canonical=noted?itemManager.getItemComposition(original.getLinkedNoteId()):original;String name=canonical.getName()+(noted?" (noted)":"");
			quantities.merge(id,(long)stack.getQuantity(),Math::addExact);itemNames.put(id,name);originals.put(stack.getId(),id);
			identities.putIfAbsent(ViewBuilder.normalize(name),id);
		} }
		Observation observation=new Observation(source,System.currentTimeMillis(),Math.max(0,event.getAmount()),quantities,itemNames,originals);
		RuntimeData current=runtime;java.util.List<com.lootluck.rates.RateRow> recordedRates=current==null||current.rates==null?java.util.List.of():current.rates.rows(source);
		submit(r->{
			// Ordered activation precedes its events; a queued old-profile event cannot write into the new profile.
			if(r.engine==null||!profile.equals(r.profile)) { return; }
			try { r.engine.observe(observation,validation.assess(observation),recordedRates);r.store.save(profile,r.engine.lifetime()); }
			catch(Exception ex) { r.error="Observation save failed; tracking paused. Check RuneLite logs.";log.warn(r.error,ex);r.engine=null; }
			if(r.rates!=null) { r.rates.refresh(source,false); }publishOnWorker(r,token);
		});
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
	private void prepareIcon(PanelState.Item item)
	{
		int quantity=(int)Math.max(1,Math.min(Integer.MAX_VALUE,item.quantity));
		net.runelite.client.util.AsyncBufferedImage image=itemManager.getImage(item.id,quantity,config.iconQuantities()&&!config.stackCounts()&&item.quantity>0);if(image==null) { return; }item.icon=new ImageIcon(image);
		image.onLoaded(()->SwingUtilities.invokeLater(()->{if(panel!=null) { panel.repaint(); }}));
	}
	private javax.swing.Icon observationIcon(int id,long quantity)
	{
		net.runelite.client.util.AsyncBufferedImage image=itemManager.getImage(id,(int)Math.max(1,Math.min(Integer.MAX_VALUE,quantity)),config.iconQuantities()&&!config.stackCounts()&&quantity>0);if(image==null) { return null; }image.onLoaded(()->SwingUtilities.invokeLater(()->{if(panel!=null) { panel.repaint(); }}));return new ImageIcon(image);
	}
	private void resetSession() { submit(r->{if(r.engine!=null) { r.engine.resetSession(); }publishOnWorker(r,generation.get());}); }
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
					r.engine.lifetime().sources.values().forEach(s->r.rates.refresh(s.identity,true));
				}
			}
			publishOnWorker(r,generation.get());
		});
	}
	private void publish() { long token=generation.get();submit(r->publishOnWorker(r,token)); }
	private void publishOnWorker(RuntimeData r,long token)
	{
		if(r!=runtime || token!=generation.get()) { return; }
		PanelState view=r.engine==null||r.rates==null?new PanelState():ViewBuilder.build(session?r.engine.session():r.engine.lifetime(),r.rates,all||config.includeUnobtained(),new LinkedHashMap<>(identities));
		view.showGe=config.showGe();view.showHa=config.showHa();view.luckColors=config.luckColors();view.showAllRates=config.showAllRates();
		view.showWikiLinks=config.showWikiLinks();
		view.includeUnobtained=config.includeUnobtained();
		view.canManageData=r.store!=null&&activeProfile!=null&&activeProfile.equals(r.profile);
		view.stackCounts=config.stackCounts();view.iconQuantities=config.iconQuantities()&&!config.stackCounts()&&!view.stackCounts;
		view.notice=!r.error.isEmpty()?r.error:!lootTrackerEnabled?"Enable RuneLite Loot Tracker to receive loot.":activeProfile==null?"Log in to select an account profile.":view.sources.isEmpty()?"No loot recorded yet.":"";
		view.dataStatus=r.rates==null?"Rate catalogue loading":r.rates.status+(config.wikiRefresh()?" · refresh enabled":" · offline mode");
		clientThread.invoke(()->{
			if(!running||token!=generation.get()) { return; }
			for(PanelState.Source source:view.sources)
			{
				long total=0;
				for(PanelState.Item item:source.items)
				{
					if(item.id<=0) { continue; }prepareIcon(item);ItemComposition comp=itemManager.getItemComposition(item.id);if(comp.getNote()!=-1) { comp=itemManager.getItemComposition(comp.getLinkedNoteId()); }
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
		if(pendingTasks.incrementAndGet()>1024) { pendingTasks.decrementAndGet();r.error="Processing queue full; coverage interrupted. Restart plugin after resolving disk issues.";log.warn(r.error);return; }
		ioTail=ioTail.handle((ignored,failure)->null).thenRunAsync(()->{try { task.accept(r); } finally { pendingTasks.decrementAndGet(); }},executor);
	}
	@Override protected void shutDown()
	{
		running=false;generation.incrementAndGet();RuntimeData run=runtime;if(run!=null&&run.rates!=null) { run.rates.close(); }
		// Already captured immutable observations finish on the storage worker; never block the client thread.
		// Shared executor is not shut down; only previously accepted immutable observations drain.
		NavigationButton oldNavigation=navigation;LootLuckPanel oldPanel=panel;SwingUtilities.invokeLater(()->{if(oldNavigation!=null) { toolbar.removeNavigation(oldNavigation); }if(panel==oldPanel) { panel=null; }});
	}
	@Provides LootLuckConfig provideConfig(ConfigManager manager) { return manager.getConfig(LootLuckConfig.class); }
}


