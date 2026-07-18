package de.evilradio.core.song.azuracast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import de.evilradio.core.song.CurrentSong;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NowPlayingMessageParserTest {

  @Test
  void parsesInitialConnectPublication() throws Exception {
    JsonObject message = load("fixtures/nowplaying-connect-mashup.json");
    var publication = NowPlayingMessageParser.extractPublication(message).orElseThrow();
    assertEquals("station:mashup", publication.channel());
    CurrentSong song = publication.song();
    assertEquals("mashup", song.getStationShortcode());
    assertEquals("Test Title", song.getTitle());
    assertEquals("Test Artist", song.getArtist());
    assertEquals(42L, song.getElapsedAtUpdate());
    assertEquals(200L, song.getDuration());
    assertTrue(song.isOnAir());
    assertEquals("Alina", song.getModeratorName());
  }

  @Test
  void parsesRunningPublication() throws Exception {
    JsonObject message = load("fixtures/nowplaying-pub-oldi.json");
    var publication = NowPlayingMessageParser.extractPublication(message).orElseThrow();
    assertEquals("station:oldi", publication.channel());
    assertEquals("oldi", publication.song().getStationShortcode());
    assertEquals("Dance With Me", publication.song().getTitle());
    assertEquals("Alphaville", publication.song().getArtist());
  }

  @Test
  void treatsEmptyObjectAsKeepalive() {
    assertTrue(NowPlayingMessageParser.isKeepalive(new JsonObject()));
    assertFalse(NowPlayingMessageParser.isKeepalive(loadQuiet("fixtures/nowplaying-pub-oldi.json")));
  }

  @Test
  void ignoresInvalidJson() {
    assertTrue(NowPlayingMessageParser.parseJsonObject("{not-json").isEmpty());
    assertTrue(NowPlayingMessageParser.parseJsonObject("").isEmpty());
    assertTrue(NowPlayingMessageParser.parseJsonObject(null).isEmpty());
  }

  @Test
  void handlesMissingOptionalFields() throws Exception {
    JsonObject message = load("fixtures/nowplaying-pub-no-duration.json");
    CurrentSong song = NowPlayingMessageParser.extractPublication(message).orElseThrow().song();
    assertEquals("techno", song.getStationShortcode());
    assertEquals("Untitled Live Mix", song.getTitle());
    assertFalse(song.hasKnownDuration());
    assertEquals(-1.0d, song.getProgress());
  }

  @Test
  void parseNowPlayingRequiresSongTitle() {
    JsonObject np = new JsonObject();
    JsonObject nowPlaying = new JsonObject();
    JsonObject song = new JsonObject();
    song.addProperty("artist", "Only Artist");
    nowPlaying.add("song", song);
    np.add("now_playing", nowPlaying);
    assertTrue(NowPlayingMessageParser.parseNowPlayingPayload(np).isEmpty());
  }

  private static JsonObject load(String path) throws Exception {
    try (InputStream in = NowPlayingMessageParserTest.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("Missing fixture: " + path);
      }
      String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return NowPlayingMessageParser.parseJsonObject(raw).orElseThrow();
    }
  }

  private static JsonObject loadQuiet(String path) {
    try {
      return load(path);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
