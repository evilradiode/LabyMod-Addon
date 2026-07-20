package de.evilradio.core.configuration;

public enum StationPickerStyle {
  WHEEL,
  LIST_PREVIEW;

  public boolean isWheel() {
    return this == WHEEL;
  }

  public boolean isListPreview() {
    return this == LIST_PREVIEW;
  }
}
