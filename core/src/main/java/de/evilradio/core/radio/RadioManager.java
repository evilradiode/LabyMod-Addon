package de.evilradio.core.radio;

import de.evilradio.core.EvilRadioAddon;

public class RadioManager {

  private RadioStream currentStream;
  private boolean isPlaying;
  private RadioPlayer radioPlayer;
  private EvilRadioAddon addon;

  public RadioManager(EvilRadioAddon addon) {
    this.addon = addon;
    this.isPlaying = false;
    this.radioPlayer = new RadioPlayer();
  }

  public RadioStream getCurrentStream() {
    return currentStream;
  }

  public void setCurrentStream(RadioStream stream) {
    this.currentStream = stream;
  }

  public boolean isPlaying() {
    // Synchronisiere mit dem tatsächlichen Status vom RadioPlayer
    if (radioPlayer != null) {
      isPlaying = radioPlayer.isPlaying();
    }
    return isPlaying;
  }

  public void setPlaying(boolean playing) {
    this.isPlaying = playing;
  }

  public void playStream(RadioStream stream) {
    // Wenn derselbe Stream bereits läuft und spielt, pausiere/starte ihn
    if (currentStream != null && currentStream.equals(stream) && isPlaying) {
      if (radioPlayer != null && radioPlayer.isPaused()) {
        resumeStream();
      }
      return;
    }
    
    // Wenn der Stream gestoppt wurde (isPlaying = false), starte ihn neu
    stopStream();
    currentStream = stream;
    
    // Speichere die ID des letzten gestarteten Streams
    if (stream != null && addon != null) {
      addon.configuration().lastStreamId().set(stream.getId());
    }
    
    // Tracke die Nutzung des Streams (nur wenn Usage-Tracking aktiviert ist)
    if (stream != null && addon != null && addon.configuration().usageBasedSorting().get()) {
      addon.configuration().incrementStreamUsage(stream.getId());
    }
    
    // Starte die Wiedergabe des Streams
    if (radioPlayer != null && stream != null) {
      radioPlayer.play(stream.getUrl());
      // Warte kurz und prüfe dann, ob der Stream erfolgreich gestartet wurde
      // Der Status wird vom RadioPlayer gesetzt, wenn der Stream erfolgreich startet
      // isPlaying wird in isPlaying() vom RadioPlayer abgefragt
      
      // Lade den aktuellen Song, wenn das Addon verfügbar ist
      if (addon != null && addon.currentSongService() != null) {
        addon.currentSongService().fetchCurrentSong();
      }
    }
  }

  public void stopStream() {
    isPlaying = false;
    currentStream = null;
    
    // Stoppe die Wiedergabe
    if (radioPlayer != null) {
      radioPlayer.stop();
    }
  }

  public void pauseStream() {
    if (radioPlayer != null && isPlaying) {
      radioPlayer.pause();
    }
  }

  public void resumeStream() {
    if (radioPlayer != null && isPlaying) {
      radioPlayer.resume();
    }
  }

  public void togglePlayPause() {
    if (radioPlayer != null && isPlaying) {
      if (radioPlayer.isPaused()) {
        resumeStream();
      } else {
        pauseStream();
      }
    }
  }

  public boolean isPaused() {
    return radioPlayer != null && radioPlayer.isPaused();
  }

  public void setVolume(float volume) {
    if (radioPlayer != null) {
      radioPlayer.setVolume(volume);
    }
  }

  public float getVolume() {
    return radioPlayer != null ? radioPlayer.getVolume() : 0.5f;
  }

  public void shutdown() {
    stopStream();
    if (radioPlayer != null) {
      radioPlayer.shutdown();
    }
  }
}

