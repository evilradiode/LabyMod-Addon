package de.evilradio.core.configuration;

/**
 * Visualizer-Stile für den Listen-Picker (Winamp-inspiriert).
 */
public enum EqualizerStyle {
  BARS,
  PEAKS,
  MIRROR,
  SCOPE,
  DOTS,
  OFF;

  public EqualizerStyle next() {
    EqualizerStyle[] values = values();
    return values[(this.ordinal() + 1) % values.length];
  }

  public boolean isEnabled() {
    return this != OFF;
  }

  public String styleId() {
    return "eq-" + this.name().toLowerCase();
  }
}
