package de.evilradio.core.hudwidget;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.EvilTextures;
import de.evilradio.core.hudwidget.CurrentSongHudWidget.CurrentSongHudWidgetConfig;
import de.evilradio.core.hudwidget.widget.CurrentSongWidget;
import net.labymod.api.client.gui.hud.hudwidget.HudWidgetConfig;
import net.labymod.api.client.gui.hud.hudwidget.widget.WidgetHudWidget;
import net.labymod.api.client.gui.screen.widget.widgets.hud.HudWidgetWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.color.ColorPickerWidget.ColorPickerSetting;
import net.labymod.api.configuration.loader.annotation.IntroducedIn;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.util.Color;
import net.labymod.api.util.ThreadSafe;

public class CurrentSongHudWidget extends WidgetHudWidget<CurrentSongHudWidgetConfig> {

  public static final String COVER_VISIBILITY_REASON = "cover_visibility";
  public static final String SONG_CHANGE_REASON = "song_change";
  public static final String TITLE_LENGTH_CHANGE_REASON = "title_length_change";
  public static final String BACKGROUND_COLOR_REASON = "background_color";

  private final EvilRadioAddon addon;

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
    config.limitTitleLength.addChangeListener(
        (property, oldValue, newValue) -> ThreadSafe.executeOnRenderThread(
            () -> this.requestUpdate(TITLE_LENGTH_CHANGE_REASON))
    );
    config.maxTitleLength.addChangeListener(
        (property, oldValue, newValue) -> ThreadSafe.executeOnRenderThread(
            () -> this.requestUpdate(TITLE_LENGTH_CHANGE_REASON))
    );
    config.backgroundColor.addChangeListener(
        (property, oldValue, newValue) -> ThreadSafe.executeOnRenderThread(
            () -> this.requestUpdate(BACKGROUND_COLOR_REASON))
    );
  }

  @Override
  public void initialize(HudWidgetWidget widget) {
    super.initialize(widget);

    CurrentSongWidget currentSongWidget = new CurrentSongWidget(this, widget.accessor().isEditor());
    widget.addChild(currentSongWidget);
    widget.addId("current-song");
  }

  @Override
  public boolean isVisibleInGame() {
    if (!this.addon.configuration().enabled().get()) return false;
    return this.addon.radioManager().isPlaying();
  }

  public static class CurrentSongHudWidgetConfig extends HudWidgetConfig {

    @SwitchSetting
    private final ConfigProperty<Boolean> showCover = ConfigProperty.create(true);

    @IntroducedIn(namespace = "evilradio", value = "1.0.4")
    @SwitchSetting
    private final ConfigProperty<Boolean> limitTitleLength = ConfigProperty.create(true);

    @IntroducedIn(namespace = "evilradio", value = "1.0.4")
    @SliderSetting(min = 0, max = 500, steps = 10)
    private final ConfigProperty<Integer> maxTitleLength = ConfigProperty.create(150);

    @IntroducedIn(namespace = "evilradio", value = "1.0.5")
    @ColorPickerSetting(alpha = true)
    private final ConfigProperty<Color> backgroundColor = ConfigProperty.create(Color.ofRGB(0, 0, 0));

    public ConfigProperty<Boolean> showCover() {
      return this.showCover;
    }

    public ConfigProperty<Boolean> limitTitleLength() {
      return this.limitTitleLength;
    }

    public ConfigProperty<Integer> maxTitleLength() {
      return maxTitleLength;
    }

    public ConfigProperty<Color> backgroundColor() {
      return this.backgroundColor;
    }

  }

  public EvilRadioAddon addon() {
    return addon;
  }

}
