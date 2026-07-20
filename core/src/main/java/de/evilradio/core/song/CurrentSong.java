package de.evilradio.core.song;

import net.labymod.api.Laby;

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

  public CurrentSong(String title, String artist, String imageUrl) {
    this(0, null, null, title, artist, imageUrl, null, null, false, false, 0L, 0L, 0L, System.currentTimeMillis());
  }

  public CurrentSong(String title, String artist, String imageUrl, String moderatorName, boolean onAir, boolean twitch) {
    this(0, null, null, title, artist, imageUrl, null, moderatorName, onAir, twitch, 0L, 0L, 0L, System.currentTimeMillis());
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
    this.stationId = stationId;
    this.stationName = stationName;
    this.stationShortcode = stationShortcode;
    String rawTitle = title == null ? "" : title;
    String rawArtist = artist == null ? "" : artist;
    this.adBreak = isAdBreakMarker(rawTitle) || isAdBreakMarker(rawArtist);
    if (this.adBreak) {
      this.title = translateOrDefault("evilradio.widget.adBreakTitle", "Jetzt läuft");
      this.artist = translateOrDefault("evilradio.widget.adBreakArtist", "Werbung");
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
      boolean adBreak
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
  }

  private static boolean isAdBreakMarker(String value) {
    return value != null && value.trim().equalsIgnoreCase(AD_BREAK_MARKER);
  }

  private static String translateOrDefault(String key, String fallback) {
    try {
      String translated = Laby.labyAPI().internationalization().getTranslation(key);
      if (translated != null && !translated.isBlank() && !translated.equals(key)) {
        return translated;
      }
    } catch (Throwable ignored) {
      // API ggf. noch nicht bereit
    }
    return fallback;
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
   * Aktuell verstrichene Sekunden seit dem letzten Update, begrenzt auf {@code [0, duration]}.
   */
  public long getCurrentElapsedSeconds() {
    long elapsed = elapsedAtUpdate + Math.max(0L, (System.currentTimeMillis() - receivedAt) / 1000L);
    if (elapsed < 0L) {
      return 0L;
    }
    if (hasKnownDuration()) {
      return Math.min(duration, elapsed);
    }
    return elapsed;
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
    return new CurrentSong(
        stationId,
        stationName,
        stationShortcode,
        title,
        artist,
        imageUrl,
        songId,
        moderatorName,
        onAir,
        twitchLive,
        playedAt,
        duration,
        elapsedAtUpdate,
        receivedAt,
        adBreak
    );
  }

  public CurrentSong withStationShortcode(String shortcode) {
    return new CurrentSong(
        stationId,
        stationName,
        shortcode,
        title,
        artist,
        imageUrl,
        songId,
        moderatorName,
        onAir,
        twitch,
        playedAt,
        duration,
        elapsedAtUpdate,
        receivedAt,
        adBreak
    );
  }

  public String getFormatted() {
    if (artist == null || artist.isBlank()) {
      return title;
    }
    return String.format("%s - %s", title, artist);
  }

  public static String formatTime(long totalSeconds) {
    long seconds = Math.max(0L, totalSeconds);
    long minutes = seconds / 60L;
    long rem = seconds % 60L;
    return String.format("%d:%02d", minutes, rem);
  }
}
