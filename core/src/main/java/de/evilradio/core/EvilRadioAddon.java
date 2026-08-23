package de.evilradio.core;

import de.evilradio.core.activity.picker.RadioStationListActivity;
import de.evilradio.core.activity.picker.RadioStationListOpener;
import de.evilradio.core.command.ListenMashupCommand;
import de.evilradio.core.configuration.EvilRadioConfiguration;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import de.evilradio.core.listener.ActivityListener;
import de.evilradio.core.listener.GameListener;
import de.evilradio.core.radio.RadioManager;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.radio.RadioStreamService;
import de.evilradio.core.schedule.ScheduleService;
import de.evilradio.core.song.CurrentSongService;
import net.labymod.api.Laby;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.hud.binding.category.HudWidgetCategory;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.models.addon.annotation.AddonMain;
import net.labymod.api.notification.Notification;
import net.labymod.api.revision.SimpleRevision;
import net.labymod.api.util.concurrent.task.Task;
import net.labymod.api.util.version.SemanticVersion;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@AddonMain
public class EvilRadioAddon extends LabyAddon<EvilRadioConfiguration> {

  private static EvilRadioAddon instance;

  public static final HudWidgetCategory HUD_WIDGET_CATEGORY = new HudWidgetCategory("evilradio");

  private RadioManager radioManager;
  private RadioStreamService radioStreamService;
  private CurrentSongService currentSongService;
  private ScheduleService scheduleService;

  private CurrentSongHudWidget currentSongHudWidget;
  private ActivityListener activityListener;
  private RadioStationListActivity radioStationListActivity;

  private boolean wasWindowFocused = true;
  private RadioStream streamBeforeFocusLoss = null;
  private boolean userManuallyStopped = false;

  private static final Set<String> ALLOWED_UUIDS = Set.of(
      "308893af-77af-4706-ac8a-1c4830038108",
      "966b5d5e-2577-4ab7-987a-89bfa59da74a"
  );

  @Override
  protected void preConfigurationLoad() {
    Laby.references().revisionRegistry().register(new SimpleRevision("evilradio", new SemanticVersion(1, 0, 2), "2026-02-21"));
    Laby.references().revisionRegistry().register(new SimpleRevision("evilradio", new SemanticVersion(1, 0, 3), "2026-03-02"));
    Laby.references().revisionRegistry().register(new SimpleRevision("evilradio", new SemanticVersion(1, 0, 4), "2026-04-22"));
    Laby.references().revisionRegistry().register(new SimpleRevision("evilradio", new SemanticVersion(1, 1, 0), "2026-07-18"));
  }

  @Override
  protected void enable() {
    
    this.registerSettingCategory();
    instance = this;

    this.radioManager = new RadioManager(this);

    this.currentSongService = new CurrentSongService(this);
    this.currentSongService.startUpdater();

    this.scheduleService = new ScheduleService(this);
    this.scheduleService.startScheduleChecker();

    this.radioStreamService = new RadioStreamService(this);
    this.radioStreamService.loadStreams(() -> {
      // Nach dem Laden der Streams: Prüfe, ob Auto-Start beim Spielstart aktiviert ist
      if (configuration().autoStartMode().get() == EvilRadioConfiguration.AutoStartMode.DISABLED) return;
      EvilRadioConfiguration.AutoStartMode mode = configuration().autoStartMode().get();
      if (mode != null && mode.shouldStartOnGameStart()) {
        this.startLastStreamWithDelay("game start");
      }
    });

    this.radioStationListActivity = new RadioStationListActivity(this);
    
    // Event-Bus registrieren für Event-Handler
    this.labyAPI().eventBus().registerListener(new GameListener(this));
    this.labyAPI().eventBus().registerListener(this.activityListener = new ActivityListener(this));

    this.registerCommand(new ListenMashupCommand(this));

    this.labyAPI().eventBus().registerListener(new RadioStationListOpener(this));

    this.labyAPI().hudWidgetRegistry().categoryRegistry().register(HUD_WIDGET_CATEGORY);
    this.labyAPI().hudWidgetRegistry().register(this.currentSongHudWidget = new CurrentSongHudWidget(this));

    // Registriere Window-Focus-Listener für Auto-Stop
    this.setupWindowFocusListener();

    this.logger().info("Enabled the Addon");

    // Setze initiales Volume aus der Konfiguration
    if (this.radioManager != null) {
      this.radioManager.setVolume(configuration().volume().get() / 100.0f);
    }
    
    // Registriere Listener für Volume-Änderungen
    configuration().volume().addChangeListener((volume) -> {
      if (this.radioManager != null) {
        this.radioManager.setVolume(volume / 100.0f);
      }
    });
    
    // Stoppe den Stream, wenn das Addon deaktiviert wird
    configuration().enabled().addChangeListener((enabled) -> {
      if (!enabled && this.radioManager != null && this.radioManager.isPlaying()) {
        this.radioManager.stopStream();
        this.logger().info("Stream has been stopped since the Addon has been disabled.");
      }
      if (!enabled && this.currentSongService != null) {
        this.currentSongService.stopUpdater();
      } else if (enabled && this.currentSongService != null) {
        this.currentSongService.startUpdater();
      }
    });

    configuration().debugForceMashupLive().visibilitySupplier(
        () -> isUuidAllowed(this.labyAPI().getUniqueId()));

  }

