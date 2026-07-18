package de.evilradio.core.song;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CurrentSongProgressTest {

  @Test
  void calculatesElapsedWithWallClockAdvance() {
    long receivedAt = System.currentTimeMillis() - 5000L;
    CurrentSong song = new CurrentSong(
        1, "Mashup", "mashup", "Title", "Artist", "https://art", "id",
        null, false, false, 100, 100, 10, receivedAt
    );
    long elapsed = song.getCurrentElapsedSeconds();
    assertTrue(elapsed >= 14 && elapsed <= 16);
  }

  @Test
  void clampsElapsedToDuration() {
    long receivedAt = System.currentTimeMillis() - 60_000L;
    CurrentSong song = new CurrentSong(
        1, "Mashup", "mashup", "Title", "Artist", "https://art", "id",
        null, false, false, 100, 20, 10, receivedAt
    );
    assertEquals(20L, song.getCurrentElapsedSeconds());
    assertEquals(1.0d, song.getProgress());
  }

  @Test
  void unknownDurationReturnsNegativeProgress() {
    CurrentSong song = new CurrentSong(
        8, "Techno", "techno", "Mix", "DJ", null, "id",
        null, false, false, 100, 0, 15, System.currentTimeMillis()
    );
    assertFalse(song.hasKnownDuration());
    assertEquals(-1.0d, song.getProgress());
    assertTrue(song.getCurrentElapsedSeconds() >= 15);
  }

  @Test
  void formatsTime() {
    assertEquals("1:05", CurrentSong.formatTime(65));
    assertEquals("0:00", CurrentSong.formatTime(0));
  }

  @Test
  void isValidRequiresTitle() {
    assertFalse(new CurrentSong("", "Artist", "https://art").isValid());
    assertTrue(new CurrentSong("Title", "Artist", "https://art").isValid());
  }
}
