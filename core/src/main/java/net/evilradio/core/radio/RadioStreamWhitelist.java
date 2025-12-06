package net.evilradio.core.radio;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Whitelist für erlaubte laut.fm Radio-Stream-Namen.
 * Verhindert, dass User eigene Links oder interne Links hinzufügen.
 */
public class RadioStreamWhitelist {
  
  private static final Set<String> ALLOWED_STREAM_NAMES = new HashSet<>(Arrays.asList(
      "evil-radio-popundrap",
      "er-schlager",
      "evil-radiox-mas",
      "er-oldie",
      "summer-time",
      "evil-animefm"
      // terramusic ist NICHT in der Liste, da es noch intern ist
  ));
  
  /**
   * Prüft, ob eine URL ein erlaubter laut.fm Stream ist.
   * 
   * @param url Die zu prüfende URL
   * @return true, wenn die URL ein erlaubter laut.fm Stream ist, sonst false
   */
  public static boolean isAllowedStream(String url) {
    if (url == null || url.isEmpty()) {
      // Leere URLs sind erlaubt (für "Coming Soon" Platzhalter)
      return true;
    }
    
    // Prüfe, ob es eine laut.fm URL ist
    if (!url.startsWith("http://stream.laut.fm/") && !url.startsWith("https://stream.laut.fm/")) {
      return false;
    }
    
    // Extrahiere den Stream-Namen aus der URL
    String streamName = extractStreamName(url);
    if (streamName == null) {
      return false;
    }
    
    // Prüfe, ob der Stream-Name in der Whitelist ist
    return ALLOWED_STREAM_NAMES.contains(streamName);
  }
  
  /**
   * Extrahiert den Stream-Namen aus einer laut.fm URL.
   * 
   * @param url Die URL (z.B. "http://stream.laut.fm/evil-radio-popundrap")
   * @return Der Stream-Name (z.B. "evil-radio-popundrap") oder null, wenn die URL ungültig ist
   */
  public static String extractStreamName(String url) {
    if (url == null || url.isEmpty()) {
      return null;
    }
    
    // Entferne Protokoll und Domain
    String path = url;
    if (path.startsWith("http://stream.laut.fm/")) {
      path = path.substring("http://stream.laut.fm/".length());
    } else if (path.startsWith("https://stream.laut.fm/")) {
      path = path.substring("https://stream.laut.fm/".length());
    } else {
      return null;
    }
    
    // Entferne Query-Parameter und Fragmente
    int queryIndex = path.indexOf('?');
    if (queryIndex != -1) {
      path = path.substring(0, queryIndex);
    }
    
    int fragmentIndex = path.indexOf('#');
    if (fragmentIndex != -1) {
      path = path.substring(0, fragmentIndex);
    }
    
    // Entferne führende/trailing Slashes
    path = path.trim();
    if (path.isEmpty()) {
      return null;
    }
    
    return path;
  }
  
  /**
   * Gibt alle erlaubten Stream-Namen zurück.
   * 
   * @return Eine Kopie der erlaubten Stream-Namen
   */
  public static Set<String> getAllowedStreamNames() {
    return new HashSet<>(ALLOWED_STREAM_NAMES);
  }
}

