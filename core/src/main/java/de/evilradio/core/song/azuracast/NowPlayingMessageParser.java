package de.evilradio.core.song.azuracast;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.evilradio.core.song.CurrentSong;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parst AzuraCast-/Centrifugo-Now-Playing-Nachrichten.
 */
public final class NowPlayingMessageParser {

  private NowPlayingMessageParser() {
  }

  public static Optional<JsonObject> parseJsonObject(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      JsonElement element = JsonParser.parseString(raw);
      if (element == null || !element.isJsonObject()) {
        return Optional.empty();
      }
      return Optional.of(element.getAsJsonObject());
    } catch (RuntimeException ignored) {
      return Optional.empty();
    }
  }

  public static boolean isKeepalive(JsonObject message) {
    return message != null && message.entrySet().isEmpty();
  }

  public static Optional<ParsedPublication> extractPublication(JsonObject message) {
    List<ParsedPublication> all = extractAllPublications(message);
    return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
  }

  /**
   * Alle Now-Playing-Publikationen einer Nachricht (wichtig bei Multi-Subscribe-Connect).
   */
  public static List<ParsedPublication> extractAllPublications(JsonObject message) {
    List<ParsedPublication> result = new ArrayList<>();
    if (message == null) {
      return result;
    }

    if (message.has("connect") && message.get("connect").isJsonObject()) {
      JsonObject connect = message.getAsJsonObject("connect");
      if (connect.has("subs") && connect.get("subs").isJsonObject()) {
        JsonObject subs = connect.getAsJsonObject("subs");
        for (String channel : subs.keySet()) {
          JsonObject sub = subs.getAsJsonObject(channel);
          if (sub == null || !sub.has("publications") || !sub.get("publications").isJsonArray()) {
            continue;
          }
          var publications = sub.getAsJsonArray("publications");
          for (int i = 0; i < publications.size(); i++) {
            parsePublicationElement(channel, publications.get(i))
                .ifPresent(snapshot -> result.add(ParsedPublication.of(channel, snapshot)));
          }
        }
      }
      if (result.isEmpty() && connect.has("data") && connect.get("data").isJsonArray()) {
        var data = connect.getAsJsonArray("data");
        for (int i = 0; i < data.size(); i++) {
          Optional<NowPlayingSnapshot> snapshot = parsePublicationElement(null, data.get(i));
          if (snapshot.isEmpty()) {
            continue;
          }
          String shortcode = snapshot.get().current().getStationShortcode();
          String channel = shortcode == null ? null : "station:" + shortcode;
          result.add(ParsedPublication.of(channel, snapshot.get()));
        }
      }
      return result;
    }

    if (message.has("pub")) {
      String channel = message.has("channel") && !message.get("channel").isJsonNull()
          ? message.get("channel").getAsString()
          : null;
      parsePublicationElement(channel, message.get("pub"))
          .ifPresent(snapshot -> result.add(ParsedPublication.of(channel, snapshot)));
    }

    return result;
  }

  public static Optional<CurrentSong> parseNowPlayingPayload(JsonObject npRoot) {
    return parseNowPlayingPayload(npRoot, System.currentTimeMillis());
  }

  public static Optional<CurrentSong> parseNowPlayingPayload(JsonObject npRoot, long receivedAt) {
    return parseNowPlayingSnapshot(npRoot, receivedAt).map(NowPlayingSnapshot::current);
  }

  /**
   * Parst den aktuell laufenden Song sowie – sofern vorhanden – den zuletzt und den als nächstes
   * gespielten Song aus einer AzuraCast-Now-Playing-Payload.
   */
  public static Optional<NowPlayingSnapshot> parseNowPlayingSnapshot(JsonObject npRoot) {
    return parseNowPlayingSnapshot(npRoot, System.currentTimeMillis());
  }

  public static Optional<NowPlayingSnapshot> parseNowPlayingSnapshot(JsonObject npRoot, long receivedAt) {
    if (npRoot == null) {
      return Optional.empty();
    }

    JsonObject np = npRoot;
    if (npRoot.has("np") && npRoot.get("np").isJsonObject()) {
      np = npRoot.getAsJsonObject("np");
    }

    int stationId = 0;
    String stationName = null;
    String stationShortcode = null;
    if (np.has("station") && np.get("station").isJsonObject()) {
      JsonObject station = np.getAsJsonObject("station");
      if (station.has("id") && station.get("id").isJsonPrimitive()) {
        stationId = station.get("id").getAsInt();
      }
      stationName = textOrNull(station, "name");
      stationShortcode = textOrNull(station, "shortcode");
    }

    boolean onAir = false;
    String moderatorName = null;
    if (np.has("live") && np.get("live").isJsonObject()) {
      JsonObject live = np.getAsJsonObject("live");
      // OnAir/Twitch nicht aus AzuraCast. streamer_name nur Fallback –
      // Anzeige-Name kommt bevorzugt aus radioInfo show.dj.
      moderatorName = textOrNull(live, "streamer_name");
    }

    JsonObject nowPlaying = np.has("now_playing") && np.get("now_playing").isJsonObject()
        ? np.getAsJsonObject("now_playing")
        : null;
    if (nowPlaying == null) {
      return Optional.empty();
    }
    if ((moderatorName == null || moderatorName.isBlank()) && nowPlaying.has("streamer")) {
      moderatorName = textOrNull(nowPlaying, "streamer");
    }

    Optional<CurrentSong> current = parseSongEntry(
        nowPlaying, stationId, stationName, stationShortcode, onAir, moderatorName, receivedAt);
    if (current.isEmpty()) {
      return Optional.empty();
    }

    CurrentSong previous = null;
    if (np.has("song_history") && np.get("song_history").isJsonArray()) {
      var history = np.getAsJsonArray("song_history");
      // AzuraCast liefert die zuletzt gespielten Songs zuerst.
      // Werbung (START_AD_BREAK) überspringen – „Vorheriger Song“ soll ein echter Track sein.
      for (int i = 0; i < history.size(); i++) {
        if (!history.get(i).isJsonObject()) {
          continue;
        }
        CurrentSong candidate = parseSongEntry(history.get(i).getAsJsonObject(),
            stationId, stationName, stationShortcode, false, null, receivedAt).orElse(null);
        if (candidate != null && candidate.isUsableAsPreviousSong()) {
          previous = candidate;
          break;
        }
      }
    }

    return Optional.of(new NowPlayingSnapshot(current.get(), previous));
  }

  private static Optional<CurrentSong> parseSongEntry(
      JsonObject entry,
      int stationId,
      String stationName,
      String stationShortcode,
      boolean onAir,
      String moderatorName,
      long receivedAt
  ) {
    if (entry == null || !entry.has("song") || !entry.get("song").isJsonObject()) {
      return Optional.empty();
    }

    JsonObject song = entry.getAsJsonObject("song");
    String title = textOrEmpty(song, "title");
    if (title.isBlank()) {
      title = textOrEmpty(song, "text");
    }
    if (title.isBlank()) {
      return Optional.empty();
    }

    String artist = textOrEmpty(song, "artist");
    String art = textOrNull(song, "art");
    String songId = textOrNull(song, "id");

    long playedAt = longOrZero(entry, "played_at");
    long duration = Math.round(doubleOrZero(entry, "duration"));
    long elapsed = Math.round(doubleOrZero(entry, "elapsed"));
    // Absolute Startzeit rekonstruieren, falls AzuraCast kein played_at liefert
    if (playedAt <= 0L && elapsed >= 0L && receivedAt > 0L) {
      playedAt = (receivedAt / 1000L) - elapsed;
    }

    return Optional.of(new CurrentSong(
        stationId,
        stationName,
        stationShortcode,
        title,
        artist,
        art,
        songId,
        moderatorName,
        onAir,
        false,
        playedAt,
        duration,
        elapsed,
        receivedAt
    ));
  }

  private static Optional<NowPlayingSnapshot> parsePublicationElement(String channel, JsonElement element) {
    if (element == null || !element.isJsonObject()) {
      return Optional.empty();
    }
    JsonObject publication = element.getAsJsonObject();
    JsonObject data = publication;
    if (publication.has("data") && publication.get("data").isJsonObject()) {
      data = publication.getAsJsonObject("data");
    }

    Optional<NowPlayingSnapshot> snapshot = parseNowPlayingSnapshot(data);
    if (snapshot.isEmpty()) {
      return Optional.empty();
    }

    NowPlayingSnapshot parsed = snapshot.get();
    CurrentSong current = parsed.current();
    if ((current.getStationShortcode() == null || current.getStationShortcode().isBlank())
        && channel != null
        && channel.startsWith("station:")) {
      String shortcode = channel.substring("station:".length());
      return Optional.of(new NowPlayingSnapshot(
          withShortcode(current, shortcode),
          withShortcode(parsed.previous(), shortcode)
      ));
    }
    return snapshot;
  }

  private static CurrentSong withShortcode(CurrentSong song, String shortcode) {
    if (song == null) {
      return null;
    }
    return song.withStationShortcode(shortcode);
  }

  private static String textOrEmpty(JsonObject object, String key) {
    String value = textOrNull(object, key);
    return value == null ? "" : value;
  }

  private static String textOrNull(JsonObject object, String key) {
    if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
      return null;
    }
    try {
      String value = object.get(key).getAsString();
      return value == null || value.isBlank() ? null : value;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static long longOrZero(JsonObject object, String key) {
    if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
      return 0L;
    }
    try {
      return object.get(key).getAsLong();
    } catch (RuntimeException ignored) {
      try {
        return Math.round(object.get(key).getAsDouble());
      } catch (RuntimeException ignoredAgain) {
        return 0L;
      }
    }
  }

  private static double doubleOrZero(JsonObject object, String key) {
    if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
      return 0.0d;
    }
    try {
      return object.get(key).getAsDouble();
    } catch (RuntimeException ignored) {
      return 0.0d;
    }
  }

  public record ParsedPublication(
      String channel,
      CurrentSong song,
      CurrentSong previousSong
  ) {

    static ParsedPublication of(String channel, NowPlayingSnapshot snapshot) {
      return new ParsedPublication(
          channel, snapshot.current(), snapshot.previous());
    }
  }

  /**
   * Momentaufnahme mit aktuellem, zuletzt und als nächstes gespieltem Song. {@code previous} und
   * {@code next} können {@code null} sein, wenn die Payload keine entsprechenden Daten enthält.
   */
  public record NowPlayingSnapshot(CurrentSong current, CurrentSong previous) {
  }
}
