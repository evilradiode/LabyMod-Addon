package de.evilradio.core.schedule;

import java.util.Collections;
import java.util.List;

/**
 * Ein Tag im Evil-Radio-Sendeplan inkl. aller Sendungen.
 */
public final class ScheduleDay {

  private final String date;
  private final String weekday;
  private final List<ScheduleShow> shows;

  public ScheduleDay(String date, String weekday, List<ScheduleShow> shows) {
    this.date = date;
    this.weekday = weekday;
    this.shows = List.copyOf(shows);
  }

  public String getDate() {
    return this.date;
  }

  public String getWeekday() {
    return this.weekday;
  }

  public List<ScheduleShow> getShows() {
    return this.shows;
  }

  public boolean isEmpty() {
    return this.shows.isEmpty();
  }

  public List<ScheduleShow> showsOrEmpty() {
    return this.shows.isEmpty() ? Collections.emptyList() : this.shows;
  }
}
