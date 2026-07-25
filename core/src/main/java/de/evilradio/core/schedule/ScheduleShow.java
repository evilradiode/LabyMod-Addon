package de.evilradio.core.schedule;

import org.jetbrains.annotations.Nullable;

/**
 * Eine Sendung aus dem Evil-Radio-Sendeplan.
 */
public final class ScheduleShow {

  private final String date;
  private final String weekday;
  private final String startTime;
  private final @Nullable String endTime;
  private final String showName;
  private final String moderator;
  private final @Nullable String showPictureUrl;
  private final @Nullable String profilePictureUrl;
  private final boolean onAir;
  private final boolean grussbox;
  private final boolean event;
  private final boolean twitch;

  public ScheduleShow(
      String date,
      String weekday,
      String startTime,
      @Nullable String endTime,
      String showName,
      String moderator,
      @Nullable String showPictureUrl,
      @Nullable String profilePictureUrl,
      boolean onAir,
      boolean grussbox,
      boolean event,
      boolean twitch) {
    this.date = date;
    this.weekday = weekday;
    this.startTime = startTime;
    this.endTime = endTime;
    this.showName = showName;
    this.moderator = moderator;
    this.showPictureUrl = showPictureUrl;
    this.profilePictureUrl = profilePictureUrl;
    this.onAir = onAir;
    this.grussbox = grussbox;
    this.event = event;
    this.twitch = twitch;
  }

  public String getDate() {
    return this.date;
  }

  public String getWeekday() {
    return this.weekday;
  }

  public String getStartTime() {
    return this.startTime;
  }

  public @Nullable String getEndTime() {
    return this.endTime;
  }

  public String getShowName() {
    return this.showName;
  }

  public String getModerator() {
    return this.moderator;
  }

  public @Nullable String getShowPictureUrl() {
    return this.showPictureUrl;
  }

  public @Nullable String getProfilePictureUrl() {
    return this.profilePictureUrl;
  }

  public boolean isOnAir() {
    return this.onAir;
  }

  public boolean isGrussbox() {
    return this.grussbox;
  }

  public boolean isEvent() {
    return this.event;
  }

  public boolean isTwitch() {
    return this.twitch;
  }

  public String getTimeLabel() {
    if (this.endTime == null || this.endTime.isBlank()) {
      return this.startTime;
    }
    return this.startTime + " - " + this.endTime;
  }
}
