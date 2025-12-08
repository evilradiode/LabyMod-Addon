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
      // Nach dem Laden der Streams: Prüfe, ob Auto-Start aktiviert ist
      if (configuration().autoStartLastStream().get()) {
        int lastStreamId = configuration().lastStreamId().get();
        if (lastStreamId >= 0) {
          RadioStream lastStream = this.radioStreamService.findStreamById(lastStreamId);
          if (lastStream != null && lastStream.getUrl() != null && !lastStream.getUrl().isEmpty()) {
            this.radioManager.playStream(lastStream);
            this.currentSongService.fetchCurrentSong();
            this.logger().info("Auto-started last stream: " + lastStream.getDisplayName());
          }
        }
      }
    });

    this.labyAPI().ingameOverlay().registerActivity(new RadioWheelOverlay(this));

    this.labyAPI().hudWidgetRegistry().register(this.currentSongHudWidget = new CurrentSongHudWidget(this));

    this.logger().info("Enabled the Addon");

    configuration().volume().addChangeListener((volume) -> this.radioManager.setVolume(volume));
    
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

}


