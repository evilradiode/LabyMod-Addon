package de.evilradio.core;

public class EvilConstants {

  public static final String API_BASE_URL = "https://api.evil-radio.de";
  public static final String API_RADIO_INFO = "https://api.evil-radio.de/?radioInfo=";
  public static final String AZURACAST_NOWPLAYING_URL =
      "https://broadcast.evil-radio.de/api/nowplaying";
  public static final String SCHEDULE_ENDPOINT = "https://api.evil-radio.de/sp";
  public static final String SCHEDULE_API_URL = "https://api.evil-radio.de/sp?opt=sendeplan";

  public static final String WISH_BOX_URL = "https://evil-radio.de/sendeplan/music-request";
  public static final String TWITCH_URL = "https://www.twitch.tv/evilradiode";

  public static class HudWidgetVariables {

    // General Keys

    public static final float MAX_PLAYER_WIDTH = 160f;

    public static final String PROGRESS_FILL_WIDTH_KEY = "--song-widget-progress-width";
    public static final String MAX_PLAYER_WIDTH_KEY = "--song-widget-max-player-width";

    // Customization Keys

    public static final String BACKGROUND_VARIABLE_KEY = "--song-widget-bg";
    public static final String BACKGROUND_BLUR_VARIABLE_KEY = "--song-widget-blur";
    public static final String BORDER_COLOR_VARIABLE_KEY = "--song-widget-border-color";
    public static final String PROGRESS_BAR_COLOR_VARIABLE_KEY = "--song-widget-progress-bar-color";
    public static final String OPACITY_VARIABLE_KEY = "--song-widget-opacity";

  }

}
