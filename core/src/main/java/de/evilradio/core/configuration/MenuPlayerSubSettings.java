package de.evilradio.core.configuration;

import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownEntryTranslationPrefix;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.configuration.loader.Config;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.annotation.IntroducedIn;
import net.labymod.api.configuration.loader.annotation.ShowSettingInParent;
import net.labymod.api.configuration.loader.property.ConfigProperty;

@ConfigName("showMainMenuPlayer")
public class MenuPlayerSubSettings extends Config {

  @ShowSettingInParent
  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @DropdownSetting
  @DropdownEntryTranslationPrefix("evilradio.settings.showMainMenuPlayer.position.type")
  private final ConfigProperty<MenuPlayerPosition> position =
      new ConfigProperty<>(MenuPlayerPosition.BOTTOM_RIGHT);

  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @SwitchSetting
  private final ConfigProperty<Boolean> debugForceMashupLive = new ConfigProperty<>(false);

  public ConfigProperty<Boolean> enabled() {
    return this.enabled;
  }

  public ConfigProperty<MenuPlayerPosition> position() {
    return this.position;
  }

  public ConfigProperty<Boolean> debugForceMashupLive() {
    return this.debugForceMashupLive;
  }

  public enum MenuPlayerPosition {
    BOTTOM_RIGHT,
    BOTTOM_LEFT;

    public boolean isLeft() {
      return this == BOTTOM_LEFT;
    }
  }
}
