package de.evilradio.core.configuration;

import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget.ButtonSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.configuration.loader.Config;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.annotation.ShowSettingInParent;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.util.MethodOrder;
import java.util.HashMap;

@ConfigName("usageStatistics")
public class UsageStatisticsSubSettings extends Config {

  // Switch für nutzerbasierte Sortierung (wird im Hauptmenü angezeigt)
  @ShowSettingInParent
  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @ButtonSetting
  @MethodOrder(after = "enabled")
  public void resetStatistics() {
    resetStreamUsageCount();
  }

  public ConfigProperty<Boolean> enabled() {
    return this.enabled;
  }

  public ConfigProperty<HashMap<Integer, Integer>> streamUsageCount = new ConfigProperty<>(new HashMap<>());

  public void resetStreamUsageCount() {
    if (streamUsageCount != null) {
      streamUsageCount.get().clear();
    }
  }

  public void incrementStreamUsage(int streamId) {
    streamUsageCount.get().put(streamId, streamUsageCount.get().getOrDefault(streamId, 0) + 1);
  }

  public int getStreamUsageCount(int streamId) {
    return streamUsageCount.get().getOrDefault(streamId, 0);
  }

}

