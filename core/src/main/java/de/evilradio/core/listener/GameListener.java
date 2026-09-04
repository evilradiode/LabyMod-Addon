package de.evilradio.core.listener;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.activity.picker.RadioStationListActivity;
import net.labymod.api.Laby;
import net.labymod.api.client.gui.screen.LabyScreen;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.KeyEvent;
import net.labymod.api.event.client.input.KeyEvent.State;
import net.labymod.api.event.client.network.server.ServerJoinEvent;
import net.labymod.api.event.client.world.WorldEnterEvent;
import net.labymod.api.event.client.world.WorldLeaveEvent;

public class GameListener {

  private EvilRadioAddon addon;

  public GameListener(EvilRadioAddon addon) {
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

    if (!Laby.labyAPI().minecraft().isMouseLocked()) return;

    this.addon.openStationPicker();
    event.setCancelled(true);
  }

  /**
   * Event-Handler für Server-Beitritt
   * Wird aufgerufen, wenn der Spieler einem Server beitritt
   */
  @Subscribe
  public void onServerJoin(ServerJoinEvent event) {
    // Prüfe, ob der Stream bereits läuft - wenn ja, tue nichts (verhindert Pause beim Subserver-Wechsel)
    if (this.addon.radioManager() != null && this.addon.radioManager().isPlaying()) return;

    // Prüfe, ob der Benutzer das Radio manuell gestoppt hat
    if (this.addon.isUserManuallyStopped()) return;

    if (this.addon.configuration().autoStartMode().get().shouldStartOnServerJoin()) {
      this.addon.startLastStreamWithDelay("server join");
    }
  }

  /**
   * Event-Handler für World-Beitritt
   * Wird aufgerufen, wenn der Spieler einer Welt beitritt (auch im Singleplayer)
   * Startet den Stream, wenn Auto-Start auf "Beim Welt betreten" steht
   */
  @Subscribe
  public void onWorldEnter(WorldEnterEvent event) {
    if (this.addon.radioManager() != null && this.addon.radioManager().isPlaying()) return;

    // Prüfe, ob der Benutzer das Radio manuell gestoppt hat
    if (this.addon.isUserManuallyStopped()) return;

    if (this.addon.configuration().autoStartMode().get().shouldStartOnServerJoin()) {
      this.addon.startLastStreamWithDelay("world enter");
    }
  }

  /**
   * Event-Handler für World-Verlassen
   * Wird aufgerufen, wenn der Spieler eine Welt verlässt
   * Stoppt den Stream, wenn Auto-Start auf "Beim Welt betreten" steht
   */
  @Subscribe
  public void onWorldLeave(WorldLeaveEvent event) {
    if (this.addon.configuration().autoStartMode().get().shouldStartOnServerJoin()) {
      // Stoppe den Stream, wenn er läuft
      if (this.addon.radioManager() != null && this.addon.radioManager().isPlaying()) {
        this.addon.radioManager().stopStream();
      }
    }
  }

}
