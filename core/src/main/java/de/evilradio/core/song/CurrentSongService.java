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
  private String currentStreamName = null;
  private EvilRadioAddon addon;

  private Task updaterTask;
  
  // Trackt, ob bereits eine Twitch-Nachricht für die aktuelle Live-Session gesendet wurde
  private boolean twitchNotificationSent = false;

  public CurrentSongService(EvilRadioAddon addon) {
    this.addon = addon;
  }

  public void startUpdater() {
    this.updaterTask = Task.builder(() -> {
      if(this.addon.radioManager().isPlaying()) {
        fetchCurrentSong();
      }
    }).repeat(1, TimeUnit.MINUTES).build();
    this.updaterTask.execute();
  }

  public void stopUpdater() {
    if(this.updaterTask == null) return;
    this.updaterTask.cancel();
  }

  private CurrentSong getSongFromJson(JsonObject object) {
    if(!object.has("current")) return null;
    if(!object.get("current").isJsonObject()) return null;
    JsonObject currentSongObject = object.get("current").getAsJsonObject();
    if (!currentSongObject.has("title") || !currentSongObject.has("artist") || !currentSongObject.has("image")) return null;
    String title = currentSongObject.get("title").getAsString();
    String artist = currentSongObject.get("artist").getAsString();
    String image = currentSongObject.get("image").getAsString();

    String moderatorName = null;
    boolean twitch = false;
    boolean onAir = false;
    if (object.has("show") && object.get("show").isJsonObject()) {
      JsonObject showObject = object.get("show").getAsJsonObject();
      if (showObject.has("twitch")) {
        twitch = showObject.get("twitch").getAsBoolean();
      }
      if (showObject.has("live")) {
        onAir = showObject.get("live").getAsBoolean();
      }
      if (showObject.has("dj")) {
        moderatorName = showObject.get("dj").getAsString();
      }
    }
    return new CurrentSong(title, artist, image, moderatorName, twitch, onAir);
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
      this.currentStreamName = null;
      this.addon.currentSongHudWidget().requestUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
      return;
    }
    
    // Prüfe, ob sich der Stream geändert hat (auch wenn currentStreamName null ist, z.B. nach resetCurrentSong())
    boolean streamChanged = this.currentStreamName != null && !this.currentStreamName.equals(streamName);
    // Wenn currentStreamName null ist, bedeutet das, dass der Stream zurückgesetzt wurde oder noch nicht geladen wurde
    // In diesem Fall sollte auch keine "Neuer Song" Notification kommen
    boolean isFirstLoadOrReset = this.currentStreamName == null;
    
    // Wenn sich der Stream geändert hat oder es der erste Load/Reset ist, setze currentSong auf null
    // um keine "Neuer Song" Notification auszulösen
    if (streamChanged || isFirstLoadOrReset) {
      this.currentSong = null;
    }
    
    // Speichere den Song und Stream-Namen vor dem Request, um später zu prüfen, ob er sich geändert hat
    CurrentSong songBefore = this.currentSong;
    String streamNameBefore = this.currentStreamName;
    
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

          CurrentSong newSong = getSongFromJson(object);
          if(newSong == null) {
            this.currentSong = null;
            this.addon.currentSongHudWidget().requestUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
            return;
          }

          // Prüfe erneut, ob sich der Stream geändert hat (könnte sich während des Requests geändert haben)
          boolean streamStillChanged = streamNameBefore != null && !streamNameBefore.equals(streamName);
          // Prüfe, ob es der erste Load/Reset war
          boolean wasFirstLoadOrReset = streamNameBefore == null;

          // Prüfe, ob sich der Song geändert hat (nur wenn bereits ein Song geladen war UND der Stream gleich geblieben ist)
          // Beim Streamwechsel oder beim ersten Laden soll keine "Neuer Song" Notification kommen
          boolean songChanged = !streamStillChanged && !wasFirstLoadOrReset && songBefore != null &&
              (!songBefore.getTitle().equals(newSong.getTitle()) ||
                  !songBefore.getArtist().equals(newSong.getArtist()));

          // Alte Twitch-Erkennung deaktiviert - wird jetzt über ScheduleService mit Sendeplan-API gehandhabt
          // Die Twitch-Status-Anzeige im Widget bleibt weiterhin aktiv
          // Prüfe Twitch-Status für Chat-Nachricht (nur für Mashup-Stream)
          // boolean wasTwitchLive = this.currentSong != null && this.currentSong.isTwitch();
          // boolean isTwitchLive = newSong.isTwitch();
          // boolean isMashupStream = streamName != null && streamName.equalsIgnoreCase("mashup");
          
          // Wenn Twitch nicht mehr live ist, setze die Flag zurück
          // if (wasTwitchLive && !isTwitchLive) {
          //   this.twitchNotificationSent = false;
          // }
          
          // Wenn sich der Stream geändert hat, setze die Flag zurück
          // if (streamStillChanged || wasFirstLoadOrReset) {
          //   this.twitchNotificationSent = false;
          // }
          
          // Sende Chat-Nachricht, wenn Twitch gerade live geworden ist (nur einmalig pro Session)
          // if (isMashupStream && isTwitchLive && !wasTwitchLive && !this.twitchNotificationSent) {
          //   this.twitchNotificationSent = true;
          //   this.addon.labyAPI().minecraft().executeOnRenderThread(() -> {
          //     Component twitchMessage = Component.text("EvilRadio ist jetzt live auf Twitch! ")
          //         .color(net.labymod.api.client.component.format.NamedTextColor.GRAY)
          //         .append(Component.text("https://www.twitch.tv/evilradiode")
          //             .color(net.labymod.api.client.component.format.TextColor.color(145, 70, 255))); // Twitch-Farbe
          //     this.addon.labyAPI().minecraft().chatExecutor().displayClientMessage(twitchMessage);
          //   });
          // }

          this.currentSong = newSong;
          // Aktualisiere den aktuellen Stream-Namen erst nach erfolgreichem Laden
          this.currentStreamName = streamName;
          // Aktualisiere das Widget nach dem Setzen des aktuellen Songs
          this.addon.currentSongHudWidget().requestUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);

          // Zeige Notification nur, wenn sich der Song geändert hat (nicht beim ersten Laden oder Streamwechsel)
          if (songChanged && this.addon.configuration().showSongChangeNotification().get()) {
            RadioStream notificationStream = this.addon.radioManager().getCurrentStream();
            Icon streamIcon = null;
            if (notificationStream != null) {
              streamIcon = notificationStream.getIcon();
            }
            this.addon.notification(
                Component.translatable("evilradio.notification.songChanged.title"),
                Component.translatable("evilradio.notification.songChanged.text",
                    Component.text(this.currentSong.getFormatted())),
                Icon.url(this.currentSong.getImageUrl()),
                streamIcon
            );
          }
        });
  }

  /**
   * Setzt den aktuellen Song zurück, wenn der Stream gestoppt wird.
   * Dies verhindert, dass beim nächsten Stream-Start fälschlicherweise "Neuer Song" Notifications ausgelöst werden.
   */
  public void resetCurrentSong() {
    this.currentSong = null;
    this.currentStreamName = null;
    this.twitchNotificationSent = false; // Setze auch die Twitch-Notification-Flag zurück
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
          callback.accept(getSongFromJson(object));
        });
  }

  public CurrentSong getCurrentSong() {
    return currentSong;
  }

  public Task getUpdaterTask() {
    return updaterTask;
  }

}
