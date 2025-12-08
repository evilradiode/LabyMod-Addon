package de.evilradio.core;

import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget.ButtonSetting;
import net.labymod.api.configuration.loader.Config;
import net.labymod.api.configuration.loader.annotation.ConfigName;

@ConfigName("usageStatistics")
public class UsageStatisticsSubSettings extends Config {

  @ButtonSetting
  public void resetStatistics() {
    EvilRadioAddon.instance().configuration().resetStreamUsageCount();
  }

}

