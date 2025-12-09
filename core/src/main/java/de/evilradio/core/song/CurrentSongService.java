package de.evilradio.core.song;

import com.google.gson.JsonObject;
import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import de.evilradio.core.radio.RadioStream;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.util.concurrent.task.Task;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.logging.Logging;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CurrentSongService {

  private final String API_BASE_URL = "https://api.evil-radio.de/?radioInfo=";

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
        CurrentSong songBefore = this.currentSong;
        fetchCurrentSong();
        this.addon.currentSongHudWidget().requestUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
        if(songBefore == null || songBefore != this.currentSong) {
          this.addon.notification(
              Component.translatable("evilradio.notification.streamSelected.titleWithStation",
                  Component.text(this.addon.radioManager().getCurrentStream().getDisplayName())),
              Component.translatable("evilradio.notification.streamSelected.textWithSong",
                      Component.text(this.currentSong.getFormatted())),
              Icon.url(this.currentSong.getImageUrl()),
              this.addon.radioManager().getCurrentStream().getIcon()
          );
        }
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
    RadioStream currentStream = this.addon.radioManager().getCurrentStream();
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
    
    Request.ofGson(JsonObject.class)
        .url(API_BASE_URL + streamName)
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

  public void fetchCurrentSong(String streamName, Consumer<CurrentSong> callback) {
    if (streamName == null || streamName.isEmpty()) {
      callback.accept(null);
      return;
    }
    Request.ofGson(JsonObject.class)
        .url(API_BASE_URL + streamName)
        .async()
        .connectTimeout(5000)
        .readTimeout(5000)
        .userAgent("EvilRadio LabyMod 4 Addon")
        .execute(response -> {
          if (response.getStatusCode() != 200 || response.hasException()) {
            callback.accept(null);
            return;
          }
          JsonObject object = response.get();
          if(!object.has("current")) {
            callback.accept(null);
            return;
          }
          if(!object.get("current").isJsonObject()) {
            callback.accept(null);
            return;
          }
          JsonObject currentSongObject = object.get("current").getAsJsonObject();
          if (!currentSongObject.has("title") || !currentSongObject.has("artist") || !currentSongObject.has("image")) {
            callback.accept(null);
            return;
          }
          String title = currentSongObject.get("title").getAsString();
          String artist = currentSongObject.get("artist").getAsString();
          String image = currentSongObject.get("image").getAsString();
          callback.accept(new CurrentSong(title, artist, image));
        });
  }

  public CurrentSong getCurrentSong() {
    return currentSong;
  }

  public Task getUpdaterTask() {
    return updaterTask;
  }

}
