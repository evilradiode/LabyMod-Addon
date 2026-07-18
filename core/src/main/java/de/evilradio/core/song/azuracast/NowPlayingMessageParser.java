package de.evilradio.core.song.azuracast;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.evilradio.core.song.CurrentSong;
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
    if (message == null) {
      return Optional.empty();
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
          if (publications.isEmpty()) {
            continue;
          }
          JsonElement last = publications.get(publications.size() - 1);
          Optional<CurrentSong> song = parsePublicationElement(channel, last);
          if (song.isPresent()) {
            return Optional.of(new ParsedPublication(channel, song.get()));
          }
        }
      }
      if (connect.has("data") && connect.get("data").isJsonArray()) {
        var data = connect.getAsJsonArray("data");
        if (!data.isEmpty()) {
          JsonElement last = data.get(data.size() - 1);
          Optional<CurrentSong> song = parsePublicationElement(null, last);
          if (song.isPresent()) {
            String shortcode = song.get().getStationShortcode();
            String channel = shortcode == null ? null : "station:" + shortcode;
            return Optional.of(new ParsedPublication(channel, song.get()));
          }
        }
      }
    }

    if (message.has("pub")) {
      String channel = message.has("channel") && !message.get("channel").isJsonNull()
          ? message.get("channel").getAsString()
          : null;
      return parsePublicationElement(channel, message.get("pub"))
          .map(song -> new ParsedPublication(channel, song));
    }

    return Optional.empty();
  }

  public static Optional<CurrentSong> parseNowPlayingPayload(JsonObject npRoot) {
    return parseNowPlayingPayload(npRoot, System.currentTimeMillis());
  }

  public static Optional<CurrentSong> parseNowPlayingPayload(JsonObject npRoot, long receivedAt) {
    if (npRoot == null) {
      return Optional.empty();
    }

    JsonObject np = npRoot;
    if (npRoot.has("np") && npRoot.get("np").isJsonObject()) {
      np = npRoot.getAsJsonObject("np");
    }

    if (!np.has("now_playing") || !np.get("now_playing").isJsonObject()) {
      return Optional.empty();
    }

    JsonObject nowPlaying = np.getAsJsonObject("now_playing");
    if (!nowPlaying.has("song") || !nowPlaying.get("song").isJsonObject()) {
      return Optional.empty();
    }

    JsonObject song = nowPlaying.getAsJsonObject("song");
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
      if (live.has("is_live") && live.get("is_live").isJsonPrimitive()) {
        onAir = live.get("is_live").getAsBoolean();
      }
      moderatorName = textOrNull(live, "streamer_name");
      if ((moderatorName == null || moderatorName.isBlank()) && nowPlaying.has("streamer")) {
        moderatorName = textOrNull(nowPlaying, "streamer");
      }
    }

    long playedAt = longOrZero(nowPlaying, "played_at");
    long duration = Math.round(doubleOrZero(nowPlaying, "duration"));
    long elapsed = Math.round(doubleOrZero(nowPlaying, "elapsed"));

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

  private static Optional<CurrentSong> parsePublicationElement(String channel, JsonElement element) {
    if (element == null || !element.isJsonObject()) {
      return Optional.empty();
    }
    JsonObject publication = element.getAsJsonObject();
    JsonObject data = publication;
    if (publication.has("data") && publication.get("data").isJsonObject()) {
      data = publication.getAsJsonObject("data");
    }

    Optional<CurrentSong> song = parseNowPlayingPayload(data);
    if (song.isEmpty()) {
      return Optional.empty();
    }

    CurrentSong parsed = song.get();
    if ((parsed.getStationShortcode() == null || parsed.getStationShortcode().isBlank())
        && channel != null
        && channel.startsWith("station:")) {
      String shortcode = channel.substring("station:".length());
      return Optional.of(new CurrentSong(
          parsed.getStationId(),
          parsed.getStationName(),
          shortcode,
          parsed.getTitle(),
          parsed.getArtist(),
          parsed.getImageUrl(),
          parsed.getSongId(),
          parsed.getModeratorName(),
          parsed.isOnAir(),
          parsed.isTwitch(),
          parsed.getPlayedAt(),
          parsed.getDuration(),
          parsed.getElapsedAtUpdate(),
          parsed.getReceivedAt()
      ));
    }
    return song;
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

  public record ParsedPublication(String channel, CurrentSong song) {
  }
}
