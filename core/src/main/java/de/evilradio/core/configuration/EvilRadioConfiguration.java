package de.evilradio.core.configuration;

import de.evilradio.core.EvilRadioAddon;
import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget.ButtonSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.KeybindWidget.KeyBindSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.annotation.Exclude;
import net.labymod.api.configuration.loader.property.ConfigProperty;
// import net.labymod.api.models.OperatingSystem; // TODO: Wieder aktivieren, wenn URL-Aufruf aktiviert wird
import net.labymod.api.models.OperatingSystem;
import net.labymod.api.util.MethodOrder;
import java.util.HashMap;
import java.util.Map;

@ConfigName("settings")
public class EvilRadioConfiguration extends AddonConfig {

  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @KeyBindSetting
  private final ConfigProperty<Key> radioMenuKeybind = new ConfigProperty<>(Key.R);

  @SwitchSetting
  private final ConfigProperty<Boolean> showSongChangeNotification = new ConfigProperty<>(true);

  @SwitchSetting
  private final ConfigProperty<Boolean> useFourLines = new ConfigProperty<>(false);

  @SliderSetting(min = 0, max = 100, steps = 2f)
  private final ConfigProperty<Float> volume = new ConfigProperty<>(25f);

  private final AutoStartSubSettings autoStart = new AutoStartSubSettings();

  private final UsageStatisticsSubSettings usageStatistics = new UsageStatisticsSubSettings();

  @Exclude
  private final ConfigProperty<Integer> lastStreamId = new ConfigProperty<>(-1);

  private Map<Integer, Integer> streamUsageCount = new HashMap<>();

  @MethodOrder(after = "usageStatistics")
  @ButtonSetting
  public void reloadStreams() {
    EvilRadioAddon.instance().radioStreamService().loadStreams();
  }

  @MethodOrder(after = "reloadStreams")
  @ButtonSetting
  public void openFlintMcPage() {
    OperatingSystem.getPlatform().openUrl("https://flintmc.net/modification/277.evilradio");
  }

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

  public ConfigProperty<Boolean> showSongChangeNotification() {
    return showSongChangeNotification;
  }

  public ConfigProperty<Boolean> useFourLines() {
    return this.useFourLines;
  }

  public ConfigProperty<Float> volume() {
    return this.volume;
  }
  
  public AutoStartSubSettings autoStart() {
    return this.autoStart;
  }
  
  public UsageStatisticsSubSettings usageStatistics() {
    return this.usageStatistics;
  }
  
  public ConfigProperty<Boolean> usageBasedSorting() {
    return usageStatistics.enabled();
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



