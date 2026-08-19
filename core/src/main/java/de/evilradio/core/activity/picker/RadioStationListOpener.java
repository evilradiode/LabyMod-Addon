package de.evilradio.core.activity.picker;

import de.evilradio.core.EvilRadioAddon;
import net.labymod.api.Laby;
import net.labymod.api.client.gui.screen.LabyScreen;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.KeyEvent;
import net.labymod.api.event.client.input.KeyEvent.State;

/**
 * Öffnet die Listen-Senderwahl als eigenen Screen (Toggle per Radio-Menü-Taste).
 */
public final class RadioStationListOpener {

  private final EvilRadioAddon addon;

  public RadioStationListOpener(EvilRadioAddon addon) {
    this.addon = addon;
  }

  @Subscribe
  public void onKey(KeyEvent event) {
    if (event.state() != State.PRESS) return;
    if (!this.addon.configuration().enabled().get()) return;

    Key openKey = this.addon.configuration().radioMenuKeybind().get();
    if (event.key() != openKey) return;

    LabyScreen current = Laby.labyAPI().minecraft().minecraftWindow().currentLabyScreen();
    if (current instanceof RadioStationListActivity) {
      ((RadioStationListActivity) current).displayPreviousScreen();
      event.setCancelled(true);
      return;
    }

    // Nur ingame öffnen (Maus gegriffen = kein anderer Screen).
    if (!Laby.labyAPI().minecraft().isMouseLocked()) return;

    this.addon.openStationPicker();
    event.setCancelled(true);
  }
}
