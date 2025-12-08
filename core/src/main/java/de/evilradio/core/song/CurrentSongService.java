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
    }).repeat(3, TimeUnit.MINUTES).build();
    this.updaterTask.execute();
  }

  public void stopUpdater() {
    if(this.updaterTask == null) return;
    this.updaterTask.cancel();
  }

  public void fetchCurrentSong() {
    Request.ofGson(JsonObject.class)
        .url("https://api.evil-radio.de/laby-addon/")
        .async()
        .connectTimeout(5000)
        .readTimeout(5000)
        .addHeader("User-Agent", "EvilRadio LabyMod 4 Addon")
        .execute(response -> {
          if(response.hasException()) {
            logging.error("Failed to load streams", response.exception());
            return;
          }
          JsonObject object = response.get();
          if(object.has("current") && object.get("current").isJsonObject()) {
            JsonObject currentSongObject = object.get("current").getAsJsonObject();
            this.currentSong = new CurrentSong(
                currentSongObject.get("title").getAsString(),
                currentSongObject.get("artist").getAsString(),
                currentSongObject.get("image").getAsString()
            );
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
