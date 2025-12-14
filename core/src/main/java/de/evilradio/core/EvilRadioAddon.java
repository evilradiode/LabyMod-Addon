package de.evilradio.core;

import de.evilradio.core.configuration.AutoStartSubSettings;
import de.evilradio.core.configuration.EvilRadioConfiguration;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import de.evilradio.core.radio.RadioManager;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.radio.RadioStreamService;
import de.evilradio.core.song.CurrentSongService;
import de.evilradio.core.activity.wheel.RadioWheelOverlay;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.models.addon.annotation.AddonMain;
import net.labymod.api.notification.Notification;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.network.server.ServerJoinEvent;
import net.labymod.api.event.client.world.WorldEnterEvent;
import net.labymod.api.event.client.world.WorldLeaveEvent;
import net.labymod.api.util.concurrent.task.Task;
import java.util.concurrent.TimeUnit;

@AddonMain
public class EvilRadioAddon extends LabyAddon<EvilRadioConfiguration> {

  private static EvilRadioAddon instance;

  private RadioManager radioManager;
  private RadioStreamService radioStreamService;
  private CurrentSongService currentSongService;

  private CurrentSongHudWidget currentSongHudWidget;
  private Task focusCheckTask;
  private boolean wasWindowFocused = true;
  private RadioStream streamBeforeFocusLoss = null;
  private boolean userManuallyStopped = false;

  @Override
  protected void enable() {
    
    this.registerSettingCategory();
    instance = this;

    this.radioManager = new RadioManager(this);

    this.currentSongService = new CurrentSongService(this);
    this.currentSongService.startUpdater();

    this.radioStreamService = new RadioStreamService(this);
    this.radioStreamService.loadStreams(() -> {
      // Nach dem Laden der Streams: Prüfe, ob Auto-Start beim Spielstart aktiviert ist
      if (!configuration().autoStart().enabled().get()) return;
      AutoStartSubSettings.AutoStartMode mode = configuration().autoStart().mode().get();
      if (mode != null && mode.shouldStartOnGameStart()) {
        this.startLastStreamWithDelay("game start");
      }
    });
    
    // Event-Bus registrieren für Event-Handler
    this.labyAPI().eventBus().registerListener(this);

    this.labyAPI().ingameOverlay().registerActivity(new RadioWheelOverlay(this));

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
        this.logger().info("Stream gestoppt, da Addon deaktiviert wurde");
      }
    });

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

  public CurrentSongHudWidget currentSongHudWidget() {
    return currentSongHudWidget;
  }
  
  /**
   * Event-Handler für Server-Beitritt
   * Wird aufgerufen, wenn der Spieler einem Server beitritt
   */
  @Subscribe
  public void onServerJoin(ServerJoinEvent event) {
    // Prüfe, ob der Stream bereits läuft - wenn ja, tue nichts (verhindert Pause beim Subserver-Wechsel)
    if (this.radioManager != null && this.radioManager.isPlaying()) {
      return;
    }
    
    // Prüfe, ob der Benutzer das Radio manuell gestoppt hat
    if (this.userManuallyStopped) {
      return;
    }
    
    // Prüfe, ob Auto-Start aktiviert ist
    if (!configuration().autoStart().enabled().get()) {
      return;
    }
    
    AutoStartSubSettings.AutoStartMode mode = configuration().autoStart().mode().get();
    if (mode != null && mode.shouldStartOnServerJoin()) {
      this.startLastStreamWithDelay("server join");
    }
  }
  
  /**
   * Event-Handler für World-Beitritt
   * Wird aufgerufen, wenn der Spieler einer Welt beitritt (auch im Singleplayer)
   * Startet den Stream, wenn Auto-Start auf "Beim Welt betreten" steht
   */
  @Subscribe
  public void onWorldEnter(WorldEnterEvent event) {
    // Prüfe, ob der Benutzer das Radio manuell gestoppt hat
    if (this.userManuallyStopped) {
      return;
    }
    
    // Prüfe, ob Auto-Start aktiviert ist
    if (!configuration().autoStart().enabled().get()) {
      return;
    }
    
    AutoStartSubSettings.AutoStartMode mode = configuration().autoStart().mode().get();
    if (mode != null && mode.shouldStartOnServerJoin()) {
      this.startLastStreamWithDelay("world enter");
    }
  }
  
  /**
   * Event-Handler für World-Verlassen
   * Wird aufgerufen, wenn der Spieler eine Welt verlässt
   * Stoppt den Stream, wenn Auto-Start auf "Beim Welt betreten" steht
   */
  @Subscribe
  public void onWorldLeave(WorldLeaveEvent event) {
    // Prüfe, ob Auto-Start aktiviert ist und auf "Beim Welt betreten" steht
    if (!configuration().autoStart().enabled().get()) {
      return;
    }
    
    AutoStartSubSettings.AutoStartMode mode = configuration().autoStart().mode().get();
    if (mode != null && mode.shouldStartOnServerJoin()) {
      // Stoppe den Stream, wenn er läuft
      if (this.radioManager != null && this.radioManager.isPlaying()) {
        this.radioManager.stopStream();
      }
    }
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
      this.focusCheckTask = Task.builder(() -> {
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
      }).repeat(500, TimeUnit.MILLISECONDS).build();
      this.focusCheckTask.execute();
    }
  }
  
  /**
   * Startet den letzten Stream mit konfigurierter Verzögerung
   * @param context Kontext für Logging (z.B. "game start", "server join")
   */
  private void startLastStreamWithDelay(String context) {
    // Prüfe zuerst, ob Auto-Start überhaupt aktiviert ist
    if (!configuration().autoStart().enabled().get()) {
      return;
    }
    
    AutoStartSubSettings.AutoStartMode mode = configuration().autoStart().mode().get();
    if (mode == null) {
      return;
    }
    
    int lastStreamId = configuration().lastStreamId().get();
    if (lastStreamId < 0) {
      return;
    }
    
    RadioStream lastStream = this.radioStreamService.findStreamById(lastStreamId);
    if (lastStream == null || lastStream.getUrl() == null || lastStream.getUrl().isEmpty()) {
      return;
    }
    
    float delaySeconds = configuration().autoStart().delay().get();
    
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
    if (stream == null || stream.getUrl() == null || stream.getUrl().isEmpty()) {
      return;
    }
    
    // Prüfe, ob derselbe Stream bereits läuft (verhindert Pause beim Subserver-Wechsel)
    RadioStream currentStream = this.radioManager.getCurrentStream();
    boolean isSameStream = currentStream != null && stream != null && 
                          currentStream.getId() == stream.getId();
    
    // Wenn derselbe Stream bereits läuft, tue nichts (verhindert Pause beim Subserver-Wechsel)
    if (isSameStream && this.radioManager.isPlaying()) {
      return;
    }
    
    // Wenn der Benutzer das Radio manuell startet, setze die Flag zurück
    this.userManuallyStopped = false;
    
    this.radioManager.playStream(stream);
    this.currentSongService.fetchCurrentSong();
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


