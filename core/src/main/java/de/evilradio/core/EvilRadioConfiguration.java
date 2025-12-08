package de.evilradio.core;

import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget.ButtonSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.KeybindWidget.KeyBindSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.configuration.settings.annotation.SettingOrder;
import net.labymod.api.util.MethodOrder;

@ConfigName("settings")
public class EvilRadioConfiguration extends AddonConfig {

  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @KeyBindSetting
  private final ConfigProperty<Key> radioMenuKeybind = new ConfigProperty<>(Key.R);
  
  @SwitchSetting
  private final ConfigProperty<Boolean> ignoreWhitelist = new ConfigProperty<>(false);
  
  @SliderSetting(min = 0, max = 1, steps = 0.01f)
  private final ConfigProperty<Float> volume = new ConfigProperty<>(0.25f);

  @MethodOrder(after = "volume")
  @ButtonSetting
  public void reloadStreams() {
    EvilRadioAddon.instance().radioStreamService().loadStreams();
  }

  @Override
  public ConfigProperty<Boolean> enabled() {
    return this.enabled;
  }

  public ConfigProperty<Key> radioMenuKeybind() {
    return this.radioMenuKeybind;
  }
  
  public ConfigProperty<Boolean> ignoreWhitelist() {
    return this.ignoreWhitelist;
  }
  
  public ConfigProperty<Float> volume() {
    return this.volume;
  }

}


