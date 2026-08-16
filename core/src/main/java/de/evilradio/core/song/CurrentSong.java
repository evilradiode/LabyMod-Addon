package de.evilradio.core.song;

import net.labymod.api.util.I18n;

/**
 * Unveränderlicher Snapshot der Now-Playing-Daten eines Senders.
 */
public final class CurrentSong {

  private static final String AD_BREAK_MARKER = "START_AD_BREAK";

  private final int stationId;
  private final String stationName;
  private final String stationShortcode;
  private final String title;
  private final String artist;
  private final String imageUrl;
  private final String songId;
  private final String moderatorName;
  private final boolean onAir;
  private final boolean twitch;
  private final long playedAt;
  private final long duration;
  private final long elapsedAtUpdate;
  private final long receivedAt;
  private final boolean adBreak;
  /** Bei Live-Sendung z. B. {@code 14:00–16:00}, sonst {@code null}. */
  private final String liveClockLabel;

  public CurrentSong(String title, String artist, String imageUrl) {
    this(0, null, null, title, artist, imageUrl, null, null, false, false, 0L, 0L, 0L,
        System.currentTimeMillis(), false, null);
  }

  public CurrentSong(String title, String artist, String imageUrl, String moderatorName, boolean onAir,
      boolean twitch) {
    this(0, null, null, title, artist, imageUrl, null, moderatorName, onAir, twitch, 0L, 0L, 0L,
        System.currentTimeMillis(), false, null);
  }

  public CurrentSong(
      int stationId,
      String stationName,
      String stationShortcode,
      String title,
      String artist,
      String imageUrl,
      String songId,
      String moderatorName,
      boolean onAir,
      boolean twitch,
      long playedAt,
      long duration,
      long elapsedAtUpdate,
      long receivedAt
  ) {
    String rawTitle = title == null ? "" : title;
    String rawArtist = artist == null ? "" : artist;
    boolean ad = isAdBreakMarker(rawTitle) || isAdBreakMarker(rawArtist);
    this.stationId = stationId;
    this.stationName = stationName;
    this.stationShortcode = stationShortcode;
    this.adBreak = ad;
    if (ad) {
      this.title = I18n.translate("evilradio.widget.adBreakTitle");
      this.artist = I18n.translate("evilradio.widget.adBreakArtist");
    } else {
      this.title = rawTitle;
      this.artist = rawArtist;
    }
    this.imageUrl = imageUrl;
    this.songId = songId;
    this.moderatorName = moderatorName;
    this.onAir = onAir;
    this.twitch = twitch;
    this.playedAt = playedAt;
    this.duration = Math.max(0L, duration);
    this.elapsedAtUpdate = Math.max(0L, elapsedAtUpdate);
    this.receivedAt = receivedAt <= 0L ? System.currentTimeMillis() : receivedAt;
    this.liveClockLabel = null;
  }

  private CurrentSong(
      int stationId,
      String stationName,
      String stationShortcode,
      String title,
      String artist,
      String imageUrl,
      String songId,
      String moderatorName,
      boolean onAir,
      boolean twitch,
      long playedAt,
      long duration,
      long elapsedAtUpdate,
      long receivedAt,
      boolean adBreak,
      String liveClockLabel
  ) {
    this.stationId = stationId;
    this.stationName = stationName;
    this.stationShortcode = stationShortcode;
    this.title = title == null ? "" : title;
    this.artist = artist == null ? "" : artist;
    this.imageUrl = imageUrl;
    this.songId = songId;
    this.moderatorName = moderatorName;
    this.onAir = onAir;
    this.twitch = twitch;
    this.playedAt = playedAt;
    this.duration = Math.max(0L, duration);
    this.elapsedAtUpdate = Math.max(0L, elapsedAtUpdate);
    this.receivedAt = receivedAt <= 0L ? System.currentTimeMillis() : receivedAt;
    this.adBreak = adBreak;
    this.liveClockLabel = liveClockLabel == null || liveClockLabel.isBlank() ? null : liveClockLabel.trim();
  }

  private static boolean isAdBreakMarker(String value) {
    return value != null && value.trim().equalsIgnoreCase(AD_BREAK_MARKER);
  }

  public int getStationId() {
    return stationId;
  }

  public String getStationName() {
    return stationName;
  }

