package de.evilradio.core.listener;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.configuration.StationPickerStyle;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import net.labymod.api.client.gui.screen.widget.Widget;
import net.labymod.api.client.gui.screen.widget.widgets.activity.settings.SettingWidget;
import net.labymod.api.configuration.settings.Setting;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.labymod.config.SettingWidgetInitializeEvent;

/**
 * Blendet das Zahnrad bei „Sender-Menü Stil“ nur ein, wenn Liste + Preview aktiv ist.
 */
public class StationPickerSettingsListener {

  private final EvilRadioAddon addon;
  private final List<WeakReference<Widget>> gearButtons = new ArrayList<>();

  public StationPickerSettingsListener(EvilRadioAddon addon) {
    this.addon = addon;
    this.addon.configuration().stationPicker().style().addChangeListener(style ->
        this.addon.labyAPI().minecraft().executeOnRenderThread(this::syncGearVisibility));
  }

  @Subscribe
  public void onSettingWidgetInitialize(SettingWidgetInitializeEvent event) {
    for (Widget widget : event.getSettings()) {
      if (!(widget instanceof SettingWidget settingWidget)) {
        continue;
      }
      Setting setting = settingWidget.setting();
      if (setting == null || !"stationPicker".equals(setting.getId())) {
        continue;
      }
      this.collectGearButtons(settingWidget);
      this.syncGearVisibility();
    }
  }

  private void collectGearButtons(Widget root) {
    List<Widget> found = new ArrayList<>();
    root.traverse(found, child -> child.hasId("advanced-button"));
    for (Widget gear : found) {
      this.gearButtons.add(new WeakReference<>(gear));
    }
  }

  private void syncGearVisibility() {
    boolean show = this.addon.configuration().stationPicker().style().get()
        == StationPickerStyle.LIST_PREVIEW;
    this.gearButtons.removeIf(ref -> ref.get() == null);
    for (WeakReference<Widget> ref : this.gearButtons) {
      Widget gear = ref.get();
      if (gear != null) {
        gear.setVisible(show);
      }
    }
  }
}
