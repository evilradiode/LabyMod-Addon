package de.evilradio.core.hudwidget;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.hudwidget.CurrentSongHudWidget.CurrentSongHudWidgetConfig;
import de.evilradio.core.hudwidget.widget.CurrentSongWidget;
import net.labymod.api.client.gui.hud.hudwidget.HudWidgetConfig;
import net.labymod.api.client.gui.hud.hudwidget.widget.WidgetHudWidget;
import net.labymod.api.client.gui.screen.widget.widgets.hud.HudWidgetWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.util.ThreadSafe;
import net.labymod.api.util.bounds.area.RectangleAreaPosition;

public class CurrentSongHudWidget extends WidgetHudWidget<CurrentSongHudWidgetConfig> {

  public static final String COVER_VISIBILITY_REASON = "cover_visibility";
  public static final String SONG_CHANGE_REASON = "song_change";

  private final EvilRadioAddon addon;

  public CurrentSongHudWidget(EvilRadioAddon addon) {
    super("evilradio_full_widget", CurrentSongHudWidgetConfig.class);
    this.addon = addon;
  }

  @Override
  public void initializePreConfigured(CurrentSongHudWidgetConfig config) {
    super.initializePreConfigured(config);

    config.setEnabled(true);
    config.setAreaIdentifier(RectangleAreaPosition.TOP_RIGHT);
    config.setX(-2);
    config.setY(2);
    config.setParentToTailOfChainIn(RectangleAreaPosition.TOP_RIGHT);
  }

  @Override
  public void load(CurrentSongHudWidgetConfig config) {
    super.load(config);
    config.showCover.addChangeListener(
        (property, oldValue, newValue) -> ThreadSafe.executeOnRenderThread(
            () -> this.requestUpdate(COVER_VISIBILITY_REASON))
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
    // Widget nur anzeigen, wenn Addon aktiviert ist und ein Stream läuft
    if (!this.addon.configuration().enabled().get()) {
      return false;
    }
    
    // Widget nur anzeigen, wenn der Stream tatsächlich läuft
    // Nicht nur wenn ein Stream ausgewählt ist (damit das Widget verschwindet, wenn gestoppt wird)
    return this.addon.radioManager().isPlaying();
  }

  public static class CurrentSongHudWidgetConfig extends HudWidgetConfig {

    @SwitchSetting
    private final ConfigProperty<Boolean> showCover = ConfigProperty.create(true);

    public ConfigProperty<Boolean> showCover() {
      return this.showCover;
    }

  }

  public EvilRadioAddon addon() {
    return addon;
  }

}