  public String getStationShortcode() {
    return stationShortcode;
  }

  public String getTitle() {
    return title;
  }

  /**
   * Titel ohne angehängtes Live-Tag ({@code *ON Air Euer | Moderator}), das oft
   * schon in Zeile 1 (On Air / Mod) steht.
   */
  public String getDisplayTitle() {
    return stripLiveShowSuffix(this.title);
  }

  public String getArtist() {
    return artist;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public String getSongId() {
    return songId;
  }

  public boolean isOnAir() {
    return onAir;
  }

  public String getModeratorName() {
    return moderatorName;
  }

  public boolean isTwitch() {
    return twitch;
  }

  public long getPlayedAt() {
    return playedAt;
  }

  public long getDuration() {
    return duration;
  }

  public long getElapsedAtUpdate() {
    return elapsedAtUpdate;
  }

  public long getReceivedAt() {
    return receivedAt;
  }

  public String getLiveClockLabel() {
    return liveClockLabel;
  }

  public boolean isValid() {
    return title != null && !title.isBlank();
  }

  public boolean isAdBreak() {
    return adBreak;
  }

  public boolean hasKnownDuration() {
    return duration > 0L;
  }

  /**
   * Playtime-Text: bei Live-Sendung Uhrzeit ({@code 14:00–16:00}), sonst {@code m:ss / m:ss}.
   */
  public String getPlaytimeLabel() {
    if (this.liveClockLabel != null) {
      return this.liveClockLabel;
    }
    if (!hasKnownDuration()) {
      return null;
    }
    return formatTime(getCurrentElapsedSeconds()) + " / " + formatTime(this.duration);
  }

  /**
   * Aktuell verstrichene Sekunden, bevorzugt über {@code played_at} (absolut),
   * damit HUD und Picker trotz unterschiedlicher Update-Zeitpunkte gleich laufen.
   */
  public long getCurrentElapsedSeconds() {
    long elapsed = this.elapsedFromPlayedAt();
    if (elapsed < 0L) {
      elapsed = this.elapsedAtUpdate
          + Math.max(0L, (System.currentTimeMillis() - this.receivedAt) / 1000L);
    }
    if (elapsed < 0L) {
      return 0L;
    }
    if (hasKnownDuration()) {
      return Math.min(this.duration, elapsed);
    }
    return elapsed;
  }

  /**
   * @return verstrichene Sekunden ab {@code played_at}, oder {@code -1} wenn unbekannt
   */
  private long elapsedFromPlayedAt() {
    if (this.playedAt >= 1_000_000_000_000L) {
      // Unix-Millis
      return (System.currentTimeMillis() - this.playedAt) / 1000L;
    }
    if (this.playedAt >= 1_000_000_000L) {
      // Unix-Sekunden
      return (System.currentTimeMillis() / 1000L) - this.playedAt;
    }
    return -1L;
  }

  /**
   * Fortschritt von 0.0 bis 1.0, oder {@code -1} wenn keine Dauer bekannt ist.
   */
  public double getProgress() {
    if (!hasKnownDuration()) {
      return -1.0d;
    }
    return Math.clamp((double) getCurrentElapsedSeconds() / (double) duration, 0.0d, 1.0d);
  }

  public CurrentSong withTwitch(boolean twitchLive) {
    return withLiveStatus(onAir, twitchLive);
  }

  /**
   * OnAir/Twitch aus der Evil-Radio-API (nicht aus AzuraCast-WS).
   */
  public CurrentSong withLiveStatus(boolean onAirLive, boolean twitchLive) {
    return copy(
        stationId, stationName, stationShortcode, title, artist, imageUrl, songId, moderatorName,
        onAirLive, twitchLive, playedAt, duration, elapsedAtUpdate, receivedAt, adBreak, liveClockLabel);
  }

  public CurrentSong withModeratorName(String name) {
    return copy(
        stationId, stationName, stationShortcode, title, artist, imageUrl, songId, name,
        onAir, twitch, playedAt, duration, elapsedAtUpdate, receivedAt, adBreak, liveClockLabel);
  }

  /**
   * Fortschritt/Playtime (z. B. Sendezeit aus radioInfo {@code show.start}/{@code show.end}).
   * {@code playedAt} in Unix-Sekunden oder -Millis (wie AzuraCast).
   */
  public CurrentSong withTiming(long playedAtEpoch, long durationSeconds) {
    return copy(
        stationId, stationName, stationShortcode, title, artist, imageUrl, songId, moderatorName,
        onAir, twitch, playedAtEpoch, durationSeconds, 0L, System.currentTimeMillis(), adBreak,
        liveClockLabel);
  }

  /**
   * Live-Sendungsfenster inkl. Uhrzeit-Label ({@code 14:00–16:00}).
   */
  public CurrentSong withShowWindow(long playedAtEpoch, long durationSeconds, String clockLabel) {
    return copy(
        stationId, stationName, stationShortcode, title, artist, imageUrl, songId, moderatorName,
        onAir, twitch, playedAtEpoch, durationSeconds, 0L, System.currentTimeMillis(), adBreak,
        clockLabel);
  }

  public CurrentSong withShowWindow(
      long playedAtEpoch, long durationSeconds, String startHHmm, String endHHmm) {
    return withShowWindow(playedAtEpoch, durationSeconds, formatShowClock(startHHmm, endHHmm));
  }

  public CurrentSong withStationShortcode(String shortcode) {
    return copy(
        stationId, stationName, shortcode, title, artist, imageUrl, songId, moderatorName,
        onAir, twitch, playedAt, duration, elapsedAtUpdate, receivedAt, adBreak, liveClockLabel);
  }

  private static CurrentSong copy(
      int stationId,
      String stationName,
      String stationShortcode,
      String title,
      String artist,
      String imageUrl,
      String songId,
      String moderatorName,
      boolean onAir,
      boolean twitch,
      long playedAt,
      long duration,
      long elapsedAtUpdate,
      long receivedAt,
      boolean adBreak,
      String liveClockLabel
  ) {
    return new CurrentSong(
        stationId, stationName, stationShortcode, title, artist, imageUrl, songId, moderatorName,
        onAir, twitch, playedAt, duration, elapsedAtUpdate, receivedAt, adBreak, liveClockLabel);
  }

  public String getFormatted() {
    String displayTitle = getDisplayTitle();
    if (artist == null || artist.isBlank()) {
      return displayTitle;
    }
    return String.format("%s - %s", displayTitle, artist);
  }

  /**
   * Entfernt Suffixe wie {@code *ON Air Euer | Derbestetv} aus AzuraCast-Titeln.
   * Nur-Live-Tags werden zu einem leeren String (kein Fallback auf den Roh-Titel).
   */
  static String stripLiveShowSuffix(String title) {
    if (title == null || title.isBlank()) {
      return title == null ? "" : title;
    }
    String cleaned = title.replaceAll("(?i)\\s*\\*?\\s*ON\\s*Air\\b.*$", "").trim();
    cleaned = cleaned.replaceAll("\\s*[|–-]\\s*$", "").trim();
    cleaned = cleaned.replaceAll("^\\*+$", "").trim();
    return cleaned;
  }

  /**
   * Echter vorheriger Track (kein Live-Tag-Müll, keine Werbung, kein Autopilot-Platzhalter).
   */
  public boolean isUsableAsPreviousSong() {
    if (!isValid() || isAdBreak()) {
      return false;
    }
    String display = stripLiveShowSuffix(this.title);
    if (display.isBlank() || display.equals("*")) {
      return false;
    }
    String artistName = this.artist == null ? "" : this.artist.trim();
    if (artistName.toLowerCase(java.util.Locale.ROOT).contains("autopilot") && display.length() < 4) {
      return false;
    }
    return true;
  }

  public static String formatShowClock(String startHHmm, String endHHmm) {
    if (startHHmm == null || startHHmm.isBlank() || endHHmm == null || endHHmm.isBlank()) {
      return null;
    }
    return startHHmm.trim() + "–" + endHHmm.trim() + " Uhr";
  }

  public static String formatTime(long totalSeconds) {
    long seconds = Math.max(0L, totalSeconds);
    long hours = seconds / 3600L;
    long minutes = (seconds % 3600L) / 60L;
    long rem = seconds % 60L;
    if (hours > 0L) {
      return String.format("%d:%02d:%02d", hours, minutes, rem);
    }
    return String.format("%d:%02d", minutes, rem);
  }
}
