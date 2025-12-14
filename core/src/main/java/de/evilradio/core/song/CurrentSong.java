package de.evilradio.core.song;

public class CurrentSong {

  private final String title;
  private final String artist;
  private final String imageUrl;
  private final String moderatorName;

  private final boolean onAir;
  private final boolean twitch;

  public CurrentSong(String title, String artist, String imageUrl) {
    this(title, artist, imageUrl, null, false, false);
  }

  public CurrentSong(String title, String artist, String imageUrl, String moderatorName, boolean onAir, boolean twitch) {
    this.title = title;
    this.artist = artist;
    this.imageUrl = imageUrl;
    this.moderatorName = moderatorName;
    this.onAir = onAir;
    this.twitch = twitch;
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

  public boolean isOnAir() {
    return onAir;
  }

  public String getModeratorName() {
    return moderatorName;
  }

  public boolean isTwitch() {
    return twitch;
  }

  public String getFormatted() {
    return String.format("%s - %s", title, artist);
  }

}
