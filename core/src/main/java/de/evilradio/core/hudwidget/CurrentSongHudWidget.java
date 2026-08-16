package de.evilradio.core.hudwidget;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.EvilTextures;
import de.evilradio.core.hudwidget.CurrentSongHudWidget.CurrentSongHudWidgetConfig;
import de.evilradio.core.hudwidget.widget.CurrentSongWidget;
import de.evilradio.core.hudwidget.widget.ModernCurrentSongWidget;
import net.labymod.api.client.component.format.TextColor;
import net.labymod.api.client.gui.hud.hudwidget.HudWidgetConfig;
import net.labymod.api.client.gui.hud.hudwidget.widget.WidgetHudWidget;
import net.labymod.api.client.gui.screen.widget.widgets.hud.HudWidgetWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.color.ColorPickerWidget.ColorPickerSetting;
import net.labymod.api.configuration.loader.annotation.IntroducedIn;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.configuration.settings.annotation.ColorRowBreak;
import net.labymod.api.configuration.settings.annotation.SettingRequires;
import net.labymod.api.configuration.settings.annotation.SettingSection;
import net.labymod.api.util.Color;
import net.labymod.api.util.ThreadSafe;

public class CurrentSongHudWidget extends WidgetHudWidget<CurrentSongHudWidgetConfig> {

  public static final String COVER_VISIBILITY_REASON = "cover_visibility";
  public static final String SONG_CHANGE_REASON = "song_change";
  public static final String COLOR_REASON = "color_style";
  public static final String TOGGLE_PREVIOUS_SONG_REASON = "toggle_previous_song";

  /** Minecraft-Grau (#AAAAAA) – bisher NamedTextColor.GRAY */
  public static final Color DEFAULT_STATION_COLOR = Color.ofRGB(170, 170, 170);
  /** Minecraft-Weiß – bisher NamedTextColor.WHITE */
  public static final Color DEFAULT_SONG_COLOR = Color.WHITE;
  /** Minecraft-Grau (#AAAAAA) – bisher NamedTextColor.GRAY */
  public static final Color DEFAULT_ARTIST_COLOR = Color.ofRGB(170, 170, 170);
  public static final Color DEFAULT_BACKGROUND_COLOR = Color.ofRGB(0, 0, 0);
  public static final Color DEFAULT_BORDER_COLOR = Color.ofRGB(85, 85, 85);
  public static final Color DEFAULT_PROGRESS_BAR_COLOR = Color.ofRGB(255, 85, 85);

  private final EvilRadioAddon addon;
  private HudWidgetWidget hudWidgetWidget = null;

  public CurrentSongHudWidget(EvilRadioAddon addon) {
    super("evilradio_full_widget", CurrentSongHudWidgetConfig.class);
    this.addon = addon;
    this.bindCategory(EvilRadioAddon.HUD_WIDGET_CATEGORY);
    this.setIcon(EvilTextures.LOGO);
  }

  @Override
  public void load(CurrentSongHudWidgetConfig config) {
    super.load(config);
    config.showCover.addChangeListener(
        (property, oldValue, newValue) -> ThreadSafe.executeOnRenderThread(
            () -> this.requestUpdate(COVER_VISIBILITY_REASON))
    );
    config.backgroundBlur.addChangeListener(
        (property, oldValue, newValue) -> ThreadSafe.executeOnRenderThread(
            () -> this.requestUpdate(COLOR_REASON))
    );
    config.backgroundColor.addChangeListener(
        (property, oldValue, newValue) -> ThreadSafe.executeOnRenderThread(
            () -> this.requestUpdate(COLOR_REASON))
    );
    config.borderColor.addChangeListener(
        (property, oldValue, newValue) -> ThreadSafe.executeOnRenderThread(
            () -> this.requestUpdate(COLOR_REASON))
    );
    config.progressBarColor.addChangeListener(
        (property, oldValue, newValue) -> ThreadSafe.executeOnRenderThread(
            () -> this.requestUpdate(COLOR_REASON))
    );
    config.stationColor.addChangeListener(
        (property, oldValue, newValue) -> ThreadSafe.executeOnRenderThread(
            () -> this.requestUpdate(COLOR_REASON))
    );
    config.songColor.addChangeListener(
        (property, oldValue, newValue) -> ThreadSafe.executeOnRenderThread(
            () -> this.requestUpdate(COLOR_REASON))
    );
    config.artistColor.addChangeListener(
        (property, oldValue, newValue) -> ThreadSafe.executeOnRenderThread(
            () -> this.requestUpdate(COLOR_REASON))
    );
    config.useModernWidget.addChangeListener((property, oldValue, newValue) -> {
      if (this.hudWidgetWidget != null) {
        this.hudWidgetWidget.reInitialize();
      }
    });
    config.showLastSong.addChangeListener(
        (property, oldValue, newValue) -> ThreadSafe.executeOnRenderThread(
            () -> this.requestUpdate(TOGGLE_PREVIOUS_SONG_REASON))
    );
  }

