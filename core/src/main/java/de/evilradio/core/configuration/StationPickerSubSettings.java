package de.evilradio.core.configuration;

import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.color.ColorPickerWidget.ColorPickerSetting;
import net.labymod.api.configuration.settings.annotation.ColorRowBreak;
import net.labymod.api.configuration.loader.Config;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.annotation.IntroducedIn;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.util.Color;

@ConfigName("stationPicker")
public class StationPickerSubSettings extends Config {

  /** Dunkel, gut lesbar – ähnlich dem alten Panel vor dem starken Glass-Look. */
  public static final Color DEFAULT_BACKGROUND_COLOR = Color.ofRGB(8, 8, 12);
  public static final Color DEFAULT_BORDER_COLOR = Color.ofRGB(160, 160, 175);
  public static final Color DEFAULT_ROW_BACKGROUND_COLOR = Color.ofRGB(24, 24, 32);
  public static final Color DEFAULT_SONG_COLOR = Color.ofRGB(220, 220, 225);
  public static final Color DEFAULT_ARTIST_COLOR = Color.ofRGB(190, 195, 205);
  public static final Color DEFAULT_TIME_COLOR = Color.ofRGB(160, 200, 220);

  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @ColorPickerSetting(alpha = true)
  private final ConfigProperty<Color> backgroundColor =
      ConfigProperty.create(DEFAULT_BACKGROUND_COLOR);

  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @ColorPickerSetting(alpha = true)
  private final ConfigProperty<Color> borderColor =
      ConfigProperty.create(DEFAULT_BORDER_COLOR);

  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @ColorPickerSetting(alpha = true)
  private final ConfigProperty<Color> rowBackgroundColor =
      ConfigProperty.create(DEFAULT_ROW_BACKGROUND_COLOR);

  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @SliderSetting(min = 0, max = 40)
  private final ConfigProperty<Integer> backgroundBlur = ConfigProperty.create(18);

  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @SwitchSetting
  private final ConfigProperty<Boolean> showEqualizer = new ConfigProperty<>(true);

  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @ColorRowBreak
  @ColorPickerSetting
  private final ConfigProperty<Color> songColor = ConfigProperty.create(DEFAULT_SONG_COLOR);

  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @ColorPickerSetting
  private final ConfigProperty<Color> artistColor = ConfigProperty.create(DEFAULT_ARTIST_COLOR);

  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @ColorPickerSetting
  private final ConfigProperty<Color> timeColor = ConfigProperty.create(DEFAULT_TIME_COLOR);

  public ConfigProperty<Color> backgroundColor() {
    return this.backgroundColor;
  }

  public ConfigProperty<Color> borderColor() {
    return this.borderColor;
  }

  public ConfigProperty<Color> rowBackgroundColor() {
    return this.rowBackgroundColor;
  }

  public ConfigProperty<Integer> backgroundBlur() {
    return this.backgroundBlur;
  }

  public ConfigProperty<Boolean> showEqualizer() {
    return this.showEqualizer;
  }

  public ConfigProperty<Color> songColor() {
    return this.songColor;
  }

  public ConfigProperty<Color> artistColor() {
    return this.artistColor;
  }

  public ConfigProperty<Color> timeColor() {
    return this.timeColor;
  }
}
