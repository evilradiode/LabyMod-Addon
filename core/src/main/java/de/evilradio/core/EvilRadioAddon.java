package de.evilradio.core;

import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import de.evilradio.core.radio.RadioManager;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.radio.RadioStreamService;
import de.evilradio.core.song.CurrentSongService;
import de.evilradio.core.ui.RadioWheelOverlay;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.models.addon.annotation.AddonMain;
import net.labymod.api.notification.Notification;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.network.server.ServerJoinEvent;
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
      AutoStartMode mode = configuration().getAutoStartMode();
      if (mode != null && mode.shouldStartOnGameStart()) {
        this.startLastStreamWithDelay("game start");
      }
    });
    
    // Event-Bus registrieren für Event-Handler
    this.labyAPI().eventBus().registerListener(this);

    this.labyAPI().ingameOverlay().registerActivity(new RadioWheelOverlay(this));

    this.labyAPI().hudWidgetRegistry().register(this.currentSongHudWidget = new CurrentSongHudWidget(this));

    this.logger().info("Enabled the Addon");

    configuration().volume().addChangeListener((volume) -> this.radioManager.setVolume(volume / 100.0f));
    
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
            .icon(Icon.texture(ResourceLocation.create("evilradio", "textures/logo.png")))
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
    
    AutoStartMode mode = configuration().getAutoStartMode();
    if (mode != null && mode.shouldStartOnServerJoin()) {
      this.startLastStreamWithDelay("server join");
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
    
    AutoStartMode mode = configuration().getAutoStartMode();
    if (mode != null && mode.shouldStartOnServerJoin()) {
      // Stoppe den Stream, wenn er läuft
      if (this.radioManager != null && this.radioManager.isPlaying()) {
        this.radioManager.stopStream();
      }
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
    
    AutoStartMode mode = configuration().getAutoStartMode();
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
    
    this.radioManager.playStream(stream);
    this.currentSongService.fetchCurrentSong();
  }
  
}


