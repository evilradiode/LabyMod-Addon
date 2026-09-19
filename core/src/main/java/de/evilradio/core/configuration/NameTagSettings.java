package de.evilradio.core.configuration;

import net.labymod.api.client.component.format.TextDecoration;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.color.ColorPickerWidget.ColorPickerSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownEntryTranslationPrefix;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownSetting;
import net.labymod.api.configuration.loader.Config;
import net.labymod.api.configuration.loader.annotation.SpriteSlot;
import net.labymod.api.configuration.loader.annotation.SpriteTexture;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.configuration.settings.annotation.SettingRequires;
import net.labymod.api.configuration.settings.annotation.SettingSection;
import net.labymod.api.util.Color;

@SpriteTexture("sprite/settings")
public class NameTagSettings extends Config {

  @SettingSection("sharing")

  @SpriteSlot(y = 2, x = 1)
  @SwitchSetting
  private final ConfigProperty<Boolean> shareCurrentStream = new ConfigProperty<>(true);

  @SettingSection("staffNameTag")

  @SpriteSlot(y = 2, x = 2)
  @SwitchSetting
  private final ConfigProperty<Boolean> showStaffNametag = new ConfigProperty<>(true);

  @SettingRequires(value = "showStaffNametag")
  @ColorPickerSetting(chroma = true)
  private final ConfigProperty<Color> staffNameTagColor = new ConfigProperty<>(Color.ofRGB(255, 255, 255));

  @SettingRequires(value = "showStaffNametag")
  @DropdownSetting
  @DropdownEntryTranslationPrefix("evilradio.settings.nameTagSettings.staffNameTagDecoration.type")
  private final ConfigProperty<CustomTextDecoration> staffNameTagDecoration = new ConfigProperty<>(CustomTextDecoration.NONE);

  // Getters

  public ConfigProperty<Boolean> shareCurrentStream() {
    return shareCurrentStream;
  }

  public ConfigProperty<Boolean> showStaffNametag() {
    return showStaffNametag;
  }

  public ConfigProperty<Color> staffNameTagColor() {
    return staffNameTagColor;
  }

  public ConfigProperty<CustomTextDecoration> staffNameTagDecoration() {
    return staffNameTagDecoration;
  }

  public enum CustomTextDecoration {

    NONE(null),
    BOLD(TextDecoration.BOLD),
    STRIKETHROUGH(TextDecoration.STRIKETHROUGH),
    UNDERLINED(TextDecoration.UNDERLINED),
    ITALIC(TextDecoration.ITALIC);

    private final TextDecoration labyDecoration;

    CustomTextDecoration(TextDecoration labyDecoration) {
      this.labyDecoration = labyDecoration;
    }

    public TextDecoration labyDecoration() {
      return labyDecoration;
    }
  }

}
