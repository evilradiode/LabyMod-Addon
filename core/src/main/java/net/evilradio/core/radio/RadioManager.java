package net.evilradio.core.radio;

import java.util.ArrayList;
import java.util.List;
import net.evilradio.core.RadioStreamConfig;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.resources.ResourceLocation;

public class RadioManager {
  private final List<RadioStream> streams;
  private RadioStream currentStream;
  private boolean isPlaying;
  private RadioPlayer radioPlayer;

  public RadioManager() {
    this.streams = new ArrayList<>();
    this.isPlaying = false;
    this.radioPlayer = new RadioPlayer();
  }
  
  /**
   * Initialisiert die Streams aus der Konfiguration.
   * Validiert dabei die URLs gegen die Whitelist (falls aktiviert).
   * 
   * @param streamConfigs Liste der Radio-Stream-Konfigurationen
   * @param ignoreWhitelist Wenn true, wird die Whitelist-Validierung übersprungen
   */
  public void initializeStreams(List<RadioStreamConfig> streamConfigs, boolean ignoreWhitelist) {
    streams.clear();
    
    for (RadioStreamConfig config : streamConfigs) {
      String url = config.url().get();
      String name = config.name().get();
      String displayName = config.displayName().get();
      String genre = config.genre().get();
      String country = config.country().get();
      int bitrate = config.bitrate().get();
      String iconPath = config.iconPath().get();
      
      // Validiere die URL gegen die Whitelist (nur wenn Whitelist aktiviert ist)
      if (!ignoreWhitelist && !url.isEmpty() && !RadioStreamWhitelist.isAllowedStream(url)) {
        // URL ist nicht erlaubt - überspringe diesen Stream oder verwende leere URL
        String originalUrl = url;
        url = "";
        // Optional: Log-Warnung ausgeben
        System.err.println("Warnung: Radio-Stream-URL ist nicht in der Whitelist: " + originalUrl);
      }
      
      // Erstelle Icon aus dem Pfad
      Icon icon = null;
      if (iconPath != null && !iconPath.isEmpty()) {
        try {
          // Parse ResourceLocation aus String (Format: "namespace:path")
          String[] parts = iconPath.split(":", 2);
          if (parts.length == 2) {
            icon = Icon.texture(ResourceLocation.create(parts[0], parts[1]));
          } else {
            // Fallback: verwende als direkten Pfad
            icon = Icon.texture(ResourceLocation.create("evilradio", iconPath));
          }
        } catch (Exception e) {
          // Bei Fehler: verwende Standard-Icon
          icon = Icon.texture(ResourceLocation.create("evilradio", "textures/stations/default.png"));
        }
      }
      
      // Verwende displayName, falls vorhanden, sonst name
      String finalDisplayName = (displayName != null && !displayName.isEmpty()) ? displayName : name;
      String category = config.category().get();
      
      streams.add(new RadioStream(
          url,
          name,
          genre,
          country,
          bitrate,
          icon,
          finalDisplayName,
          category
      ));
    }
  }

  public List<RadioStream> getStreams() {
    return new ArrayList<>(streams);
  }

  public RadioStream getCurrentStream() {
    return currentStream;
  }

  public void setCurrentStream(RadioStream stream) {
    this.currentStream = stream;
  }

  public boolean isPlaying() {
    return isPlaying;
  }

  public void setPlaying(boolean playing) {
    this.isPlaying = playing;
  }

  public void playStream(RadioStream stream) {
    if (currentStream != null && currentStream.equals(stream) && isPlaying) {
      // Wenn derselbe Stream bereits läuft, pausiere/starte ihn
      if (radioPlayer != null && radioPlayer.isPaused()) {
        resumeStream();
      }
      return;
    }
    
    stopStream();
    currentStream = stream;
    isPlaying = true;
    
    // Starte die Wiedergabe des Streams
    if (radioPlayer != null && stream != null) {
      radioPlayer.play(stream.getUrl());
    }
  }

  public void stopStream() {
    isPlaying = false;
    
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

