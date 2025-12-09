package de.evilradio.core.song;

public class CurrentSong {

  private final String title;
  private final String artist;
  private final String imageUrl;

  public CurrentSong(String title, String artist, String imageUrl) {
    this.title = title;
    this.artist = artist;
    this.imageUrl = imageUrl;
  }

  public String getTitle() {
    return title;
  }

  public String getArtist() {
    return artist;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public String getFormatted() {
    return String.format("%s - %s", title, artist);
  }

}
