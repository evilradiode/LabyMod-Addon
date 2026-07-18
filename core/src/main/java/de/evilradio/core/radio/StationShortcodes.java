package de.evilradio.core.radio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Explizite Zuordnung bekannter Evil-Radio-Sender zu AzuraCast-Shortcodes.
 * Primärquelle bleibt {@code internal_name} aus der Streams-API; diese Map dient nur als Fallback.
 */
public final class StationShortcodes {

  private static final Map<String, String> BY_STREAM_NAME;
  private static final Map<String, String> BY_DISPLAY_NAME;

  static {
    Map<String, String> byName = new LinkedHashMap<>();
    byName.put("Mashup", "mashup");
    byName.put("Summer", "sommer");
    byName.put("Xmas", "x-mas");
    byName.put("Schlager", "schlager");
    byName.put("Oldie", "oldi");
    byName.put("Anime", "animefm");
    byName.put("POP", "pop_und_rap");
    byName.put("Techno", "techno");
    BY_STREAM_NAME = Collections.unmodifiableMap(byName);

    Map<String, String> byDisplay = new LinkedHashMap<>();
    byDisplay.put("Mashup", "mashup");
    byDisplay.put("Sommer", "sommer");
    byDisplay.put("X-MAS", "x-mas");
    byDisplay.put("X-Mas", "x-mas");
    byDisplay.put("Schlager", "schlager");
    byDisplay.put("Oldie", "oldi");
    byDisplay.put("AnimeFm", "animefm");
    byDisplay.put("Anime", "animefm");
    byDisplay.put("POP und Rap", "pop_und_rap");
    byDisplay.put("Pop & Rap", "pop_und_rap");
    byDisplay.put("Techno", "techno");
    BY_DISPLAY_NAME = Collections.unmodifiableMap(byDisplay);
  }

  private StationShortcodes() {
  }

  public static Optional<String> fromStreamName(String streamName) {
    if (streamName == null || streamName.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_STREAM_NAME.get(streamName.trim()));
  }

  public static Optional<String> fromDisplayName(String displayName) {
    if (displayName == null || displayName.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_DISPLAY_NAME.get(displayName.trim()));
  }

  public static String resolve(String internalName, String streamName, String displayName) {
    if (internalName != null && !internalName.isBlank()) {
      return internalName.trim();
    }
    return fromStreamName(streamName)
        .or(() -> fromDisplayName(displayName))
        .orElse(null);
  }

  public static Map<String, String> knownByStreamName() {
    return BY_STREAM_NAME;
  }

  public static Map<String, String> knownByDisplayName() {
    return BY_DISPLAY_NAME;
  }

  public static boolean isValidShortcode(String shortcode) {
    if (shortcode == null || shortcode.isBlank()) {
      return false;
    }
    String value = shortcode.trim();
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_')) {
        return false;
      }
    }
    return true;
  }
}
