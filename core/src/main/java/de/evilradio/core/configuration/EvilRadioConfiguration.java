package de.evilradio.core.configuration;

import de.evilradio.core.EvilRadioAddon;
import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget.ButtonSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.KeybindWidget.KeyBindSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownEntryTranslationPrefix;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownSetting;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.annotation.Exclude;
import net.labymod.api.configuration.loader.annotation.IntroducedIn;
import net.labymod.api.configuration.loader.annotation.SpriteSlot;
import net.labymod.api.configuration.loader.annotation.SpriteTexture;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.configuration.settings.annotation.SettingSection;
import net.labymod.api.util.MethodOrder;

@SpriteTexture("sprite/settings")
@ConfigName("settings")
public class EvilRadioConfiguration extends AddonConfig {

  @SpriteSlot()
  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @SpriteSlot(x = 1)
  @KeyBindSetting
  private final ConfigProperty<Key> radioMenuKeybind = new ConfigProperty<>(Key.R);

  @SpriteSlot(x = 2)
  @SliderSetting(min = 0, max = 100, steps = 2f)
  private final ConfigProperty<Float> volume = new ConfigProperty<>(25f);

  @SpriteSlot(x = 7)
  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  private final AudioEqualizerSubSettings audioEqualizer = new AudioEqualizerSubSettings();

  @SettingSection("customization")

  @SpriteSlot(x = 3)
  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @DropdownSetting
  @DropdownEntryTranslationPrefix("evilradio.settings.menuPlayerPosition.type")
  private final ConfigProperty<MenuPlayerPosition> menuPlayerPosition = new ConfigProperty<>(MenuPlayerPosition.BOTTOM_RIGHT);

  @SpriteSlot(x = 4)
  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  private final StationPickerSubSettings stationPicker = new StationPickerSubSettings();

  @Exclude
  private final ConfigProperty<EqualizerStyle> equalizerStyle =
      new ConfigProperty<>(EqualizerStyle.BARS);

  @SettingSection("notifications")

  @SpriteSlot(x = 5)
  @SwitchSetting
  private final ConfigProperty<Boolean> showSongChangeNotification = new ConfigProperty<>(true);

  @SpriteSlot(x = 6)
  @SwitchSetting
  private final ConfigProperty<Boolean> showLiveChatNotification = new ConfigProperty<>(true);

  @SettingSection("autoStartStop")

  @SpriteSlot(y = 1)
  @DropdownSetting
  @DropdownEntryTranslationPrefix("evilradio.settings.autoStartMode.type")
  private final ConfigProperty<AutoStartMode> autoStartMode = new ConfigProperty<>(AutoStartMode.DISABLED);

  @SpriteSlot(y = 1, x = 1)
  @SliderSetting(min = 0, max = 10, steps = 0.5f)
  private final ConfigProperty<Float> autoStartDelay = new ConfigProperty<>(2.0f);

  @SpriteSlot(y = 1, x = 2)
  @SwitchSetting
  private final ConfigProperty<Boolean> autoStopOnFocusLoss = new ConfigProperty<>(false);

  @SettingSection("advanced")

  @SpriteSlot(y = 1, x = 3)
  private final UsageStatisticsSubSettings usageStatistics = new UsageStatisticsSubSettings();

  @Exclude
  private final ConfigProperty<Integer> lastStreamId = new ConfigProperty<>(-1);

  @SpriteSlot(y = 1, x = 4)
  @MethodOrder(after = "usageStatistics")
  @ButtonSetting
  public void reloadStreams() {
    EvilRadioAddon.instance().radioStreamService().loadStreams();
  }

  @IntroducedIn(namespace = "evilradio", value = "1.1.0")
  @SwitchSetting
  private final ConfigProperty<Boolean> debugForceMashupLive = new ConfigProperty<>(false);


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

  public AudioEqualizerSubSettings audioEqualizer() {
    return this.audioEqualizer;
  }

  public ConfigProperty<MenuPlayerPosition> menuPlayerPosition() {
    return menuPlayerPosition;
  }

  public StationPickerSubSettings stationPicker() {
    return this.stationPicker;
  }

  public ConfigProperty<EqualizerStyle> equalizerStyle() {
    return this.equalizerStyle;
  }

  public ConfigProperty<Boolean> showSongChangeNotification() {
    return showSongChangeNotification;
  }

  public ConfigProperty<Boolean> showLiveChatNotification() {
    return this.showLiveChatNotification;
  }

  public ConfigProperty<AutoStartMode> autoStartMode() {
    return autoStartMode;
  }

  public ConfigProperty<Float> autoStartDelay() {
    return autoStartDelay;
  }

  public ConfigProperty<Boolean> autoStopOnFocusLoss() {
    return this.autoStopOnFocusLoss;
  }
  
  public UsageStatisticsSubSettings usageStatistics() {
    return this.usageStatistics;
  }
  
  public ConfigProperty<Integer> lastStreamId() {
    return this.lastStreamId;
  }

  public ConfigProperty<Boolean> debugForceMashupLive() {
    return debugForceMashupLive;
  }

  public enum MenuPlayerPosition {
    DISABLED,
    BOTTOM_RIGHT,
    BOTTOM_LEFT;

    public boolean isLeft() {
      return this == BOTTOM_LEFT;
    }
  }

  public enum AutoStartMode {
    DISABLED, ON_GAME_START, ON_SERVER_JOIN;

    public boolean shouldStartOnGameStart() {
      return this == ON_GAME_START;
    }

    public boolean shouldStartOnServerJoin() {
      return this == ON_SERVER_JOIN;
    }
  }

  public enum EqualizerStyle {
    BARS,
    PEAKS,
    MIRROR,
    SCOPE,
    DOTS,
    OFF;

    public EqualizerStyle next() {
      EqualizerStyle[] values = values();
      return values[(this.ordinal() + 1) % values.length];
    }

    public boolean isEnabled() {
      return this != OFF;
    }

    public String styleId() {
      return "eq-" + this.name().toLowerCase();
    }
  }

}



