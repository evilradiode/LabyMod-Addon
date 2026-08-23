package de.evilradio.core.listener;

import de.evilradio.core.EvilRadioAddon;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.network.server.ServerJoinEvent;
import net.labymod.api.event.client.world.WorldEnterEvent;
import net.labymod.api.event.client.world.WorldLeaveEvent;

public class GameListener {

  private EvilRadioAddon addon;

  public GameListener(EvilRadioAddon addon) {
    this.addon = addon;
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