  @Override
  protected Class<EvilRadioConfiguration> configurationClass() {
    return EvilRadioConfiguration.class;
  }

  public void notification(Component title, Component text) {
    this.labyAPI().notificationController().push(Notification.builder()
        .title(title)
        .text(text)
        .icon(EvilTextures.LOGO)
        .build()
    );
  }

  public void notification(Component title, Component text, Icon icon, Icon secondaryIcon) {
    if(icon == null) {
      icon = EvilTextures.LOGO;
    }
    if(secondaryIcon == null) {
      secondaryIcon = EvilTextures.LOGO;
    }
    this.labyAPI().notificationController().push(Notification.builder()
        .title(title)
        .text(text)
        .icon(icon)
        .secondaryIcon(secondaryIcon)
        .build()
    );
  }

  public void requestHudWidgetUpdate(String reason) {
    this.labyAPI().minecraft().executeOnRenderThread(() -> {
      if(this.currentSongHudWidget.isEnabled()) {
        this.currentSongHudWidget.requestUpdate(reason);
      }
      if(this.activityListener != null) {
        this.activityListener.update(reason);
      }
    });
  }

  public static EvilRadioAddon instance() {
    return instance;
  }

  public RadioManager radioManager() {
    return radioManager;
  }

  public RadioStreamService radioStreamService() {
    return radioStreamService;
  }

  public CurrentSongService currentSongService() {
    return currentSongService;
  }
  
  public ScheduleService scheduleService() {
    return scheduleService;
  }

  public void openStationPicker() {
    if (!this.configuration().enabled().get() || this.radioStationListActivity == null) return;
    this.labyAPI().minecraft().executeNextTick(() ->
        this.labyAPI().minecraft().minecraftWindow()
            .displayScreen(this.radioStationListActivity));
  }

  public boolean isUuidAllowed(UUID uuid) {
    return uuid != null && ALLOWED_UUIDS.contains(uuid.toString());
  }

  /**
   * Addon-Version aus der Laby-AddonInfo (Fallback {@code unknown}).
   */
  public String addonVersion() {
    try {
      if (this.addonInfo() != null && this.addonInfo().getVersion() != null) {
        return this.addonInfo().getVersion();
      }
    } catch (Throwable ignored) {
      // AddonInfo ggf. noch nicht bereit
    }
    return "unknown";
  }

  /**
   * User-Agent für Evil-Radio-API-Calls inkl. Addon-Version.
   */
  public String apiUserAgent() {
    return "EvilRadio LabyMod 4 Addon/" + this.addonVersion();
  }

