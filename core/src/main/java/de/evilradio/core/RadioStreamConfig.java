package de.evilradio.core;

import net.labymod.api.configuration.loader.Config;
import net.labymod.api.configuration.loader.property.ConfigProperty;

/**
 * Konfiguration für einen einzelnen Radio-Stream.
 */
public class RadioStreamConfig extends Config {
  
  private final ConfigProperty<String> url = new ConfigProperty<>("");
  private final ConfigProperty<String> name = new ConfigProperty<>("");
  private final ConfigProperty<String> displayName = new ConfigProperty<>("");
  private final ConfigProperty<String> genre = new ConfigProperty<>("Radio");
  private final ConfigProperty<String> country = new ConfigProperty<>("DE");
  private final ConfigProperty<Integer> bitrate = new ConfigProperty<>(128);
  private final ConfigProperty<String> iconPath = new ConfigProperty<>("");
  private final ConfigProperty<String> category = new ConfigProperty<>("");
  
  public ConfigProperty<String> url() {
    return url;
  }
  
  public ConfigProperty<String> name() {
    return name;
  }
  
  public ConfigProperty<String> displayName() {
    return displayName;
  }
  
  public ConfigProperty<String> genre() {
    return genre;
  }
  
  public ConfigProperty<String> country() {
    return country;
  }
  
  public ConfigProperty<Integer> bitrate() {
    return bitrate;
  }
  
  public ConfigProperty<String> iconPath() {
    return iconPath;
  }
  
  public ConfigProperty<String> category() {
    return category;
  }
}

