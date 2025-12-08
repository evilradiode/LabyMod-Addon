package de.evilradio.core.song;

import com.google.gson.JsonObject;
import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import net.labymod.api.util.concurrent.task.Task;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.logging.Logging;
import java.util.concurrent.TimeUnit;

public class CurrentSongService {

  private final Logging logging = Logging.create("EvilRadio-CurrentSongService");

  private CurrentSong currentSong = null;
  private EvilRadioAddon addon;

  private Task updaterTask;

  public CurrentSongService(EvilRadioAddon addon) {
    this.addon = addon;
  }

  public void startUpdater() {
    this.updaterTask = Task.builder(() -> {
      if(this.addon.radioManager().isPlaying()) {
        fetchCurrentSong();
        this.addon.currentSongHudWidget().requestUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
      }
    }).repeat(1, TimeUnit.MINUTES).build();
    this.updaterTask.execute();
  }

  public void stopUpdater() {
    if(this.updaterTask == null) return;
    this.updaterTask.cancel();
  }

  public void fetchCurrentSong() {
    // Hole den aktuellen Stream
    de.evilradio.core.radio.RadioStream currentStream = this.addon.radioManager().getCurrentStream();
    if (currentStream == null) {
      logging.warn("No current stream found, cannot fetch song info");
      this.currentSong = null;
      this.addon.currentSongHudWidget().requestUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
      return;
    }
    
    String streamName = currentStream.getName();
    if (streamName == null || streamName.isEmpty()) {
      logging.warn("Current stream has no name, cannot fetch song info");
      this.currentSong = null;
      this.addon.currentSongHudWidget().requestUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
      return;
    }
    
    String apiUrl = "https://api.evil-radio.de/?radioInfo=" + streamName;
    
    Request.ofGson(JsonObject.class)
        .url(apiUrl)
        .async()
        .connectTimeout(5000)
        .readTimeout(5000)
        .userAgent("EvilRadio LabyMod 4 Addon")
        .execute(response -> {
          if(response.hasException() || response.getStatusCode() != 200) {
            logging.error("Failed to load current song", response.hasException() ? response.exception() : new Exception("HTTP " + response.getStatusCode()));
            this.currentSong = null;
            this.addon.currentSongHudWidget().requestUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
            return;
          }
          
          JsonObject object = response.get();
          
          if(object.has("current") && object.get("current").isJsonObject()) {
            JsonObject currentSongObject = object.get("current").getAsJsonObject();
            
            // Prüfe, ob alle benötigten Felder vorhanden sind
            if (!currentSongObject.has("title") || !currentSongObject.has("artist") || !currentSongObject.has("image")) {
              logging.warn("API response missing required fields. Available keys: " + currentSongObject.keySet());
              this.currentSong = null;
              this.addon.currentSongHudWidget().requestUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
              return;
            }
            
            String title = currentSongObject.get("title").getAsString();
            String artist = currentSongObject.get("artist").getAsString();
            String image = currentSongObject.get("image").getAsString();
            
            this.currentSong = new CurrentSong(title, artist, image);
            // Aktualisiere das Widget nach dem Setzen des aktuellen Songs
            this.addon.currentSongHudWidget().requestUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
          } else {
            // Wenn kein Song gefunden wurde, setze currentSong auf null
            this.currentSong = null;
            this.addon.currentSongHudWidget().requestUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
          }
        });
  }

  public CurrentSong getCurrentSong() {
    return currentSong;
  }

  public Task getUpdaterTask() {
    return updaterTask;
  }

}
