package de.evilradio.core;

public enum AutoStartMode {
  ON_GAME_START("onGameStart"),
  ON_SERVER_JOIN("onServerJoin");

  private final String translationKey;

  AutoStartMode(String translationKey) {
    this.translationKey = translationKey;
  }

  public String getTranslationKey() {
    return "evilradio.settings.autoStart.mode.type." + translationKey;
  }

  public boolean shouldStartOnGameStart() {
    return this == ON_GAME_START;
  }

  public boolean shouldStartOnServerJoin() {
    return this == ON_SERVER_JOIN;
  }
}

