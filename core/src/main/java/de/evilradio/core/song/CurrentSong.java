package de.evilradio.core.song;

public class CurrentSong {

  private final String title;
  private final String artist;
  private final String imageUrl;
  private final boolean onAir;
  private final String moderatorName;

  public CurrentSong(String title, String artist, String imageUrl) {
    // Parse On Air Status und Moderator-Name aus title oder artist
    ParsedSongInfo parsed = parseSongInfo(title, artist);
    
    this.title = parsed.cleanTitle;
    this.artist = parsed.cleanArtist;
    this.imageUrl = imageUrl;
    this.onAir = parsed.onAir;
    this.moderatorName = parsed.moderatorName;
  }

  private ParsedSongInfo parseSongInfo(String title, String artist) {
    boolean onAir = false;
    String moderatorName = null;
    String cleanTitle = title;
    String cleanArtist = artist;

    // Prüfe ob "* On Air" im title oder artist enthalten ist
    String combined = String.format("%s - %s", artist, title);
    String sourceToCheck = combined;
    
    // Prüfe auch direkt im title oder artist Feld
    if (title.contains("* On Air") || artist.contains("* On Air")) {
      sourceToCheck = title.contains("* On Air") ? title : artist;
    }

    // Prüfe ob "* On Air" im String enthalten ist
    if (sourceToCheck.contains("* On Air")) {
      onAir = true;
      
      // Extrahiere Moderator-Name nach "|" (alles nach dem | ist der Moderator-Name)
      if (sourceToCheck.contains("|")) {
        String[] parts = sourceToCheck.split("\\|", 2);
        if (parts.length == 2) {
          moderatorName = parts[1].trim();
          // Entferne "| Moderator-Name" aus dem String
          sourceToCheck = parts[0].trim();
        }
      }
      
      // Entferne "* On Air" und "Euer"/"Eure" aus dem String
      sourceToCheck = sourceToCheck.replace("* On Air", "").trim();
      sourceToCheck = sourceToCheck.replaceAll("\\bEuer\\b", "").trim();
      sourceToCheck = sourceToCheck.replaceAll("\\bEure\\b", "").trim();
      sourceToCheck = sourceToCheck.replaceAll("\\s+", " ").trim(); // Mehrfache Leerzeichen entfernen
      
      // Wenn das Format direkt im title oder artist war, passe entsprechend an
      if (title.contains("* On Air")) {
        cleanTitle = sourceToCheck;
      } else if (artist.contains("* On Air")) {
        cleanArtist = sourceToCheck;
      } else {
        // Format war im kombinierten String, teile wieder in artist und title
        if (sourceToCheck.contains(" - ")) {
          String[] parts = sourceToCheck.split(" - ", 2);
          if (parts.length == 2) {
            cleanArtist = parts[0].trim();
            cleanTitle = parts[1].trim();
          }
        }
      }
    }

    return new ParsedSongInfo(cleanTitle, cleanArtist, onAir, moderatorName);
  }

  private static class ParsedSongInfo {
    final String cleanTitle;
    final String cleanArtist;
    final boolean onAir;
    final String moderatorName;

    ParsedSongInfo(String cleanTitle, String cleanArtist, boolean onAir, String moderatorName) {
      this.cleanTitle = cleanTitle;
      this.cleanArtist = cleanArtist;
      this.onAir = onAir;
      this.moderatorName = moderatorName;
    }
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

  public String getFormatted() {
    return String.format("%s - %s", title, artist);
  }

}
