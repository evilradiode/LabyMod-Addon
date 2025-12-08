package de.evilradio.core;

import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownEntryTranslationPrefix;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.configuration.loader.Config;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.annotation.ShowSettingInParent;
import net.labymod.api.configuration.loader.property.ConfigProperty;

@ConfigName("autoStart")
public class AutoStartSubSettings extends Config {

  @ShowSettingInParent
  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(false);

  @DropdownSetting
  @DropdownEntryTranslationPrefix("evilradio.settings.autoStart.mode.type")
  private final ConfigProperty<AutoStartMode> mode = new ConfigProperty<>(AutoStartMode.ON_SERVER_JOIN);

  @SliderSetting(min = 0, max = 10, steps = 0.5f)
  private final ConfigProperty<Float> delay = new ConfigProperty<>(2.0f);

  public ConfigProperty<Boolean> enabled() {
    return this.enabled;
  }

  public ConfigProperty<AutoStartMode> mode() {
    return this.mode;
  }

  public ConfigProperty<Float> delay() {
    return this.delay;
  }

}

