package de.evilradio.core.listener;

import de.evilradio.core.EvilRadioAddon;
import net.labymod.api.client.gui.screen.widget.Widget;
import net.labymod.api.client.gui.screen.widget.widgets.activity.settings.SettingWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.configuration.settings.Setting;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.labymod.config.SettingWidgetInitializeEvent;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * LabyMod zeigt das Subsettings-Zahnrad immer, sobald Kind-Settings existieren.
 * {@code @SettingRequires} deaktiviert nur den Inhalt – deshalb blenden wir den
 * {@code advanced-button} selbst aus, solange das Preset nicht CUSTOM ist.
 */
public class AudioEqualizerSettingsListener {

  private final EvilRadioAddon addon;
  private @Nullable ButtonWidget advancedButton;

  public AudioEqualizerSettingsListener(EvilRadioAddon addon) {
    this.addon = addon;
    this.addon.configuration().audioEqualizer().preset()
        .addChangeListener(preset -> this.syncAdvancedButton());
  }

  @Subscribe
  public void onSettingWidgetInitialize(SettingWidgetInitializeEvent event) {
    List<SettingWidget> equalizerWidgets = new ArrayList<>();
    for (Widget widget : event.getSettings()) {
      if (!(widget instanceof SettingWidget settingWidget)) {
        continue;
      }
      Setting setting = settingWidget.setting();
      if ("audioEqualizer".equals(setting.getId())) {
        equalizerWidgets.add(settingWidget);
      }
    }
    if (equalizerWidgets.isEmpty()) {
      return;
    }

    // advanced-button entsteht erst beim Initialize nach dem Event
    this.addon.labyAPI().minecraft().executeNextTick(() -> {
      this.advancedButton = null;
      for (SettingWidget settingWidget : equalizerWidgets) {
        Widget child = settingWidget.getChildRecursive("advanced-button");
        if (child instanceof ButtonWidget button) {
          this.advancedButton = button;
          this.syncAdvancedButton();
          break;
        }
      }
    });
  }

  private void syncAdvancedButton() {
    if (this.advancedButton == null) {
      return;
    }
    boolean custom = this.addon.configuration().audioEqualizer().preset().get().isCustom();
    this.advancedButton.setVisible(custom);
  }
}
