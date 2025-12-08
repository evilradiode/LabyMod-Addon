package de.evilradio.core;

import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget.ButtonSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.KeybindWidget.KeyBindSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.util.MethodOrder;
import java.util.HashMap;
import java.util.Map;

@ConfigName("settings")
public class EvilRadioConfiguration extends AddonConfig {

  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @KeyBindSetting
  private final ConfigProperty<Key> radioMenuKeybind = new ConfigProperty<>(Key.R);
  
  @SliderSetting(min = 0, max = 1, steps = 0.01f)
  private final ConfigProperty<Float> volume = new ConfigProperty<>(0.25f);
  
  @SwitchSetting
  private final ConfigProperty<Boolean> usageBasedSorting = new ConfigProperty<>(true);
  
  @SwitchSetting
  private final ConfigProperty<Boolean> autoStartLastStream = new ConfigProperty<>(false);
  
  // ID des letzten gestarteten Streams
  private final ConfigProperty<Integer> lastStreamId = new ConfigProperty<>(-1);
  
  // Nutzungsstatistiken: Map von Stream-ID zu Nutzungsanzahl
  private Map<Integer, Integer> streamUsageCount = new HashMap<>();

  @MethodOrder(after = "volume")
  @ButtonSetting
  public void reloadStreams() {
    EvilRadioAddon.instance().radioStreamService().loadStreams();
  }

  @MethodOrder(after = "reloadStreams")
  @ButtonSetting
  public void resetStreamUsageCount() {
    if (streamUsageCount != null) {
      streamUsageCount.clear();
    }
  }

  @Override
  public ConfigProperty<Boolean> enabled() {
    return this.enabled;
  }

  public ConfigProperty<Key> radioMenuKeybind() {
    return this.radioMenuKeybind;
  }
  
  public ConfigProperty<Float> volume() {
    return this.volume;
  }
  
  public ConfigProperty<Boolean> usageBasedSorting() {
    return this.usageBasedSorting;
  }
  
  public ConfigProperty<Boolean> autoStartLastStream() {
    return this.autoStartLastStream;
  }
  
  public ConfigProperty<Integer> lastStreamId() {
    return this.lastStreamId;
  }
  
  public Map<Integer, Integer> streamUsageCount() {
    if (streamUsageCount == null) {
      streamUsageCount = new HashMap<>();
    }
    return streamUsageCount;
  }
  
  public void incrementStreamUsage(int streamId) {
    if (streamUsageCount == null) {
      streamUsageCount = new HashMap<>();
    }
    streamUsageCount.put(streamId, streamUsageCount.getOrDefault(streamId, 0) + 1);
  }
  
  public int getStreamUsageCount(int streamId) {
    if (streamUsageCount == null) {
      return 0;
    }
    return streamUsageCount.getOrDefault(streamId, 0);
  }

}


