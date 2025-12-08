package de.evilradio.core;

import de.evilradio.core.radio.RadioManager;
import de.evilradio.core.radio.RadioStreamService;
import de.evilradio.core.ui.RadioWheelOverlay;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.models.addon.annotation.AddonMain;

@AddonMain
public class EvilRadioAddon extends LabyAddon<EvilRadioConfiguration> {

  private static EvilRadioAddon instance;

  private RadioManager radioManager;

  private RadioStreamService radioStreamService;

  @Override
  protected void enable() {
    this.registerSettingCategory();
    instance = this;

    this.radioManager = new RadioManager();

    this.radioStreamService = new RadioStreamService();
    this.radioStreamService.loadStreams();

    this.labyAPI().ingameOverlay().registerActivity(new RadioWheelOverlay(this));

    this.logger().info("Enabled the Addon");

    configuration().volume().addChangeListener((volume) -> this.radioManager.setVolume(volume));
  }

  @Override
  protected Class<EvilRadioConfiguration> configurationClass() {
    return EvilRadioConfiguration.class;
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


