package de.evilradio.core.radio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StationShortcodesTest {

  @Test
  void mapsAllKnownDisplayNamesToShortcodes() {
    assertEquals("mashup", StationShortcodes.fromDisplayName("Mashup").orElseThrow());
    assertEquals("sommer", StationShortcodes.fromDisplayName("Sommer").orElseThrow());
    assertEquals("x-mas", StationShortcodes.fromDisplayName("X-MAS").orElseThrow());
    assertEquals("schlager", StationShortcodes.fromDisplayName("Schlager").orElseThrow());
    assertEquals("oldi", StationShortcodes.fromDisplayName("Oldie").orElseThrow());
    assertEquals("animefm", StationShortcodes.fromDisplayName("AnimeFm").orElseThrow());
    assertEquals("pop_und_rap", StationShortcodes.fromDisplayName("POP und Rap").orElseThrow());
    assertEquals("techno", StationShortcodes.fromDisplayName("Techno").orElseThrow());
  }

  @Test
  void mapsStreamNamesToShortcodes() {
    assertEquals("mashup", StationShortcodes.fromStreamName("Mashup").orElseThrow());
    assertEquals("sommer", StationShortcodes.fromStreamName("Summer").orElseThrow());
    assertEquals("x-mas", StationShortcodes.fromStreamName("Xmas").orElseThrow());
    assertEquals("schlager", StationShortcodes.fromStreamName("Schlager").orElseThrow());
    assertEquals("oldi", StationShortcodes.fromStreamName("Oldie").orElseThrow());
    assertEquals("animefm", StationShortcodes.fromStreamName("Anime").orElseThrow());
    assertEquals("pop_und_rap", StationShortcodes.fromStreamName("POP").orElseThrow());
    assertEquals("techno", StationShortcodes.fromStreamName("Techno").orElseThrow());
  }

  @Test
  void prefersInternalNameOverFallbacks() {
    assertEquals("oldi", StationShortcodes.resolve("oldi", "Oldie", "Oldie"));
    assertEquals("pop_und_rap", StationShortcodes.resolve("pop_und_rap", "POP", "Pop & Rap"));
    assertEquals("oldi", StationShortcodes.resolve(null, "Oldie", "Oldie"));
    assertEquals("mashup", StationShortcodes.resolve(null, "Mashup", "Mashup"));
  }

  @Test
  void rejectsInvalidShortcodes() {
    assertFalse(StationShortcodes.isValidShortcode(null));
    assertFalse(StationShortcodes.isValidShortcode(""));
    assertFalse(StationShortcodes.isValidShortcode("POP und Rap"));
    assertTrue(StationShortcodes.isValidShortcode("x-mas"));
    assertTrue(StationShortcodes.isValidShortcode("pop_und_rap"));
  }
}