  /**
   * Registriert einen periodischen Check für Window-Focus-Verlust
   * Prüft alle 500ms, ob das Fenster den Fokus verloren hat
   */
  private void setupWindowFocusListener() {
    var window = this.labyAPI().minecraft().minecraftWindow();
    if (window != null) {
      // Initialisiere den Focus-Status
      this.wasWindowFocused = window.isFocused();
      
      // Verwende einen periodischen Check (alle 500ms) statt bei jedem Tick
      Task.builder(() -> {
        if (!configuration().autoStopOnFocusLoss().get()) {
          return;
        }
        
        boolean isFocused = window.isFocused();
        
        // Wenn das Fenster den Fokus verloren hat und der Stream läuft, stoppe ihn
        if (!isFocused && wasWindowFocused && this.radioManager != null && this.radioManager.isPlaying()) {
          // Speichere den aktuellen Stream, damit er später wieder gestartet werden kann
          this.streamBeforeFocusLoss = this.radioManager.getCurrentStream();
          this.radioManager.stopStream();
          this.logger().info("Stream gestoppt, da Fenster den Fokus verloren hat");
        }
        
        // Wenn das Fenster den Fokus wiederbekommt und ein Stream vorher lief, starte ihn wieder
        if (isFocused && !wasWindowFocused && this.streamBeforeFocusLoss != null && this.radioManager != null && !this.radioManager.isPlaying()) {
          this.radioManager.playStream(this.streamBeforeFocusLoss);
          this.logger().info("Stream wieder gestartet, da Fenster den Fokus wiederbekommen hat");
          this.streamBeforeFocusLoss = null; // Zurücksetzen nach dem Resume
        }
        
        wasWindowFocused = isFocused;
      }).repeat(500, TimeUnit.MILLISECONDS).build().execute();
    }
  }
  
  /**
   * Startet den letzten Stream mit konfigurierter Verzögerung
   * @param context Kontext für Logging (z.B. "game start", "server join")
   */
  public void startLastStreamWithDelay(String context) {
    // Prüfe zuerst, ob Auto-Start überhaupt aktiviert ist
    if (configuration().autoStartMode().get() == EvilRadioConfiguration.AutoStartMode.DISABLED) return;
    
    int lastStreamId = configuration().lastStreamId().get();
    if (lastStreamId < 0) return;
    
    RadioStream lastStream = this.radioStreamService.findStreamById(lastStreamId);
    if (lastStream == null || lastStream.getUrl() == null || lastStream.getUrl().isEmpty()) return;
    
    float delaySeconds = configuration().autoStartDelay().get();
    
    if (delaySeconds > 0) {
      // Starte mit Verzögerung
      Task.builder(() -> {
        this.startLastStream(lastStream, context);
      }).delay((long)(delaySeconds * 1000), TimeUnit.MILLISECONDS).build().execute();
    } else {
      // Starte sofort
      this.startLastStream(lastStream, context);
    }
  }
  
  /**
   * Startet den letzten Stream
   * @param stream Der zu startende Stream
   * @param context Kontext für Logging
   */
  private void startLastStream(RadioStream stream, String context) {
    if (stream == null || stream.getUrl() == null || stream.getUrl().isEmpty()) return;
    
    // Prüfe, ob derselbe Stream bereits läuft (verhindert Pause beim Subserver-Wechsel)
    RadioStream currentStream = this.radioManager.getCurrentStream();
    boolean isSameStream = currentStream != null && currentStream.getId() == stream.getId();
    
    // Wenn derselbe Stream bereits läuft, tue nichts (verhindert Pause beim Subserver-Wechsel)
    if (isSameStream && this.radioManager.isPlaying()) return;
    
    // Wenn der Benutzer das Radio manuell startet, setze die Flag zurück
    this.userManuallyStopped = false;
    
    this.radioManager.playStream(stream);
  }
  
  /**
   * Markiert, dass der Benutzer das Radio manuell gestoppt hat
   */
  public void setUserManuallyStopped(boolean stopped) {
    this.userManuallyStopped = stopped;
  }
  
  /**
   * Gibt zurück, ob der Benutzer das Radio manuell gestoppt hat
   */
  public boolean isUserManuallyStopped() {
    return this.userManuallyStopped;
  }
  
}


