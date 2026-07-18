package de.evilradio.core.song;

import com.google.gson.JsonObject;
import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.song.artwork.ArtworkCache;
import de.evilradio.core.song.azuracast.AzuraCastNowPlayingService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.logging.Logging;

public class CurrentSongService {

  private final String API_BASE_URL = "https://api.evil-radio.de/?radioInfo=";

  private final Logging logging = Logging.create("EvilRadio-CurrentSongService");

  private final AtomicReference<CurrentSong> currentSong = new AtomicReference<>();
  private final AtomicReference<NowPlayingConnectionState> connectionState =
      new AtomicReference<>(NowPlayingConnectionState.IDLE);
  private final AtomicReference<String> currentShortcode = new AtomicReference<>();
  private final AtomicReference<String> currentStreamName = new AtomicReference<>();
  private final ArtworkCache artworkCache = new ArtworkCache(32);
  private final AzuraCastNowPlayingService nowPlayingService = new AzuraCastNowPlayingService();

  private EvilRadioAddon addon;
  private boolean twitchNotificationSent = false;

  public CurrentSongService(EvilRadioAddon addon) {
    this.addon = addon;
    this.nowPlayingService.setSongListener(this::onNowPlayingSong);
    this.nowPlayingService.setStateListener(this::onConnectionState);
  }

  public void startUpdater() {
    this.nowPlayingService.start();
    RadioStream currentStream = this.addon.radioManager() == null
        ? null
        : this.addon.radioManager().getCurrentStream();
    if (currentStream != null && this.addon.radioManager().isPlaying()) {
      switchStation(currentStream);
    }
  }

  public void stopUpdater() {
    this.nowPlayingService.stop();
  }

  public void shutdown() {
    this.nowPlayingService.shutdown();
  }

  /**
   * Wechselt die WebSocket-Subscription auf den angegebenen Sender.
   */
  public void switchStation(RadioStream stream) {
    if (stream == null || !stream.hasAzuraCastShortcode()) {
      logging.warn("Cannot subscribe NowPlaying – missing AzuraCast shortcode for stream "
          + (stream == null ? "null" : stream.getName()));
      resetCurrentSong();
      this.nowPlayingService.switchStation(null);
      requestHudUpdate();
      return;
    }

    String shortcode = stream.getAzuraCastShortcode();
    String previousShortcode = this.currentShortcode.get();
    boolean stationChanged = previousShortcode != null && !previousShortcode.equals(shortcode);

    this.currentStreamName.set(stream.getName());
    this.currentShortcode.set(shortcode);
    this.artworkCache.bumpGeneration();
    this.currentSong.set(null);
    this.connectionState.set(NowPlayingConnectionState.LOADING);
    if (stationChanged) {
      this.twitchNotificationSent = false;
    }

    requestHudUpdate();
    this.nowPlayingService.switchStation(shortcode);
  }

  private void onConnectionState(NowPlayingConnectionState state, String shortcode) {
    String active = this.currentShortcode.get();
    if (shortcode != null && active != null && !active.equals(shortcode)) {
      return;
    }
    this.connectionState.set(state == null ? NowPlayingConnectionState.DISCONNECTED : state);
    requestHudUpdate();
  }

  private void onNowPlayingSong(CurrentSong song) {
    if (song == null || !song.isValid()) {
      return;
    }

    String activeShortcode = this.currentShortcode.get();
    if (activeShortcode == null) {
      return;
    }
    if (song.getStationShortcode() != null && !activeShortcode.equals(song.getStationShortcode())) {
      return;
    }

    CurrentSong previous = this.currentSong.get();
    boolean songChanged = previous != null
        && (!previous.getTitle().equals(song.getTitle()) || !previous.getArtist().equals(song.getArtist()));

    String cacheKey = ArtworkCache.key(activeShortcode, song.getSongId(), song.getImageUrl());
    long artworkGeneration = this.artworkCache.currentGeneration();
    this.artworkCache.put(cacheKey, song.getImageUrl());

    this.currentSong.set(song);
    this.connectionState.set(NowPlayingConnectionState.CONNECTED);
    requestHudUpdate();

    this.artworkCache.applyIfCurrent(artworkGeneration, song.getImageUrl(), url -> {
      // Cover-URL ist bereits im Snapshot; Generation verhindert spätere Alt-Downloads
    });

    if (songChanged && this.addon.configuration().showSongChangeNotification().get()) {
      RadioStream notificationStream = this.addon.radioManager().getCurrentStream();
      Icon streamIcon = notificationStream == null ? null : notificationStream.getIcon();
      String imageUrl = song.getImageUrl();
      this.addon.notification(
          Component.translatable("evilradio.notification.songChanged.title"),
          Component.translatable("evilradio.notification.songChanged.text",
              Component.text(song.getFormatted())),
          imageUrl == null || imageUrl.isBlank() ? null : Icon.url(imageUrl),
          streamIcon
      );
    }
  }

  private CurrentSong getSongFromJson(JsonObject object) {
    if (!object.has("current")) {
      return null;
    }
    if (!object.get("current").isJsonObject()) {
      return null;
    }
    JsonObject currentSongObject = object.get("current").getAsJsonObject();
    if (!currentSongObject.has("title") || !currentSongObject.has("artist") || !currentSongObject.has("image")) {
      return null;
    }
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
    return new CurrentSong(title, artist, image, moderatorName, onAir, twitch);
  }

  /**
   * Startet/aktualisiert die Now-Playing-Subscription für den aktuell spielenden Sender.
   */
  public void fetchCurrentSong() {
    RadioStream currentStream = this.addon.radioManager().getCurrentStream();
    if (currentStream == null) {
      logging.warn("No current stream found, cannot fetch song info");
      resetCurrentSong();
      this.nowPlayingService.switchStation(null);
      requestHudUpdate();
      return;
    }
    switchStation(currentStream);
  }

  /**
   * Setzt den aktuellen Song zurück, wenn der Stream gestoppt wird.
   */
  public void resetCurrentSong() {
    this.currentSong.set(null);
    this.currentStreamName.set(null);
    this.currentShortcode.set(null);
    this.connectionState.set(NowPlayingConnectionState.IDLE);
    this.twitchNotificationSent = false;
    this.artworkCache.bumpGeneration();
  }

  /**
   * Legacy-REST-Abruf (Wheel/Menü/On-Air-Badges). Nicht für das Live-HUD.
   */
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
    return currentSong.get();
  }

  public NowPlayingConnectionState getConnectionState() {
    return connectionState.get();
  }

  public String getCurrentShortcode() {
    return currentShortcode.get();
  }

  public ArtworkCache artworkCache() {
    return artworkCache;
  }

  public AzuraCastNowPlayingService nowPlayingService() {
    return nowPlayingService;
  }

  private void requestHudUpdate() {
    if (this.addon.currentSongHudWidget() != null && this.addon.currentSongHudWidget().isEnabled()) {
      this.addon.labyAPI().minecraft().executeOnRenderThread(() ->
          this.addon.currentSongHudWidget().requestUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON)
      );
    }
  }
}
