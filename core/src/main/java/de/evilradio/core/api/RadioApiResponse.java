package de.evilradio.core.api;

import com.google.gson.JsonObject;

public class RadioApiResponse {
  private final String currentSong;
  private final CurrentSongInfo current;
  
  public RadioApiResponse(JsonObject json) {
    this.currentSong = json.has("currentSong") ? json.get("currentSong").getAsString() : "";
    
    if (json.has("current") && json.get("current").isJsonObject()) {
      JsonObject currentObj = json.get("current").getAsJsonObject();
      String title = currentObj.has("title") ? currentObj.get("title").getAsString() : "";
      String artist = currentObj.has("artist") ? currentObj.get("artist").getAsString() : "";
      String image = currentObj.has("image") ? currentObj.get("image").getAsString() : "";
      this.current = new CurrentSongInfo(title, artist, image);
    } else {
      this.current = new CurrentSongInfo("", "", "");
    }
  }
  
  public String getCurrentSong() {
    return currentSong;
  }
  
  public CurrentSongInfo getCurrent() {
    return current;
  }
  
  public static class CurrentSongInfo {
    private final String title;
    private final String artist;
    private final String image;
    
    public CurrentSongInfo(String title, String artist, String image) {
      this.title = title;
      this.artist = artist;
      this.image = image;
    }
    
    public String getTitle() {
      return title;
    }
    
    public String getArtist() {
      return artist;
    }
    
    public String getImage() {
      return image;
    }
    
    public String getFormatted() {
      if (artist.isEmpty() && title.isEmpty()) {
        return "";
      }
      if (artist.isEmpty()) {
        return title;
      }
      if (title.isEmpty()) {
        return artist;
      }
      return artist + " - " + title;
    }
  }
}

