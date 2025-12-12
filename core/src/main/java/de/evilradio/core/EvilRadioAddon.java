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
  
  // Flag, um zu verfolgen, ob ein WorldEnterEvent nach einem WorldLeaveEvent kommt (Subserver-Wechsel)
  private boolean worldEnterExpected = false;
  private Task pendingWorldLeaveStopTask = null;
  
  // Fokus-Tracking für Auto-Pause
  private boolean lastWindowFocused = true;
  private boolean wasPausedByFocusLoss = false;
  private Task focusCheckTask = null;

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
      if (!configuration().autoStart().enabled().get()) {
        return;
      }
      AutoStartSubSettings.AutoStartMode mode = configuration().autoStart().mode().get();
      if (mode != null && mode.shouldStartOnGameStart()) {
        this.startLastStreamWithDelay("game start");
      }
    });
    
    // Event-Bus registrieren für Event-Handler
    this.labyAPI().eventBus().registerListener(this);

    this.labyAPI().ingameOverlay().registerActivity(new RadioWheelOverlay(this));

    this.labyAPI().hudWidgetRegistry().register(this.currentSongHudWidget = new CurrentSongHudWidget(this));

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
      if (!enabled) {
        if (this.radioManager != null && this.radioManager.isPlaying()) {
          this.radioManager.stopStream();
          this.logger().info("Stream gestoppt, da Addon deaktiviert wurde");
        }
        // Stoppe auch den Fokus-Check-Task
        stopFocusCheckTask();
        wasPausedByFocusLoss = false;
      } else {
        // Wenn Addon wieder aktiviert wird, starte Fokus-Check-Task neu (falls aktiviert)
        if (configuration().pauseOnFocusLoss().get()) {
          startFocusCheckTask();
        }
      }
    });

    configuration().useFourLines().addChangeListener((useFourLines) -> {
      if(this.currentSongHudWidget != null) {
        this.currentSongHudWidget.requestUpdate(CurrentSongHudWidget.FOUR_LINES_REASON);
      }
    });

    // Starte Fokus-Check-Task für Auto-Pause bei Fokus-Verlust
    startFocusCheckTask();
    
    // Listener für Änderungen der pauseOnFocusLoss-Einstellung
    configuration().pauseOnFocusLoss().addChangeListener((enabled) -> {
      if (enabled) {
        startFocusCheckTask();
      } else {
        stopFocusCheckTask();
        // Wenn durch Fokus-Verlust pausiert wurde, setze Flag zurück
        wasPausedByFocusLoss = false;
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
    // Wenn ein WorldLeaveEvent vorher kam, handelt es sich wahrscheinlich um einen Subserver-Wechsel
    // In diesem Fall sollte der Stream nicht gestoppt werden
    if (this.worldEnterExpected) {
      this.worldEnterExpected = false;
      // Bricht den geplanten Stop ab, falls noch nicht ausgeführt
      if (this.pendingWorldLeaveStopTask != null) {
        this.pendingWorldLeaveStopTask.cancel();
        this.pendingWorldLeaveStopTask = null;
      }
      // Der Stream läuft weiter, wir müssen ihn nicht neu starten
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
   * Verwendet eine Verzögerung, um Subserver-Wechsel zu erkennen (dabei wird kein WorldLeave gefolgt von WorldEnter ausgelöst)
   */
  @Subscribe
  public void onWorldLeave(WorldLeaveEvent event) {
    // Prüfe, ob Auto-Start aktiviert ist und auf "Beim Welt betreten" steht
    if (!configuration().autoStart().enabled().get()) {
      return;
    }
    
    AutoStartSubSettings.AutoStartMode mode = configuration().autoStart().mode().get();
    if (mode != null && mode.shouldStartOnServerJoin()) {
      // Setze Flag, dass ein WorldEnterEvent erwartet wird (Subserver-Wechsel)
      this.worldEnterExpected = true;
      
      // Bricht einen vorherigen geplanten Stop ab, falls vorhanden
      if (this.pendingWorldLeaveStopTask != null) {
        this.pendingWorldLeaveStopTask.cancel();
      }
      
      // Plane den Stop mit einer kurzen Verzögerung (500ms)
      // Wenn innerhalb dieser Zeit ein WorldEnterEvent kommt, wird der Stop abgebrochen
      this.pendingWorldLeaveStopTask = Task.builder(() -> {
        // Nur stoppen, wenn immer noch erwartet wird (kein WorldEnterEvent kam)
        if (this.worldEnterExpected && this.radioManager != null && this.radioManager.isPlaying()) {
          this.radioManager.stopStream();
        }
        this.worldEnterExpected = false;
        this.pendingWorldLeaveStopTask = null;
      }).delay(500, TimeUnit.MILLISECONDS).build();
      this.pendingWorldLeaveStopTask.execute();
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
    
    // Verzögerung nur beim Spielstart anwenden, nicht beim Welt betreten oder Server-Join
    boolean shouldApplyDelay = "game start".equals(context);
    float delaySeconds = shouldApplyDelay ? configuration().autoStart().delay().get() : 0;
    
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
    
    this.radioManager.playStream(stream);
    this.currentSongService.fetchCurrentSong();
  }
  
  /**
   * Startet den Task zum regelmäßigen Prüfen des Fenster-Fokus
   */
  private void startFocusCheckTask() {
    // Stoppe vorherigen Task, falls vorhanden
    stopFocusCheckTask();
    
    // Nur starten, wenn die Einstellung aktiviert ist
    if (!configuration().pauseOnFocusLoss().get()) {
      return;
    }
    
    // Initialisiere den letzten Fokus-Status
    try {
      lastWindowFocused = this.labyAPI().minecraft().minecraftWindow().isFocused();
    } catch (Exception e) {
      // Fallback: annehmen, dass Fenster fokussiert ist
      lastWindowFocused = true;
    }
    
    // Erstelle Task, der alle 100ms den Fokus-Status prüft
    this.focusCheckTask = Task.builder(() -> {
      if (!configuration().pauseOnFocusLoss().get()) {
        stopFocusCheckTask();
        return;
      }
      
      boolean currentFocused;
      try {
        currentFocused = this.labyAPI().minecraft().minecraftWindow().isFocused();
      } catch (Exception e) {
        // Bei Fehler: annehmen, dass Fenster fokussiert ist
        currentFocused = true;
      }
      
      // Prüfe, ob sich der Fokus-Status geändert hat
      if (currentFocused != lastWindowFocused) {
        lastWindowFocused = currentFocused;
        
        if (this.radioManager != null) {
          if (!currentFocused) {
            // Fenster hat Fokus verloren
            if (this.radioManager.isPlaying() && !this.radioManager.isPaused()) {
              this.radioManager.pauseStream();
              wasPausedByFocusLoss = true;
              this.logger().debug("Stream pausiert wegen Fokus-Verlust");
            }
          } else {
            // Fenster hat Fokus zurückerhalten
            if (wasPausedByFocusLoss && this.radioManager.isPaused()) {
              this.radioManager.resumeStream();
              wasPausedByFocusLoss = false;
              this.logger().debug("Stream fortgesetzt nach Fokus-Rückkehr");
            }
          }
        }
      }
    }).repeat(100, TimeUnit.MILLISECONDS).build();
    
    this.focusCheckTask.execute();
  }
  
  /**
   * Stoppt den Fokus-Check-Task
   */
  private void stopFocusCheckTask() {
    if (this.focusCheckTask != null) {
      this.focusCheckTask.cancel();
      this.focusCheckTask = null;
    }
  }
  
}


