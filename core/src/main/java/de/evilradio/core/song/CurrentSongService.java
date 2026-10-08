package de.evilradio.core.song;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.evilradio.core.EvilConstants;
import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.song.artwork.ArtworkCache;
import de.evilradio.core.song.azuracast.AzuraCastNowPlayingService;
import de.evilradio.core.song.azuracast.NowPlayingMessageParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.logging.Logging;

public class CurrentSongService {

  /** Mindestabstand zwischen radioInfo-Calls (Song-Spam / Reconnects). */
  private static final long SHOW_STATUS_MIN_INTERVAL_MS = 5_000L;
  /** Soft-Heartbeat für Presence, falls Songs sehr lange laufen. */
  private static final long SHOW_STATUS_HEARTBEAT_MS = 12L * 60L * 1_000L;
  /** Badges nach letztem API-true behalten (Abmoderation nach Sendeplan-Ende). */
  private static final long LIVE_STATUS_GRACE_MS = 10L * 60L * 1_000L;
  private static final ZoneId SHOW_TIME_ZONE = ZoneId.of("Europe/Berlin");
  private static final DateTimeFormatter SHOW_TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

  private final Logging logging = Logging.create("EvilRadio-CurrentSongService");

  private final AtomicReference<CurrentSong> currentSong = new AtomicReference<>();
  private final AtomicReference<CurrentSong> previousSong = new AtomicReference<>();
  /** Letzte Sendungsdaten aus radioInfo – einzige Quelle für OnAir/Twitch/DJ/Sendezeit. */
  private final AtomicReference<CurrentSong.Show> currentShow = new AtomicReference<>(CurrentSong.Show.NONE);
  private final AtomicReference<AzuraCastNowPlayingService.NowPlayingConnectionState> connectionState =
      new AtomicReference<>(AzuraCastNowPlayingService.NowPlayingConnectionState.IDLE);
  private final AtomicReference<String> currentShortcode = new AtomicReference<>();
  private final AtomicReference<String> currentStreamName = new AtomicReference<>();
  private final ArtworkCache artworkCache = new ArtworkCache(32);
  private final AzuraCastNowPlayingService nowPlayingService = new AzuraCastNowPlayingService();
  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(8))
      .build();
  private final AtomicLong lastShowStatusFetchAt = new AtomicLong(0L);
  private final AtomicLong lastStuckRefreshAt = new AtomicLong(0L);
  /** Pro Sender: letztes radioInfo-true für OnAir/Twitch-Grace. */
  private final ConcurrentHashMap<String, LiveGrace> liveGraceByStation = new ConcurrentHashMap<>();

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
    String previousStreamName = this.currentStreamName.get();
    boolean stationChanged = previousShortcode != null && !previousShortcode.equals(shortcode);

    if (stationChanged) {
      this.clearLiveGrace(previousStreamName);
      this.twitchNotificationSent = false;
    }

    this.currentStreamName.set(stream.getName());
    this.currentShortcode.set(shortcode);
    this.artworkCache.bumpGeneration();
    this.currentSong.set(null);
    this.previousSong.set(null);
    this.currentShow.set(CurrentSong.Show.NONE);
    this.connectionState.set(AzuraCastNowPlayingService.NowPlayingConnectionState.LOADING);
    this.lastShowStatusFetchAt.set(0L);
    this.lastStuckRefreshAt.set(0L);

    this.addon.requestHudWidgetUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
    this.nowPlayingService.switchStation(shortcode);
  }

  private void onConnectionState(AzuraCastNowPlayingService.NowPlayingConnectionState state, String shortcode) {
    String active = this.currentShortcode.get();
    if (shortcode != null && active != null && !active.equals(shortcode)) {
      return;
    }
    this.connectionState.set(state == null ? AzuraCastNowPlayingService.NowPlayingConnectionState.DISCONNECTED : state);
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
    if (isStaleRecoveredSong(previous, song)) {
      return;
    }
    boolean songChanged = isSongIdentityChanged(previous, song);
    boolean streamSelectedToast = Boolean.TRUE.equals(
        this.pendingStreamSelectedNotification.getAndSet(false));

    // Sendungsdaten kommen nicht aus dem WS – letzten radioInfo-Stand anhängen.
    song = song.withShow(this.currentShow.get());

    String cacheKey = ArtworkCache.key(activeShortcode, song.getSongId(), song.getImageUrl());
    long artworkGeneration = this.artworkCache.currentGeneration();
    this.artworkCache.put(cacheKey, song.getImageUrl());

    this.currentSong.set(song);
    this.connectionState.set(AzuraCastNowPlayingService.NowPlayingConnectionState.CONNECTED);
    // Während OnAir keinen „Vorherigen Song“ (History oft Live-Müll / Autopilot)
    if (song.isOnAir() && this.previousSong.get() != null) {
      this.previousSong.set(null);
    }
    this.addon.requestHudWidgetUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);

    this.artworkCache.applyIfCurrent(artworkGeneration, song.getImageUrl(), url -> {
      // Cover-URL ist bereits im Snapshot; Generation verhindert spätere Alt-Downloads
    });

    if (!song.isAdBreak() && (songChanged || shouldHeartbeatShowStatus())) {
      this.refreshShowStatusFromApi();
    }

    if (streamSelectedToast) {
      if (song.isAdBreak()) {
        // Werbung nicht im Toast – auf nächsten echten Track warten.
        this.pendingStreamSelectedNotification.set(true);
      } else {
        this.pushStreamSelectedNotification(song);
      }
    } else if (songChanged
        && previous != null
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
      this.addon.sharingController().broadcastCurrentStream();
    }
  }

  /**
   * Nach WS-Reconnect mit Recover-History kann ein bereits beendeter Track als
   * „aktuell“ ankommen. Den nicht über einen noch laufenden Song legen.
   */
  private static boolean isStaleRecoveredSong(CurrentSong current, CurrentSong incoming) {
    if (current == null || !isElapsedToEnd(incoming)) {
      return false;
    }
    return isSongIdentityChanged(current, incoming) && !isElapsedToEnd(current);
  }

  private static boolean isElapsedToEnd(CurrentSong song) {
    return song != null
        && song.hasKnownDuration()
        && song.getCurrentElapsedSeconds() >= song.getDuration();
  }

  /**
   * Wenn der lokale Fortschritt am Ende klebt (typisch nach WS-Recover ohne Live-Pub),
   * aktuellen Song per AzuraCast-REST nachladen – unabhängig vom Picker.
   */
  public void refreshIfStuckAtEnd() {
    if (this.addon.radioManager() == null || !this.addon.radioManager().isPlaying()) {
      return;
    }
    CurrentSong song = this.currentSong.get();
    if (!isElapsedToEnd(song)) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now - this.lastStuckRefreshAt.get() < 5_000L) {
      return;
    }
    this.lastStuckRefreshAt.set(now);

    final String expectedShortcode = this.currentShortcode.get();
    if (expectedShortcode == null || expectedShortcode.isBlank()) {
      return;
    }
    this.fetchAllNowPlaying(songs -> {
      if (songs == null || songs.isEmpty()) {
        return;
      }
      CurrentSong fresh = songs.get(expectedShortcode);
      if (fresh == null) {
        for (Map.Entry<String, CurrentSong> entry : songs.entrySet()) {
          if (expectedShortcode.equalsIgnoreCase(entry.getKey())) {
            fresh = entry.getValue();
            break;
          }
        }
      }
      if (fresh == null || !fresh.isValid()) {
        return;
      }
      CurrentSong apply = fresh;
      this.addon.labyAPI().minecraft().executeOnRenderThread(() -> this.onNowPlayingSong(apply));
    });
  }

  private static boolean isSongIdentityChanged(CurrentSong previous, CurrentSong next) {
    if (previous == null || next == null) {
      return true;
    }
    if (previous.getSongId() != null && !previous.getSongId().isBlank()
        && next.getSongId() != null && !next.getSongId().isBlank()) {
      return !previous.getSongId().equals(next.getSongId());
    }
    return !Objects.equals(previous.getTitle(), next.getTitle())
        || !Objects.equals(previous.getArtist(), next.getArtist());
  }

  private boolean shouldHeartbeatShowStatus() {
    long last = this.lastShowStatusFetchAt.get();
    return last <= 0L || System.currentTimeMillis() - last >= SHOW_STATUS_HEARTBEAT_MS;
  }

  /**
   * Lädt OnAir/Twitch von der Evil-Radio-API (Presence inkl. uuid + User-Agent/Version).
   */
  private void refreshShowStatusFromApi() {
    String streamName = this.currentStreamName.get();
    if (streamName == null || streamName.isBlank()) {
      RadioStream stream = this.addon.radioManager() == null
          ? null
          : this.addon.radioManager().getCurrentStream();
      streamName = stream == null ? null : stream.getName();
    }
    if (streamName == null || streamName.isBlank()) {
      return;
    }

    long now = System.currentTimeMillis();
    long last = this.lastShowStatusFetchAt.get();
    if (last > 0L && now - last < SHOW_STATUS_MIN_INTERVAL_MS) {
      return;
    }
    this.lastShowStatusFetchAt.set(now);

    final String station = streamName;
    final String expectedShortcode = this.currentShortcode.get();
    fetchShowStatus(station, show -> {
      if (show == null) {
        return;
      }
      String activeShortcode = this.currentShortcode.get();
      if (expectedShortcode != null && activeShortcode != null
          && !expectedShortcode.equals(activeShortcode)) {
        return;
      }
      applyShowStatus(show);
    });
  }

  private void applyShowStatus(ShowStatus show) {
    ShowStatus effective = this.applyLiveGrace(this.currentStreamName.get(), show);
    CurrentSong.Show info = toShow(effective);
    CurrentSong.Show previousInfo = this.currentShow.getAndSet(info);

    boolean changed = false;
    // Während OnAir bzw. direkt nach Sendungsende keinen „Vorherigen Song“ (Live-Müll/Autopilot)
    if ((info.onAir() || previousInfo.onAir()) && this.previousSong.getAndSet(null) != null) {
      changed = true;
    }

    CurrentSong current = this.currentSong.get();
    if (current != null && !info.equals(current.getShow())) {
      // updateAndGet: falls zwischenzeitlich ein neuer WS-Song kam, auf diesen anwenden
      this.currentSong.updateAndGet(song -> song == null ? null : song.withShow(info));
      changed = true;
    }

    if (changed) {
      this.addon.requestHudWidgetUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
    }
  }

  /**
   * Übernimmt Live-Flags, DJ-Namen, Wunschbox und ggf. Sendezeit (start/end) aus radioInfo.
   */
  public CurrentSong applyShowToSong(CurrentSong song, ShowStatus show) {
    if (song == null || show == null) {
      return song;
    }
    return song.withShow(toShow(show));
  }

  private static CurrentSong.Show toShow(ShowStatus show) {
    if (show == null) {
      return CurrentSong.Show.NONE;
    }
    // Sendezeit (z. B. 14:00–16:00) nur bei Live – Azura-Trackdauer ist dann oft 0.
    boolean window = show.onAir() && show.hasShowWindow();
    return new CurrentSong.Show(
        show.onAir(),
        show.twitch(),
        show.stream(),
        show.name(),
        show.pictureUrl(),
        show.moderatorName(),
        show.moderatorImage(),
        show.startHHmm(),
        show.endHHmm(),
        show.event(),
        show.wishBoxEnabled(),
        window ? show.showPlayedAtSec() : 0L,
        window ? show.showDurationSec() : 0L,
        window ? CurrentSong.formatShowClock(show.startHHmm(), show.endHHmm()) : null);
  }

  /**
   * Hält OnAir/Twitch nach Sendeschluss noch bis max. {@link #LIVE_STATUS_GRACE_MS} nach
   * geplanter Endzeit (Abmoderation). Wenn die API die Sendung klar beendet hat
   * ({@code start}/{@code end} = NONE), sofort löschen.
   */
  private ShowStatus applyLiveGrace(String streamName, ShowStatus raw) {
    if (raw == null) {
      return null;
    }
    String key = normalizeStationKey(streamName);
    if (key == null) {
      return raw;
    }

    if (raw.onAir() || raw.twitch()) {
      this.liveGraceByStation.put(key, new LiveGrace(
          System.currentTimeMillis(),
          raw.onAir(),
          raw.twitch(),
          raw.showPlayedAtSec(),
          raw.showDurationSec(),
          raw.startHHmm(),
          raw.endHHmm()));
      return raw;
    }

    // Server meldet klar „keine Sendung“ → Badges sofort weg (nicht 10 Min Grace).
    if (!raw.hasShowSchedule()) {
      this.liveGraceByStation.remove(key);
      return raw;
    }

    LiveGrace grace = this.liveGraceByStation.get(key);
    if (grace != null && (grace.onAir || grace.twitch)) {
      long graceUntilMs = graceGraceUntilMs(grace);
      if (System.currentTimeMillis() < graceUntilMs) {
        return raw.withLiveWindow(
            grace.onAir,
            grace.twitch,
            grace.showPlayedAtSec,
            grace.showDurationSec,
            grace.startHHmm,
            grace.endHHmm);
      }
    }

    this.liveGraceByStation.remove(key);
    return raw;
  }

  /** Ende der Abmoderations-Grace: geplantes Sendungsende + 10 Min, sonst lastTrue + 10 Min. */
  private static long graceGraceUntilMs(LiveGrace grace) {
    if (grace.showPlayedAtSec > 0L && grace.showDurationSec > 0L) {
      return (grace.showPlayedAtSec + grace.showDurationSec) * 1000L + LIVE_STATUS_GRACE_MS;
    }
    return grace.lastTrueAt + LIVE_STATUS_GRACE_MS;
  }

  private void clearLiveGrace(String streamName) {
    String key = normalizeStationKey(streamName);
    if (key != null) {
      this.liveGraceByStation.remove(key);
    }
  }

  private static String normalizeStationKey(String streamName) {
    if (streamName == null || streamName.isBlank()) {
      return null;
    }
    return streamName.trim().toLowerCase(Locale.ROOT);
  }

  private record LiveGrace(
      long lastTrueAt,
      boolean onAir,
      boolean twitch,
      long showPlayedAtSec,
      long showDurationSec,
      String startHHmm,
      String endHHmm
  ) {
  }

  private void fetchShowStatus(String streamName, Consumer<ShowStatus> callback) {
    if (streamName == null || streamName.isBlank()) {
      callback.accept(null);
      return;
    }
    String uuid = this.addon.labyAPI().getUniqueId().toString();
    String url = EvilConstants.API_RADIO_INFO
        + URLEncoder.encode(streamName, StandardCharsets.UTF_8)
        + "&uuid="
        + URLEncoder.encode(uuid, StandardCharsets.UTF_8);

    Request.ofGson(JsonObject.class)
        .url(url)
        .async()
        .connectTimeout(5000)
        .readTimeout(5000)
        .userAgent(this.addon.apiUserAgent())
        .addHeader("X-Addon-Version", this.addon.addonVersion())
        .addHeader("X-Minecraft-Version", this.addon.labyAPI().minecraft().getVersion())
        .addHeader("X-Used-Theme", this.addon.labyAPI().config().appearance().theme().get())
        .addHeader("X-Used-Widget-Theme", this.addon.currentSongHudWidget().getConfig().hudWidgetDesign().get().name())
        .execute(response -> {
          if (response.getStatusCode() != 200 || response.hasException()) {
            callback.accept(null);
            return;
          }
          callback.accept(parseShowStatus(response.get()));
        });
  }

  /**
   * OnAir/Twitch aus {@code radioInfo} ({@code show}), ohne Abhängigkeit vom {@code current}-Song-Block.
   * Callback {@code null} bei Netzwerk-/Parse-Fehler. Grace gilt auch hier (Picker).
   */
  public void fetchLiveFlags(String streamName, Consumer<ShowStatus> callback) {
    this.fetchShowStatus(streamName, raw -> {
      if (raw == null) {
        callback.accept(null);
        return;
      }
      callback.accept(this.applyLiveGrace(streamName, raw));
    });
  }

  private static ShowStatus parseShowStatus(JsonObject object) {
    JsonObject showObject = object.has("show") && object.get("show").isJsonObject()
        ? object.get("show").getAsJsonObject()
        : new JsonObject();
    String start = showString(showObject, "start");
    String end = showString(showObject, "end");
    long[] window = resolveShowWindow(start, end);
    return new ShowStatus(
        showBoolean(showObject, "live"),
        showBoolean(showObject, "twitch"),
        showString(showObject, "stream"),
        showString(showObject, "name"),
        showString(showObject, "picture"),
        showString(showObject, "dj"),
        showString(showObject, "djImage"),
        start,
        end,
        showBoolean(showObject, "event"),
        showBoolean(showObject, "wishBox"),
        window[0],
        window[1]);
  }

  private static String showString(JsonObject showObject, String key) {
    if (!showObject.has(key) || !showObject.get(key).isJsonPrimitive()) {
      return null;
    }
    return normalizeShowField(showObject.get(key).getAsString());
  }

  private static boolean showBoolean(JsonObject showObject, String key) {
    return showObject.has(key)
        && showObject.get(key).isJsonPrimitive()
        && showObject.get(key).getAsBoolean();
  }

  private static String normalizeShowField(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.equalsIgnoreCase("NONE") || trimmed.equals("-") || trimmed.equalsIgnoreCase("null")) {
      return null;
    }
    return trimmed;
  }

  /**
   * @return {@code [playedAtUnixSec, durationSec]} oder {@code [0, 0]} wenn unbekannt
   */
  private static long[] resolveShowWindow(String startHHmm, String endHHmm) {
    if (startHHmm == null || startHHmm.isBlank() || endHHmm == null || endHHmm.isBlank()) {
      return new long[]{0L, 0L};
    }
    try {
      LocalTime start = LocalTime.parse(startHHmm.trim(), SHOW_TIME_FORMAT);
      LocalTime end = LocalTime.parse(endHHmm.trim(), SHOW_TIME_FORMAT);
      ZonedDateTime now = ZonedDateTime.now(SHOW_TIME_ZONE);
      LocalDate day = now.toLocalDate();
      ZonedDateTime startDt = ZonedDateTime.of(day, start, SHOW_TIME_ZONE);
      ZonedDateTime endDt = ZonedDateTime.of(day, end, SHOW_TIME_ZONE);
      if (!endDt.isAfter(startDt)) {
        endDt = endDt.plusDays(1);
      }
      // Über Mitternacht: Sendung gestern gestartet
      if (now.isBefore(startDt)) {
        ZonedDateTime startYesterday = startDt.minusDays(1);
        ZonedDateTime endYesterday = endDt.minusDays(1);
        if (!now.isBefore(startYesterday) && now.isBefore(endYesterday)) {
          startDt = startYesterday;
          endDt = endYesterday;
        }
      }
      long durationSec = Duration.between(startDt, endDt).getSeconds();
      if (durationSec <= 0L) {
        return new long[]{0L, 0L};
      }
      return new long[]{startDt.toEpochSecond(), durationSec};
    } catch (DateTimeParseException | ArithmeticException ignored) {
      return new long[]{0L, 0L};
    }
  }

  public record ShowStatus(
      boolean onAir,
      boolean twitch,
      String stream,
      String name,
      String pictureUrl,
      String moderatorName,
      String moderatorImage,
      String startHHmm,
      String endHHmm,
      boolean event,
      boolean wishBoxEnabled,
      long showPlayedAtSec,
      long showDurationSec
  ) {
    /** Kopie mit Live-Flags und Sendezeit aus der Grace (Rest der Daten bleibt aktuell). */
    ShowStatus withLiveWindow(
        boolean onAir, boolean twitch, long playedAtSec, long durationSec, String startHHmm, String endHHmm) {
      return new ShowStatus(
          onAir, twitch, this.stream, this.name, this.pictureUrl, this.moderatorName, this.moderatorImage,
          startHHmm, endHHmm, this.event, this.wishBoxEnabled, playedAtSec, durationSec);
    }

    public boolean hasShowWindow() {
      return this.showPlayedAtSec > 0L && this.showDurationSec > 0L;
    }

    /** False wenn die API keine Sendungszeiten mehr liefert (z. B. start/end = NONE). */
    public boolean hasShowSchedule() {
      return (this.startHHmm != null && !this.startHHmm.isBlank())
          || (this.endHHmm != null && !this.endHHmm.isBlank());
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
    // Werbung: letzten echten Track behalten
    if (song != null && song.isAdBreak()) {
      return;
    }
    // Während Live-Sendung keinen „Vorherigen Song“ zeigen
    CurrentSong current = this.currentSong.get();
    if (current != null && current.isOnAir()) {
      if (this.previousSong.get() != null) {
        this.previousSong.set(null);
        this.addon.requestHudWidgetUpdate(CurrentSongHudWidget.SONG_CHANGE_REASON);
      }
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
    // Live-Tag-/Autopilot-Müll nach Sendungsende → leer, bis wieder ein echter Track kommt
    CurrentSong next = (song != null && song.isUsableAsPreviousSong()) ? song : null;
    if (Objects.equals(this.previousSong.get(), next)) {
      return;
    }
    this.previousSong.set(next);
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
    return new CurrentSong(title, artist, image).withShow(toShow(parseShowStatus(object)));
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
    this.clearLiveGrace(this.currentStreamName.get());
    this.currentSong.set(null);
    this.previousSong.set(null);
    this.currentShow.set(CurrentSong.Show.NONE);
    this.currentStreamName.set(null);
    this.currentShortcode.set(null);
    this.connectionState.set(AzuraCastNowPlayingService.NowPlayingConnectionState.IDLE);
    this.twitchNotificationSent = false;
    this.pendingStreamSelectedNotification.set(false);
    this.lastShowStatusFetchAt.set(0L);
    this.lastStuckRefreshAt.set(0L);
    this.artworkCache.bumpGeneration();
  }

  /**
   * Legacy-REST-Abruf (Menü/On-Air-Badges). Nicht für das Live-HUD.
   */
  public void fetchCurrentSong(String streamName, Consumer<CurrentSong> callback) {
    if (streamName == null || streamName.isEmpty()) {
      callback.accept(null);
      return;
    }
    String uuid = this.addon.labyAPI().getUniqueId().toString();
    String url = EvilConstants.API_RADIO_INFO
        + URLEncoder.encode(streamName, StandardCharsets.UTF_8)
        + "&uuid="
        + URLEncoder.encode(uuid, StandardCharsets.UTF_8);
    Request.ofGson(JsonObject.class)
        .url(url)
        .async()
        .connectTimeout(5000)
        .readTimeout(5000)
        .userAgent(this.addon.apiUserAgent())
        .addHeader("X-Addon-Version", this.addon.addonVersion())
        .addHeader("X-Minecraft-Version", this.addon.labyAPI().minecraft().getVersion())
        .addHeader("X-Used-Theme", this.addon.labyAPI().config().appearance().theme().get())
        .addHeader("X-Used-Widget-Theme", this.addon.currentSongHudWidget().getConfig().hudWidgetDesign().get().name())
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
    if (callback == null) return;
    HttpRequest request = HttpRequest.newBuilder(URI.create(EvilConstants.AZURACAST_NOWPLAYING_URL))
        .timeout(Duration.ofSeconds(8))
        .header("User-Agent", this.addon.apiUserAgent())
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
    if (root == null || !root.isJsonArray()) return songs;
    JsonArray array = root.getAsJsonArray();
    for (JsonElement element : array) {
      if (element == null || !element.isJsonObject()) continue;
      var snapshot = NowPlayingMessageParser.parseNowPlayingSnapshot(element.getAsJsonObject());
      if (snapshot.isEmpty()) continue;
      CurrentSong song = snapshot.get().current();
      if (song == null || !song.isValid()) continue;
      String shortcode = song.getStationShortcode();
      if (shortcode == null || shortcode.isBlank()) continue;
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

  public AzuraCastNowPlayingService.NowPlayingConnectionState getConnectionState() {
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
