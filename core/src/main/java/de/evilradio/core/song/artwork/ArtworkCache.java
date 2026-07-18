package de.evilradio.core.song.artwork;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Begrenzter Artwork-Cache mit Generationsschutz gegen Senderwechsel-Races.
 */
public final class ArtworkCache {

  private final int maxEntries;
  private final AtomicLong generation = new AtomicLong(0L);
  private final Map<String, String> urls;

  public ArtworkCache(int maxEntries) {
    this.maxEntries = Math.max(1, maxEntries);
    this.urls = new LinkedHashMap<>(16, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
        return size() > ArtworkCache.this.maxEntries;
      }
    };
  }

  public long bumpGeneration() {
    return generation.incrementAndGet();
  }

  public long currentGeneration() {
    return generation.get();
  }

  public synchronized String put(String cacheKey, String artworkUrl) {
    if (cacheKey == null || cacheKey.isBlank() || artworkUrl == null || artworkUrl.isBlank()) {
      return null;
    }
    if (!(artworkUrl.startsWith("http://") || artworkUrl.startsWith("https://"))) {
      return null;
    }
    urls.put(cacheKey, artworkUrl);
    return artworkUrl;
  }

  public synchronized String get(String cacheKey) {
    return urls.get(cacheKey);
  }

  public synchronized int size() {
    return urls.size();
  }

  /**
   * Wendet Artwork nur an, wenn die Generation noch aktuell ist.
   */
  public void applyIfCurrent(long expectedGeneration, String artworkUrl, Consumer<String> applier) {
    if (expectedGeneration != generation.get()) {
      return;
    }
    if (artworkUrl == null || artworkUrl.isBlank()) {
      return;
    }
    if (!(artworkUrl.startsWith("http://") || artworkUrl.startsWith("https://"))) {
      return;
    }
    applier.accept(artworkUrl);
  }

  public static String key(String stationShortcode, String songId, String artworkUrl) {
    if (songId != null && !songId.isBlank() && stationShortcode != null && !stationShortcode.isBlank()) {
      return stationShortcode + "|" + songId;
    }
    if (artworkUrl != null && !artworkUrl.isBlank()) {
      return artworkUrl;
    }
    return Objects.toString(stationShortcode, "") + "|" + Objects.toString(songId, "");
  }
}
