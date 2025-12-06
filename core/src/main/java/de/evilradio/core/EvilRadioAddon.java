package de.evilradio.core;

import de.evilradio.core.radio.RadioManager;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.models.addon.annotation.AddonMain;

@AddonMain
public class EvilRadioAddon extends LabyAddon<EvilRadioConfiguration> {

  private RadioManager radioManager;
  private Key radioMenuKeybind;

  @Override
  protected void enable() {
    this.registerSettingCategory();

    // Radio-Manager initialisieren
    this.radioManager = new RadioManager();
    
    // Initialisiere Standard-Streams in der Konfiguration, falls leer
    EvilRadioConfiguration config = this.configuration();
    if (config != null) {
      config.initializeDefaultStreams();
      
      // Lade Streams aus der Konfiguration in den RadioManager
      boolean ignoreWhitelist = config.ignoreWhitelist().get();
      this.radioManager.initializeStreams(config.radioStreams(), ignoreWhitelist);
      
      this.logger().info("Radio-Streams aus Konfiguration geladen: " + config.radioStreams().size() + 
          (ignoreWhitelist ? " (Whitelist deaktiviert)" : " (Whitelist aktiviert)"));
    }

    // Keybind Listener registrieren (als Game-Keybind, reagiert nur im Spiel)
    this.logger().info("Radio-Wheel Keybind Listener registriert als Game-Keybind");

    this.logger().info("Enabled the Addon");
  }

  @Override
  protected Class<EvilRadioConfiguration> configurationClass() {
    return EvilRadioConfiguration.class;
  }

  public RadioManager getRadioManager() {
    return radioManager;
  }

  public Key getRadioMenuKeybind() {
    return radioMenuKeybind;
  }

  public void setRadioMenuKeybind(Key key) {
    this.radioMenuKeybind = key;
    this.logger().info("Radio-Menü Keybind geändert zu: " + (key != null ? key.getName() : "null"));
  }


}


