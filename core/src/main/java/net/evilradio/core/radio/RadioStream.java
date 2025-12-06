package net.evilradio.core.radio;

import net.labymod.api.client.gui.icon.Icon;

public class RadioStream {
  private final String url;
  private final String name;
  private final String genre;
  private final String country;
  private final int bitrate;
  private final Icon icon;
  private final String displayName;
  private final String category;

  public RadioStream(String url, String name, String genre, String country, int bitrate) {
    this(url, name, genre, country, bitrate, null, name, "");
  }

  public RadioStream(String url, String name, String genre, String country, int bitrate, Icon icon, String displayName) {
    this(url, name, genre, country, bitrate, icon, displayName, "");
  }

  public RadioStream(String url, String name, String genre, String country, int bitrate, Icon icon, String displayName, String category) {
    this.url = url;
    this.name = name;
    this.genre = genre;
    this.country = country;
    this.bitrate = bitrate;
    this.icon = icon;
    this.displayName = displayName;
    this.category = category;
  }

  public String getUrl() {
    return url;
  }

  public String getName() {
    return name;
  }

  public String getGenre() {
    return genre;
  }

  public String getCountry() {
    return country;
  }

  public int getBitrate() {
    return bitrate;
  }

  public Icon getIcon() {
    return icon;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getCategory() {
    return category;
  }

  @Override
  public String toString() {
    return name;
  }
}


