package de.evilradio.core;

import java.util.ArrayList;
import java.util.List;
import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.widget.widgets.input.KeybindWidget.KeyBindSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.property.ConfigProperty;

@ConfigName("settings")
public class EvilRadioConfiguration extends AddonConfig {

  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @KeyBindSetting
  private final ConfigProperty<Key> radioMenuKeybind = new ConfigProperty<>(Key.R);
  
  @SwitchSetting
  private final ConfigProperty<Boolean> ignoreWhitelist = new ConfigProperty<>(false);
  
  @SliderSetting(min = 0, max = 100, steps = 1)
  private final ConfigProperty<Integer> volume = new ConfigProperty<>(50);
  
  // Liste der Radio-Stream-Konfigurationen
  private final List<RadioStreamConfig> radioStreams = new ArrayList<>();

  @Override
  public ConfigProperty<Boolean> enabled() {
    return this.enabled;
  }

  public ConfigProperty<Key> radioMenuKeybind() {
    return this.radioMenuKeybind;
  }
  
  public List<RadioStreamConfig> radioStreams() {
    return this.radioStreams;
  }
  
  public ConfigProperty<Boolean> ignoreWhitelist() {
    return this.ignoreWhitelist;
  }
  
  public ConfigProperty<Integer> volume() {
    return this.volume;
  }
  
  /**
   * Initialisiert die Standard-Radio-Streams, falls die Liste leer ist.
   */
  public void initializeDefaultStreams() {
    if (!radioStreams.isEmpty()) {
      return; // Bereits initialisiert
    }
    
    // POP & RAP
    RadioStreamConfig popRap = new RadioStreamConfig();
    popRap.url().set("https://stream.laut.fm/evil-radio-popundrap");
    popRap.name().set("POP");
    popRap.displayName().set("Evil-Radio Pop und Rap");
    popRap.genre().set("Radio");
    popRap.country().set("DE");
    popRap.bitrate().set(128);
    popRap.iconPath().set("evilradio:textures/stations/poprap.png");
    popRap.category().set("POP & RAP");
    radioStreams.add(popRap);
    
    // MASHUP (Platzhalter - noch kein Stream verfügbar)
    RadioStreamConfig mashup = new RadioStreamConfig();
    mashup.url().set("https://stream.laut.fm/evil-radio");
    mashup.name().set("Mashup");
    mashup.displayName().set("Evil-Radio Mashup");
    mashup.genre().set("Radio");
    mashup.country().set("DE");
    mashup.bitrate().set(128);
    mashup.iconPath().set("evilradio:textures/stations/mashup.png");
    mashup.category().set("MASHUP");
    radioStreams.add(mashup);
    
    // SCHLAGER
    RadioStreamConfig schlager = new RadioStreamConfig();
    schlager.url().set("https://stream.laut.fm/er-schlager");
    schlager.name().set("Schlager");
    schlager.displayName().set("Evil-Radio Schlager");
    schlager.genre().set("Radio");
    schlager.country().set("DE");
    schlager.bitrate().set(128);
    schlager.iconPath().set("evilradio:textures/stations/schlager.png");
    schlager.category().set("SCHLAGER");
    radioStreams.add(schlager);
    
    // XMAS
    RadioStreamConfig xmas = new RadioStreamConfig();
    xmas.url().set("https://stream.laut.fm/evil-radiox-mas");
    xmas.name().set("Xmas");
    xmas.displayName().set("Evil-Radio X-Mas");
    xmas.genre().set("Radio");
    xmas.country().set("DE");
    xmas.bitrate().set(128);
    xmas.iconPath().set("evilradio:textures/stations/xmas.png");
    xmas.category().set("XMAS");
    radioStreams.add(xmas);
    
    // OLDIE
    RadioStreamConfig oldie = new RadioStreamConfig();
    oldie.url().set("https://stream.laut.fm/er-oldie");
    oldie.name().set("Oldie");
    oldie.displayName().set("Evil-Radio Oldie");
    oldie.genre().set("Radio");
    oldie.country().set("DE");
    oldie.bitrate().set(128);
    oldie.iconPath().set("evilradio:textures/stations/oldie.png");
    oldie.category().set("OLDIE");
    radioStreams.add(oldie);
    
    // SOMMER
    RadioStreamConfig sommer = new RadioStreamConfig();
    sommer.url().set("https://stream.laut.fm/summer-time");
    sommer.name().set("Summer");
    sommer.displayName().set("Evil-Radio Sommer-Time");
    sommer.genre().set("Radio");
    sommer.country().set("DE");
    sommer.bitrate().set(128);
    sommer.iconPath().set("evilradio:textures/stations/sommer.png");
    sommer.category().set("SOMMER");
    radioStreams.add(sommer);
    
    // ANIME
    RadioStreamConfig anime = new RadioStreamConfig();
    anime.url().set("https://stream.laut.fm/evil-animefm");
    anime.name().set("Anime");
    anime.displayName().set("Evil Anime FM");
    anime.genre().set("Radio");
    anime.country().set("DE");
    anime.bitrate().set(128);
    anime.iconPath().set("evilradio:textures/stations/anime.png");
    anime.category().set("ANIME");
    radioStreams.add(anime);
    
    // Coming Soon (Platzhalter)
    RadioStreamConfig comingSoon = new RadioStreamConfig();
    comingSoon.url().set("");
    comingSoon.name().set("Coming Soon");
    comingSoon.displayName().set("Coming Soon / Aber Wann?");
    comingSoon.genre().set("Radio");
    comingSoon.country().set("DE");
    comingSoon.bitrate().set(128);
    comingSoon.iconPath().set("evilradio:textures/stations/comingsoon.png");
    comingSoon.category().set("Coming Soon / Aber Wann?");
    radioStreams.add(comingSoon);
  }
}


