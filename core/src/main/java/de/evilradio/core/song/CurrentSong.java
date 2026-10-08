package de.evilradio.core.song;

import net.labymod.api.util.I18n;

/**
 * Unveränderlicher Snapshot der Now-Playing-Daten eines Senders.
 * <p>
 * Track-Daten kommen aus AzuraCast (WebSocket/REST), Sendungsdaten ({@link Show}) ausschließlich
 * aus der Evil-Radio-REST-API ({@code radioInfo}).
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
  private final long playedAt;
  private final long duration;
  private final long elapsedAtUpdate;
  private final long receivedAt;
  private final boolean adBreak;
  private final Show show;

  public CurrentSong(String title, String artist, String imageUrl) {
    this(0, null, null, title, artist, imageUrl, null, 0L, 0L, 0L, System.currentTimeMillis());
  }

  public CurrentSong(
      int stationId,
      String stationName,
      String stationShortcode,
      String title,
      String artist,
      String imageUrl,
      String songId,
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
    this.playedAt = playedAt;
    this.duration = Math.max(0L, duration);
    this.elapsedAtUpdate = Math.max(0L, elapsedAtUpdate);
    this.receivedAt = receivedAt <= 0L ? System.currentTimeMillis() : receivedAt;
    this.show = Show.NONE;
  }

  private CurrentSong(CurrentSong source, String stationShortcode, Show show) {
    this.stationId = source.stationId;
    this.stationName = source.stationName;
    this.stationShortcode = stationShortcode;
    this.title = source.title;
    this.artist = source.artist;
    this.imageUrl = source.imageUrl;
    this.songId = source.songId;
    this.playedAt = source.playedAt;
    this.duration = source.duration;
    this.elapsedAtUpdate = source.elapsedAtUpdate;
    this.receivedAt = source.receivedAt;
    this.adBreak = source.adBreak;
    this.show = show == null ? Show.NONE : show;
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

  public Show getShow() {
    return show;
  }

  public boolean isOnAir() {
    return show.onAir();
  }

  public String getModeratorName() {
    return show.moderatorName();
  }

  public boolean isWishBoxEnabled() {
    return show.wishBoxEnabled();
  }

  public boolean isTwitch() {
    return show.twitch();
  }

  public String getShowName() {
    return show.name();
  }

  public String getShowPictureUrl() {
    return show.pictureUrl();
  }

  public String getModeratorImageUrl() {
    return show.moderatorImage();
  }

  public boolean isEvent() {
    return show.event();
  }

  /**
   * Startzeit (Unix-Sekunden/-Millis): bei Live-Sendung mit Sendefenster der Sendungsbeginn,
   * sonst der Track-Start aus AzuraCast.
   */
  public long getPlayedAt() {
    return hasLiveShowWindow() ? show.playedAtSec() : playedAt;
  }

  /**
   * Dauer in Sekunden: bei Live-Sendung mit Sendefenster die Sendungsdauer, sonst die Track-Dauer.
   */
  public long getDuration() {
    return hasLiveShowWindow() ? show.durationSec() : duration;
  }

  public long getElapsedAtUpdate() {
    return elapsedAtUpdate;
  }

  public long getReceivedAt() {
    return receivedAt;
  }

  /** Bei Live-Sendung z. B. {@code 14:00–16:00 Uhr}, sonst {@code null}. */
  public String getLiveClockLabel() {
    return show.onAir() ? show.clockLabel() : null;
  }

  private boolean hasLiveShowWindow() {
    return show.onAir() && show.hasWindow();
  }

  public boolean isValid() {
    return title != null && !title.isBlank();
  }

  public boolean isAdBreak() {
    return adBreak;
  }

  public boolean hasKnownDuration() {
    return getDuration() > 0L;
  }

  /**
   * Playtime-Text: bei Live-Sendung Uhrzeit ({@code 14:00–16:00}), sonst {@code m:ss / m:ss}.
   */
  public String getPlaytimeLabel() {
    String clockLabel = getLiveClockLabel();
    if (clockLabel != null) {
      return clockLabel;
    }
    if (!hasKnownDuration()) {
      return null;
    }
    return formatTime(getCurrentElapsedSeconds()) + " / " + formatTime(getDuration());
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
      return Math.min(getDuration(), elapsed);
    }
    return elapsed;
  }

  /**
   * @return verstrichene Sekunden ab {@code played_at}, oder {@code -1} wenn unbekannt
   */
  private long elapsedFromPlayedAt() {
    long start = getPlayedAt();
    if (start >= 1_000_000_000_000L) {
      // Unix-Millis
      return (System.currentTimeMillis() - start) / 1000L;
    }
    if (start >= 1_000_000_000L) {
      // Unix-Sekunden
      return (System.currentTimeMillis() / 1000L) - start;
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
    return Math.clamp((double) getCurrentElapsedSeconds() / (double) getDuration(), 0.0d, 1.0d);
  }

  /**
   * Sendungsdaten aus der Evil-Radio-API (radioInfo) übernehmen.
   */
  public CurrentSong withShow(Show show) {
    return new CurrentSong(this, stationShortcode, show);
  }

  public CurrentSong withStationShortcode(String shortcode) {
    return new CurrentSong(this, shortcode, show);
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

  @Override
  public String toString() {
    return "CurrentSong{" +
        "stationId=" + stationId +
        ", stationName='" + stationName + '\'' +
        ", stationShortcode='" + stationShortcode + '\'' +
        ", title='" + title + '\'' +
        ", artist='" + artist + '\'' +
        ", imageUrl='" + imageUrl + '\'' +
        ", songId='" + songId + '\'' +
        ", playedAt=" + playedAt +
        ", duration=" + duration +
        ", elapsedAtUpdate=" + elapsedAtUpdate +
        ", receivedAt=" + receivedAt +
        ", adBreak=" + adBreak +
        ", show=" + show  +
        '}';
  }

  /**
   * Sendungsdaten – ausschließlich aus der Evil-Radio-REST-API ({@code radioInfo} → {@code show}),
   * nie aus dem AzuraCast-WebSocket.
   *
   * @param onAir          {@code live}
   * @param twitch         {@code twitch}
   * @param stream         {@code stream}, z. B. {@code Mashup}
   * @param name           {@code name}, Titel der Sendung
   * @param pictureUrl     {@code picture}, Flyer der Sendung
   * @param moderatorName  {@code dj}
   * @param moderatorImage {@code djImage}, Avatar des DJs
   * @param startHHmm      {@code start}, z. B. {@code 20:00}
   * @param endHHmm        {@code end}, z. B. {@code 22:00}
   * @param event          {@code event}
   * @param wishBoxEnabled {@code wishBox}
   * @param playedAtSec    Sendungsbeginn in Unix-Sekunden, {@code 0} wenn unbekannt
   * @param durationSec    Sendungsdauer in Sekunden, {@code 0} wenn unbekannt
   * @param clockLabel     z. B. {@code 20:00–22:00 Uhr}, sonst {@code null}
   */
  public record Show(
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
      long playedAtSec,
      long durationSec,
      String clockLabel
  ) {

    public static final Show NONE = new Show(
        false, false, null, null, null, null, null, null, null, false, false, 0L, 0L, null);

    public Show {
      stream = blankToNull(stream);
      name = blankToNull(name);
      pictureUrl = blankToNull(pictureUrl);
      moderatorName = blankToNull(moderatorName);
      moderatorImage = blankToNull(moderatorImage);
      startHHmm = blankToNull(startHHmm);
      endHHmm = blankToNull(endHHmm);
      playedAtSec = Math.max(0L, playedAtSec);
      durationSec = Math.max(0L, durationSec);
      clockLabel = blankToNull(clockLabel);
    }

    private static String blankToNull(String value) {
      return value == null || value.isBlank() ? null : value.trim();
    }

    public boolean hasWindow() {
      return this.playedAtSec > 0L && this.durationSec > 0L;
    }

    @Override
    public String toString() {
      return "Show{" +
          "onAir=" + onAir +
          ", twitch=" + twitch +
          ", stream='" + stream + '\'' +
          ", name='" + name + '\'' +
          ", pictureUrl='" + pictureUrl + '\'' +
          ", moderatorName='" + moderatorName + '\'' +
          ", moderatorImage='" + moderatorImage + '\'' +
          ", startHHmm='" + startHHmm + '\'' +
          ", endHHmm='" + endHHmm + '\'' +
          ", event=" + event +
          ", wishBoxEnabled=" + wishBoxEnabled +
          ", playedAtSec=" + playedAtSec +
          ", durationSec=" + durationSec +
          ", clockLabel='" + clockLabel + '\'' +
          '}';
    }
  }
}
