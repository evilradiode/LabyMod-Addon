package de.evilradio.core.song.azuracast;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Schützt vor verspäteten Publications nach Senderwechseln.
 */
public final class SubscriptionGenerationGuard {

  private final AtomicLong generation = new AtomicLong(0L);
  private volatile String activeShortcode;

  public synchronized long switchTo(String shortcode) {
    this.activeShortcode = shortcode;
    return generation.incrementAndGet();
  }

  public synchronized long clear() {
    this.activeShortcode = null;
    return generation.incrementAndGet();
  }

  public long currentGeneration() {
    return generation.get();
  }

  public String activeShortcode() {
    return activeShortcode;
  }

  public boolean accepts(long messageGeneration, String channelOrShortcode) {
    if (messageGeneration != generation.get()) {
      return false;
    }
    String active = activeShortcode;
    if (active == null || active.isBlank()) {
      return false;
    }
    if (channelOrShortcode == null || channelOrShortcode.isBlank()) {
      return false;
    }
    String expectedChannel = "station:" + active;
    return active.equals(channelOrShortcode) || expectedChannel.equals(channelOrShortcode);
  }
}