  @Override
  public void initialize(HudWidgetWidget widget) {
    super.initialize(widget);
    this.hudWidgetWidget = widget;
    if (this.config.useModernWidget.get()) {
      widget.addChild(new ModernCurrentSongWidget(this.addon, this));
      widget.addId("current-song-modern");
    } else {
      widget.addChild(new CurrentSongWidget(this.addon, this));
      widget.addId("current-song");
    }
  }

  @Override
  public boolean isVisibleInGame() {
    if (!this.addon.configuration().enabled().get()) {
      return false;
    }
    return this.addon.radioManager().isPlaying();
  }

  public static TextColor toTextColor(Color color) {
    if (color == null) {
      return TextColor.color(255, 255, 255);
    }
    return TextColor.color(color.getRed(), color.getGreen(), color.getBlue());
  }

  public static class CurrentSongHudWidgetConfig extends HudWidgetConfig {

    @SwitchSetting
    private final ConfigProperty<Boolean> showCover = ConfigProperty.create(true);

    @IntroducedIn(namespace = "evilradio", value = "1.1.")
    @SwitchSetting
    private final ConfigProperty<Boolean> useModernWidget = ConfigProperty.create(false);

    @SettingRequires("useModernWidget")
    @IntroducedIn(namespace = "evilradio", value = "1.1.0")
    @SwitchSetting
    private final ConfigProperty<Boolean> showLastSong = ConfigProperty.create(false);

    @SettingSection("customization")

    @IntroducedIn(namespace = "evilradio", value = "1.1.0")
    @SliderSetting(min = 0, max = 100)
    private final ConfigProperty<Integer> backgroundBlur = ConfigProperty.create(25);

    @IntroducedIn(namespace = "evilradio", value = "1.1.0")
    @ColorPickerSetting(alpha = true)
    private final ConfigProperty<Color> backgroundColor = ConfigProperty.create(DEFAULT_BACKGROUND_COLOR);

    @IntroducedIn(namespace = "evilradio", value = "1.1.0")
    @ColorPickerSetting(alpha = true)
    private final ConfigProperty<Color> borderColor = ConfigProperty.create(DEFAULT_BORDER_COLOR);

    @IntroducedIn(namespace = "evilradio", value = "1.1.0")
    @ColorPickerSetting(alpha = true)
    private final ConfigProperty<Color> progressBarColor = ConfigProperty.create(DEFAULT_PROGRESS_BAR_COLOR);

    @IntroducedIn(namespace = "evilradio", value = "1.1.0")
    @ColorRowBreak
    @ColorPickerSetting
    private final ConfigProperty<Color> stationColor = ConfigProperty.create(DEFAULT_STATION_COLOR);

    @IntroducedIn(namespace = "evilradio", value = "1.1.0")
    @ColorPickerSetting
    private final ConfigProperty<Color> songColor = ConfigProperty.create(DEFAULT_SONG_COLOR);

    @IntroducedIn(namespace = "evilradio", value = "1.1.0")
    @ColorPickerSetting
    private final ConfigProperty<Color> artistColor = ConfigProperty.create(DEFAULT_ARTIST_COLOR);

    public ConfigProperty<Boolean> showCover() {
      return this.showCover;
    }

    public ConfigProperty<Boolean> showLastSong() {
      return showLastSong;
    }

    public ConfigProperty<Integer> backgroundBlur() {
      return backgroundBlur;
    }

    public ConfigProperty<Color> backgroundColor() {
      return this.backgroundColor;
    }

    public ConfigProperty<Color> borderColor() {
      return borderColor;
    }

    public ConfigProperty<Color> progressBarColor() {
      return this.progressBarColor;
    }

    public ConfigProperty<Color> stationColor() {
      return this.stationColor;
    }

    public ConfigProperty<Color> songColor() {
      return this.songColor;
    }

    public ConfigProperty<Color> artistColor() {
      return this.artistColor;
    }

  }

}
