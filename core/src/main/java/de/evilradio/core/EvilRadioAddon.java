package de.evilradio.core;

import de.evilradio.core.i18n.TranslationService;
import de.evilradio.core.radio.RadioManager;
import de.evilradio.core.radio.RadioStreamService;
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

  @Override
  protected void enable() {
    this.registerSettingCategory();
    instance = this;
    
    // Initialisiere Translation Service
    TranslationService.initialize(this);

    this.radioManager = new RadioManager(this);

    this.radioStreamService = new RadioStreamService(this);
    this.radioStreamService.loadStreams(() -> {
      // Nach dem Laden der Streams: Prüfe, ob Auto-Start aktiviert ist
      if (configuration().autoStartLastStream().get()) {
        int lastStreamId = configuration().lastStreamId().get();
        if (lastStreamId >= 0) {
          de.evilradio.core.radio.RadioStream lastStream = this.radioStreamService.findStreamById(lastStreamId);
          if (lastStream != null && lastStream.getUrl() != null && !lastStream.getUrl().isEmpty()) {
            this.radioManager.playStream(lastStream);
            this.logger().info("Auto-started last stream: " + lastStream.getDisplayName());
          }
        }
      }
    });

    this.labyAPI().ingameOverlay().registerActivity(new RadioWheelOverlay(this));

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

}


