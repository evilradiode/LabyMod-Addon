package de.evilradio.core.listener;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.radio.AudioStreamDebug;
import net.labymod.api.client.gui.screen.widget.Widget;
import net.labymod.api.client.gui.screen.widget.widgets.activity.settings.SettingWidget;
import net.labymod.api.configuration.settings.Setting;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.labymod.config.SettingWidgetInitializeEvent;

/**
 * Blendet „Audio-Stream-Debug“ nur für freigeschaltete Laby-UUIDs ein.
 */
public class AudioStreamDebugSettingsListener {

  private final EvilRadioAddon addon;

  public AudioStreamDebugSettingsListener(EvilRadioAddon addon) {
    this.addon = addon;
  }

  @Subscribe
  public void onSettingWidgetInitialize(SettingWidgetInitializeEvent event) {
    boolean allowed = AudioStreamDebug.isUuidAllowed(this.addon.labyAPI().getUniqueId());
    for (Widget widget : event.getSettings()) {
      if (!(widget instanceof SettingWidget settingWidget)) {
        continue;
      }
      Setting setting = settingWidget.setting();
      if (setting == null || !"audioStreamDebug".equals(setting.getId())) {
        continue;
      }
      settingWidget.setVisible(allowed);
      if (!allowed && Boolean.TRUE.equals(this.addon.configuration().audioStreamDebug().get())) {
        this.addon.configuration().audioStreamDebug().set(false);
      }
    }
  }
}
