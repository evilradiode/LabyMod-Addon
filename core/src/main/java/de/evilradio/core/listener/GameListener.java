package de.evilradio.core.listener;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.configuration.AutoStartSubSettings;
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

    // Prüfe, ob Auto-Start aktiviert ist
    if (!this.addon.configuration().autoStart().enabled().get()) return;

    AutoStartSubSettings.AutoStartMode mode = this.addon.configuration().autoStart().mode().get();
    if (mode != null && mode.shouldStartOnServerJoin()) {
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
    // Prüfe, ob der Benutzer das Radio manuell gestoppt hat
    if (this.addon.isUserManuallyStopped()) return;

    // Prüfe, ob Auto-Start aktiviert ist
    if (!this.addon.configuration().autoStart().enabled().get()) return;

    AutoStartSubSettings.AutoStartMode mode = this.addon.configuration().autoStart().mode().get();
    if (mode != null && mode.shouldStartOnServerJoin()) {
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
    // Prüfe, ob Auto-Start aktiviert ist und auf "Beim Welt betreten" steht
    if (!this.addon.configuration().autoStart().enabled().get()) return;

    AutoStartSubSettings.AutoStartMode mode = this.addon.configuration().autoStart().mode().get();
    if (mode != null && mode.shouldStartOnServerJoin()) {
      // Stoppe den Stream, wenn er läuft
      if (this.addon.radioManager() != null && this.addon.radioManager().isPlaying()) {
        this.addon.radioManager().stopStream();
      }
    }
  }

}
