package de.evilradio.core.configuration;

import de.evilradio.core.EvilRadioAddon;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget.ButtonSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.configuration.loader.Config;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.annotation.ShowSettingInParent;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.util.MethodOrder;

@ConfigName("usageStatistics")
public class UsageStatisticsSubSettings extends Config {

  // Switch für nutzerbasierte Sortierung (wird im Hauptmenü angezeigt)
  @ShowSettingInParent
  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @ButtonSetting
  @MethodOrder(after = "enabled")
  public void resetStatistics() {
    EvilRadioAddon.instance().configuration().resetStreamUsageCount();
  }

  public ConfigProperty<Boolean> enabled() {
    return this.enabled;
  }

}

