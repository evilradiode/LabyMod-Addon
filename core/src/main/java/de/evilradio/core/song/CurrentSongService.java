package de.evilradio.core.song;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.song.artwork.ArtworkCache;
import de.evilradio.core.song.azuracast.AzuraCastNowPlayingService;
import de.evilradio.core.song.azuracast.NowPlayingMessageParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.logging.Logging;

public class CurrentSongService {

  private final String API_BASE_URL = "https://api.evil-radio.de/?radioInfo=";
  private static final String AZURACAST_NOWPLAYING_URL =
      "https://broadcast.evil-radio.de/api/nowplaying";

  private final Logging logging = Logging.create("EvilRadio-CurrentSongService");

  private final AtomicReference<CurrentSong> currentSong = new AtomicReference<>();
  private final AtomicReference<CurrentSong> previousSong = new AtomicReference<>();
  private final AtomicReference<NowPlayingConnectionState> connectionState =
      new AtomicReference<>(NowPlayingConnectionState.IDLE);
  private final AtomicReference<String> currentShortcode = new AtomicReference<>();
  private final AtomicReference<String> currentStreamName = new AtomicReference<>();
  private final ArtworkCache artworkCache = new ArtworkCache(32);
  private final AzuraCastNowPlayingService nowPlayingService = new AzuraCastNowPlayingService();
  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(8))
      .build();

  private EvilRadioAddon addon;
  private boolean twitchNotificationSent = false;
  private final AtomicReference<Boolean> pendingStreamSelectedNotification =
      new AtomicReference<>(false);

  public CurrentSongService(EvilRadioAddon addon) {
    this.addon = addon;
    this.nowPlayingService.setSongListener(this::onNowPlayingSong);
    this.nowPlayingService.setPreviousSongListener(this::onPreviousSong);
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
    if (stream == null) {
      logging.warn("Cannot subscribe NowPlaying – missing AzuraCast shortcode because Stream is null.");
      resetCurrentSong();
      this.nowPlayingService.clearSubscription();
      this.addon.requestHudWidgetUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
      return;
    }

    String shortcode = stream.getAzuraCastShortcode();
    String previousShortcode = this.currentShortcode.get();
    boolean stationChanged = previousShortcode != null && !previousShortcode.equals(shortcode);

    this.currentStreamName.set(stream.getName());
    this.currentShortcode.set(shortcode);
    this.artworkCache.bumpGeneration();
    this.currentSong.set(null);
    this.previousSong.set(null);
    this.connectionState.set(NowPlayingConnectionState.LOADING);
    if (stationChanged) {
      this.twitchNotificationSent = false;
    }

    this.addon.requestHudWidgetUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
    this.nowPlayingService.switchStation(shortcode);
  }

  private void onConnectionState(NowPlayingConnectionState state, String shortcode) {
    String active = this.currentShortcode.get();
    if (shortcode != null && active != null && !active.equals(shortcode)) {
      return;
    }
    this.connectionState.set(state == null ? NowPlayingConnectionState.DISCONNECTED : state);
    this.addon.requestHudWidgetUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
  }

  /**
   * Zeigt die nächste gültige WS-Now-Playing-Nachricht als „Sender gewählt“-Toast
   * (statt eines separaten REST-Abrufs).
   */
  public void armStreamSelectedNotification() {
    this.pendingStreamSelectedNotification.set(true);
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
    boolean streamSelectedToast = Boolean.TRUE.equals(
        this.pendingStreamSelectedNotification.getAndSet(false));

    String cacheKey = ArtworkCache.key(activeShortcode, song.getSongId(), song.getImageUrl());
    long artworkGeneration = this.artworkCache.currentGeneration();
    this.artworkCache.put(cacheKey, song.getImageUrl());

    this.currentSong.set(song);
    this.connectionState.set(NowPlayingConnectionState.CONNECTED);
    this.addon.requestHudWidgetUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);

    this.artworkCache.applyIfCurrent(artworkGeneration, song.getImageUrl(), url -> {
      // Cover-URL ist bereits im Snapshot; Generation verhindert spätere Alt-Downloads
    });

    if (streamSelectedToast) {
      if (song.isAdBreak()) {
        // Werbung nicht im Toast – auf nächsten echten Track warten.
        this.pendingStreamSelectedNotification.set(true);
      } else {
        this.pushStreamSelectedNotification(song);
      }
    } else if (songChanged
        && !song.isAdBreak()
        && this.addon.configuration().showSongChangeNotification().get()) {
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

  private void pushStreamSelectedNotification(CurrentSong song) {
    RadioStream stream = this.addon.radioManager().getCurrentStream();
    String stationName = stream != null ? stream.getDisplayName() : song.getStationName();
    if (stationName == null || stationName.isBlank()) {
      stationName = this.currentStreamName.get();
    }
    if (stationName == null) {
      stationName = "";
    }

    Component title = Component.translatable(
        "evilradio.notification.streamSelected.titleWithStation",
        Component.text(stationName)
    );
    String songText = song.getFormatted();
    final Component text;
    final Icon artwork;
    if (songText != null && !songText.isBlank()) {
      text = Component.translatable(
          "evilradio.notification.streamSelected.textWithSong",
          Component.text(songText)
      );
      String imageUrl = song.getImageUrl();
      artwork = imageUrl == null || imageUrl.isBlank() ? null : Icon.url(imageUrl);
    } else {
      text = Component.translatable("evilradio.notification.streamSelected.text");
      artwork = null;
    }

    final Icon streamIcon = stream == null ? null : stream.getIcon();
    this.addon.labyAPI().minecraft().executeOnRenderThread(() ->
        this.addon.notification(title, text, artwork, streamIcon));
  }

  private void onPreviousSong(CurrentSong song) {
    // Werbung nie als „Vorheriger Song“ anzeigen – letzten echten Track behalten.
    if (song != null && song.isAdBreak()) {
      return;
    }
    if (song != null && song.isValid()) {
      String activeShortcode = this.currentShortcode.get();
      if (activeShortcode != null
          && song.getStationShortcode() != null
          && !activeShortcode.equals(song.getStationShortcode())) {
        return;
      }
    }
    this.previousSong.set(song != null && song.isValid() ? song : null);
    this.addon.requestHudWidgetUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
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
      this.nowPlayingService.clearSubscription();
      this.addon.requestHudWidgetUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
      return;
    }
    switchStation(currentStream);
  }

  /**
   * Setzt den aktuellen Song zurück, wenn der Stream gestoppt wird.
   */
  public void resetCurrentSong() {
    this.currentSong.set(null);
    this.previousSong.set(null);
    this.currentStreamName.set(null);
    this.currentShortcode.set(null);
    this.connectionState.set(NowPlayingConnectionState.IDLE);
    this.twitchNotificationSent = false;
    this.pendingStreamSelectedNotification.set(false);
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

  /**
   * Ein Request für Now-Playing aller Stationen (AzuraCast). Key = Station-Shortcode.
   */
  public void fetchAllNowPlaying(Consumer<Map<String, CurrentSong>> callback) {
    if (callback == null) {
      return;
    }
    HttpRequest request = HttpRequest.newBuilder(URI.create(AZURACAST_NOWPLAYING_URL))
        .timeout(Duration.ofSeconds(8))
        .header("User-Agent", "EvilRadio LabyMod 4 Addon")
        .GET()
        .build();

    CompletableFuture.supplyAsync(() -> {
      try {
        HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
          return Map.<String, CurrentSong>of();
        }
        return parseAllNowPlaying(response.body());
      } catch (Exception error) {
        this.logging.warn("Failed to fetch all now-playing – " + error.getMessage());
        return Map.<String, CurrentSong>of();
      }
    }).thenAccept(callback);
  }

  private static Map<String, CurrentSong> parseAllNowPlaying(String body) {
    Map<String, CurrentSong> songs = new HashMap<>();
    JsonElement root = JsonParser.parseString(body);
    if (root == null || !root.isJsonArray()) {
      return songs;
    }
    JsonArray array = root.getAsJsonArray();
    for (JsonElement element : array) {
      if (element == null || !element.isJsonObject()) {
        continue;
      }
      var snapshot = NowPlayingMessageParser.parseNowPlayingSnapshot(element.getAsJsonObject());
      if (snapshot.isEmpty()) {
        continue;
      }
      CurrentSong song = snapshot.get().current();
      if (song == null || !song.isValid()) {
        continue;
      }
      String shortcode = song.getStationShortcode();
      if (shortcode == null || shortcode.isBlank()) {
        continue;
      }
      songs.put(shortcode.trim(), song);
    }
    return songs;
  }

  public CurrentSong getCurrentSong() {
    return currentSong.get();
  }

  public CurrentSong getPreviousSong() {
    return previousSong.get();
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

}
