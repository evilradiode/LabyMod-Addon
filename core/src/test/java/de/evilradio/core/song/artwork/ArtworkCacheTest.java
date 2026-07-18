package de.evilradio.core.song.artwork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ArtworkCacheTest {

  @Test
  void rejectsInvalidProtocols() {
    ArtworkCache cache = new ArtworkCache(8);
    assertNull(cache.put("key", "ftp://example/art.jpg"));
    assertNull(cache.put("key", "javascript:alert(1)"));
  }

  @Test
  void limitsCacheSizeWithLru() {
    ArtworkCache cache = new ArtworkCache(2);
    cache.put("a", "https://example/a.jpg");
    cache.put("b", "https://example/b.jpg");
    cache.put("c", "https://example/c.jpg");
    assertEquals(2, cache.size());
    assertNull(cache.get("a"));
    assertEquals("https://example/b.jpg", cache.get("b"));
    assertEquals("https://example/c.jpg", cache.get("c"));
  }

  @Test
  void generationPreventsStaleArtworkApply() {
    ArtworkCache cache = new ArtworkCache(8);
    long generation = cache.currentGeneration();
    cache.put("mashup|1", "https://example/old.jpg");

    cache.bumpGeneration();

    AtomicReference<String> applied = new AtomicReference<>();
    cache.applyIfCurrent(generation, "https://example/old.jpg", applied::set);
    assertNull(applied.get());

    long current = cache.currentGeneration();
    cache.applyIfCurrent(current, "https://example/new.jpg", applied::set);
    assertEquals("https://example/new.jpg", applied.get());
  }

  @Test
  void prefersStationAndSongIdAsKey() {
    assertEquals("oldi|abc", ArtworkCache.key("oldi", "abc", "https://example/art.jpg"));
    assertEquals("https://example/art.jpg", ArtworkCache.key(null, null, "https://example/art.jpg"));
  }
}
