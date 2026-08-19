package de.evilradio.core.schedule;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.command.ListenMashupCommand;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.event.ClickEvent;
import net.labymod.api.client.component.event.HoverEvent;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.TextColor;
import net.labymod.api.client.component.format.TextDecoration;
import net.labymod.api.util.concurrent.task.Task;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.logging.Logging;
import org.jetbrains.annotations.Nullable;

public class ScheduleService {

  private static final String SCHEDULE_API_URL = "https://api.evil-radio.de/sp?opt=sendeplan";

  /**
   * Wie auf evil-radio.de/sendeplan: Banner kommt aus {@code beschreibung} (Landscape),
   * nicht aus dem Hochformat-{@code showpicture}.
   */
  private static final Pattern HTML_IMG_SRC = Pattern.compile(
      "(?i)<img[^>]+\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']");

  private final Logging logging = Logging.create("EvilRadio-ScheduleService");

  private final EvilRadioAddon addon;
  private Task scheduleCheckTask;
  private Task hourlyUpdateTask;

  private List<ScheduleDay> cachedDays = new ArrayList<>();
  private List<ScheduleShow> cachedShows = new ArrayList<>();

  /** Trackt, welche Sendung bereits benachrichtigt wurde (Datum + Startzeit als Key). */
  private String lastNotifiedShowKey = null;

  public ScheduleService(EvilRadioAddon addon) {
    this.addon = addon;
  }

  public void startScheduleChecker() {
    this.loadAndCacheSchedule(null);

    this.scheduleCheckTask = Task.builder(this::checkSchedule).repeat(1, TimeUnit.MINUTES).build();
    this.scheduleCheckTask.execute();

    this.hourlyUpdateTask = Task.builder(() -> this.loadAndCacheSchedule(null))
        .repeat(1, TimeUnit.HOURS)
        .build();
    this.hourlyUpdateTask.execute();

    this.checkSchedule();
  }

  public void stopScheduleChecker() {
    if (this.scheduleCheckTask != null) {
      this.scheduleCheckTask.cancel();
    }
    if (this.hourlyUpdateTask != null) {
      this.hourlyUpdateTask.cancel();
    }
  }

  public List<ScheduleDay> days() {
    return Collections.unmodifiableList(this.cachedDays);
  }

  public List<ScheduleShow> shows() {
    return Collections.unmodifiableList(this.cachedShows);
  }

  /**
   * Aktuell laufende Sendung: zuerst {@code onair} von heute, sonst Zeitfenster.
   */
  public @Nullable ScheduleShow currentOnAirShow() {
    LocalDate today = LocalDate.now();
    ScheduleShow todayOnAir = null;
    ScheduleShow anyOnAir = null;
    for (ScheduleShow show : this.cachedShows) {
      if (!show.isOnAir()) {
        continue;
      }
      if (anyOnAir == null) {
        anyOnAir = show;
      }
      LocalDate showDate = this.parseDate(show.getDate());
      if (showDate != null && showDate.equals(today)) {
        todayOnAir = show;
        break;
      }
    }
    if (todayOnAir != null) {
      return todayOnAir;
    }
    if (anyOnAir != null) {
      return anyOnAir;
    }
    return this.showAt(today, LocalTime.now());
  }

  private @Nullable ScheduleShow showAt(LocalDate date, LocalTime time) {
    for (ScheduleShow show : this.cachedShows) {
      LocalDate showDate = this.parseDate(show.getDate());
      if (showDate == null || !showDate.equals(date)) {
        continue;
      }
      LocalTime start = this.parseTime(show.getStartTime());
      if (start == null) {
        continue;
      }
      LocalTime end = this.parseTime(show.getEndTime());
      if (end == null) {
        if (!time.isBefore(start)) {
          return show;
        }
        continue;
      }
      boolean overnight = end.isBefore(start);
      boolean inWindow = overnight
          ? !time.isBefore(start) || time.isBefore(end)
          : !time.isBefore(start) && time.isBefore(end);
      if (inWindow) {
        return show;
      }
    }
    return null;
  }

  public @Nullable ScheduleDay dayByDate(String date) {
    if (date == null) {
      return null;
    }
    for (ScheduleDay day : this.cachedDays) {
      if (date.equals(day.getDate())) {
        return day;
      }
    }
    return null;
  }

  /**
   * Lädt den Sendeplan asynchron neu. {@code onDone} läuft auf dem Render-Thread.
   */
  public void refreshAsync(@Nullable Runnable onDone) {
    this.loadAndCacheSchedule(onDone);
  }

  private void loadAndCacheSchedule(@Nullable Runnable onDone) {
    Request.ofGson(JsonArray.class)
        .url(SCHEDULE_API_URL)
        .async()
        .connectTimeout(5000)
        .readTimeout(5000)
        .userAgent(this.addon.apiUserAgent())
        .addHeader("X-Addon-Version", this.addon.addonVersion())
        .execute(response -> {
          if (response.hasException() || response.getStatusCode() != 200) {
            this.logging.error(
                "Failed to load schedule",
                response.hasException()
                    ? response.exception()
                    : new Exception("HTTP " + response.getStatusCode()));
            this.runOnRender(onDone);
            return;
          }

          JsonArray scheduleArray = response.get();
          if (scheduleArray == null || scheduleArray.size() == 0) {
            this.runOnRender(onDone);
            return;
          }

          this.applyParsedSchedule(scheduleArray);
          this.logging.info(
              "Sendeplan geladen und lokal gespeichert: "
                  + this.cachedShows.size()
                  + " Sendungen / "
                  + this.cachedDays.size()
                  + " Tage");
          this.runOnRender(onDone);
        });
  }

  private void checkSchedule() {
    if (!this.cachedShows.isEmpty()) {
      this.checkCachedShows();
    }

    Request.ofGson(JsonArray.class)
        .url(SCHEDULE_API_URL)
        .async()
        .connectTimeout(5000)
        .readTimeout(5000)
        .userAgent(this.addon.apiUserAgent())
        .addHeader("X-Addon-Version", this.addon.addonVersion())
        .execute(response -> {
          if (response.hasException() || response.getStatusCode() != 200) {
            this.logging.error(
                "Failed to load schedule",
                response.hasException()
                    ? response.exception()
                    : new Exception("HTTP " + response.getStatusCode()));
            return;
          }

          JsonArray scheduleArray = response.get();
          if (scheduleArray == null || scheduleArray.size() == 0) {
            return;
          }

          this.applyParsedSchedule(scheduleArray);
          this.checkCachedShows();
        });
  }

  private void applyParsedSchedule(JsonArray scheduleArray) {
    List<ScheduleDay> days = this.parseAllDays(scheduleArray);
    List<ScheduleShow> shows = new ArrayList<>();
    for (ScheduleDay day : days) {
      shows.addAll(day.getShows());
    }
    this.cachedDays = days;
    this.cachedShows = shows;
  }

  private void checkCachedShows() {
    for (ScheduleShow show : this.cachedShows) {
      if (!this.shouldSendNotification(show)) {
        continue;
      }
      String showKey = show.getDate() + "_" + show.getStartTime();
      if (!showKey.equals(this.lastNotifiedShowKey)) {
        this.lastNotifiedShowKey = showKey;
        this.sendLiveNotification(show);
      }
    }
  }

  private List<ScheduleDay> parseAllDays(JsonArray scheduleArray) {
    List<ScheduleDay> days = new ArrayList<>();
    LocalDate today = LocalDate.now();

    for (JsonElement dayElement : scheduleArray) {
      JsonArray dayDataArray = this.extractDayEntries(dayElement);
      if (dayDataArray == null || dayDataArray.size() == 0) {
        continue;
      }

      JsonObject dayInfo = this.findDayInfo(dayDataArray);
      if (dayInfo == null || !dayInfo.has("datum")) {
        continue;
      }

      String dateStr = dayInfo.get("datum").getAsString();
      LocalDate showDate = this.parseDate(dateStr);
      if (showDate == null || showDate.isBefore(today) || showDate.isAfter(today.plusDays(7))) {
        continue;
      }

      String weekday = dayInfo.has("wochentag") ? dayInfo.get("wochentag").getAsString() : "";
      List<ScheduleShow> shows = this.parseShowsForDay(dayDataArray, dateStr, weekday);
      days.add(new ScheduleDay(dateStr, weekday, shows));
    }

    return days;
  }

  private List<ScheduleShow> parseShowsForDay(
      JsonArray dayDataArray, String dateStr, String weekday) {
    List<ScheduleShow> shows = new ArrayList<>();
    for (int i = 0; i < dayDataArray.size(); i++) {
      ScheduleShow show = this.parseShowEntry(dayDataArray.get(i), dateStr, weekday);
      if (show != null) {
        shows.add(show);
      }
    }
    return shows;
  }

  private @Nullable ScheduleShow parseShowEntry(
      JsonElement showElement, String dateStr, String weekday) {
    if (!showElement.isJsonObject()) {
      return null;
    }
    JsonObject showObj = showElement.getAsJsonObject();
    if (!showObj.has("sendungen") || !showObj.get("sendungen").isJsonObject()) {
      return null;
    }
    return this.parseSendung(showObj.getAsJsonObject("sendungen"), dateStr, weekday);
  }

  private @Nullable ScheduleShow parseSendung(
      JsonObject sendung, String dateStr, String weekday) {
    if (!sendung.has("von")) {
      return null;
    }

    String startTimeStr = sendung.get("von").getAsString();
    String endTimeStr = sendung.has("bis") && !sendung.get("bis").isJsonNull()
        ? sendung.get("bis").getAsString()
        : null;

    if (this.isShowCancelled(startTimeStr, endTimeStr)) {
      return null;
    }

    String showname = sendung.has("showname") && !sendung.get("showname").isJsonNull()
        ? sendung.get("showname").getAsString()
        : "Unbekannte Sendung";
    String moderator = sendung.has("moderator") && !sendung.get("moderator").isJsonNull()
        ? sendung.get("moderator").getAsString()
        : "Unbekannt";
    String showPicture = this.resolveShowBannerUrl(sendung);
    String profilePicture = this.optionalString(sendung, "profil_bild");
    boolean onAir = this.isOnAirFlag(sendung);
    boolean grussbox = this.isFlagOne(sendung, "grussbox");
    boolean event = this.isFlagOne(sendung, "event");
    boolean twitch = this.isFlagOne(sendung, "twitch");

    return new ScheduleShow(
        dateStr,
        weekday,
        startTimeStr,
        endTimeStr,
        showname,
        moderator,
        showPicture,
        profilePicture,
        onAir,
        grussbox,
        event,
        twitch);
  }

  /**
   * Website-Flyer (Landscape aus Beschreibung) bevorzugen; sonst {@code showpicture}.
   */
  private @Nullable String resolveShowBannerUrl(JsonObject sendung) {
    String fromDescription = this.firstHtmlImageSrc(this.optionalString(sendung, "beschreibung"));
    if (fromDescription != null && !fromDescription.isBlank()) {
      return fromDescription;
    }
    return this.optionalString(sendung, "showpicture");
  }

  private @Nullable String firstHtmlImageSrc(@Nullable String html) {
    if (html == null || html.isBlank()) {
      return null;
    }
    Matcher matcher = HTML_IMG_SRC.matcher(html);
    if (!matcher.find()) {
      return null;
    }
    String src = matcher.group(1).trim();
    return src.isEmpty() ? null : src;
  }

  /**
   * API-Form: Tag 0 als verschachteltes Array, weitere Tage als {@code {"n":[...]}}.
   */
  private @Nullable JsonArray extractDayEntries(JsonElement dayElement) {
    if (dayElement == null || dayElement.isJsonNull()) {
      return null;
    }
    if (dayElement.isJsonArray()) {
      JsonArray arr = dayElement.getAsJsonArray();
      while (arr.size() == 1 && arr.get(0).isJsonArray()) {
        arr = arr.get(0).getAsJsonArray();
      }
      return arr;
    }
    if (dayElement.isJsonObject()) {
      JsonObject dayObject = dayElement.getAsJsonObject();
      for (String dayKey : dayObject.keySet()) {
        JsonElement dayDataElement = dayObject.get(dayKey);
        if (dayDataElement != null && dayDataElement.isJsonArray()) {
          return dayDataElement.getAsJsonArray();
        }
      }
    }
    return null;
  }

  private @Nullable JsonObject findDayInfo(JsonArray dayDataArray) {
    for (int i = 0; i < dayDataArray.size(); i++) {
      JsonElement element = dayDataArray.get(i);
      if (!element.isJsonObject()) {
        continue;
      }
      JsonObject obj = element.getAsJsonObject();
      if (obj.has("datum")) {
        return obj;
      }
    }
    return null;
  }

  private boolean isShowCancelled(String startTimeStr, String endTimeStr) {
    if (endTimeStr == null || endTimeStr.isEmpty()) {
      return false;
    }

    LocalTime startTime = this.parseTime(startTimeStr);
    LocalTime endTime = this.parseTime(endTimeStr);
    if (startTime == null || endTime == null) {
      return false;
    }

    long durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
    return durationMinutes <= 10;
  }

  /**
   * Chat-Hinweis nur kurz nach dem geplanten Sendungsstart (nicht vorher).
   * Fenster: Startzeit bis +8 Minuten – bei 1-Minuten-Check zuverlässig, ohne Frühwarnung.
   */
  private boolean shouldSendNotification(ScheduleShow show) {
    LocalDate today = LocalDate.now();
    LocalTime now = LocalTime.now();

    LocalDate showDate = this.parseDate(show.getDate());
    LocalTime startTime = this.parseTime(show.getStartTime());
    if (showDate == null || startTime == null || !showDate.equals(today)) {
      return false;
    }

    long minutesSinceStart = ChronoUnit.MINUTES.between(startTime, now);
    return minutesSinceStart >= 0 && minutesSinceStart <= 8;
  }

  private void sendLiveNotification(ScheduleShow show) {
    if (!this.addon.configuration().enabled().get()
        || !this.addon.configuration().showLiveChatNotification().get()) {
      return;
    }

    this.addon.labyAPI().minecraft().executeOnRenderThread(() -> {
      String moderator = show.getModerator() == null || show.getModerator().isBlank()
          ? "EvilRadio"
          : show.getModerator().trim();
      String showName = show.getShowName() == null || show.getShowName().isBlank()
          ? "Live"
          : show.getShowName().trim();
      String timeLabel = formatScheduleTimeLabel(show);

      Component prefix = Component.text("[Evil-Radio] ")
          .color(NamedTextColor.DARK_RED)
          .decorate(TextDecoration.BOLD);

      Component listenButton = Component.translatable("evilradio.schedule.listenButton")
          .color(NamedTextColor.GREEN)
          .decorate(TextDecoration.BOLD, TextDecoration.UNDERLINED)
          .clickEvent(ClickEvent.runCommand("/" + ListenMashupCommand.COMMAND_NAME))
          .hoverEvent(HoverEvent.showText(
              Component.translatable("evilradio.schedule.listenHover")
                  .color(NamedTextColor.GRAY)));

      Component message = Component.empty()
          .append(prefix)
          .append(Component.translatable(
                  "evilradio.schedule.liveMessage",
                  Component.text(moderator).color(NamedTextColor.GOLD),
                  Component.text(showName).color(NamedTextColor.AQUA),
                  Component.text(timeLabel).color(NamedTextColor.YELLOW))
              .color(NamedTextColor.GRAY))
          .append(Component.newline())
          .append(prefix)
          .append(Component.translatable("evilradio.schedule.liveHint")
              .color(NamedTextColor.GRAY))
          .append(Component.space())
          .append(listenButton);

      if (show.isTwitch()) {
        message = message.append(Component.space())
            .append(Component.translatable("evilradio.schedule.twitchUrl")
                .color(TextColor.color(145, 70, 255))
                .clickEvent(ClickEvent.openUrl("https://www.twitch.tv/evilradiode"))
                .hoverEvent(HoverEvent.showText(
                    Component.translatable("evilradio.schedule.twitchHover")
                        .color(NamedTextColor.LIGHT_PURPLE))));
      }

      this.addon.labyAPI().minecraft().chatExecutor().displayClientMessage(message);
    });

    this.logging.info(
        "Live-Benachrichtigung gesendet für Sendung: "
            + show.getShowName()
            + " / "
            + show.getModerator()
            + " um "
            + show.getStartTime()
            + (show.isTwitch() ? " (mit Twitch-Link)" : ""));
  }

  private static String formatScheduleTimeLabel(ScheduleShow show) {
    String start = show.getStartTime();
    String end = show.getEndTime();
    if (end == null || end.isBlank()) {
      return start + " Uhr";
    }
    return start + "–" + end + " Uhr";
  }

  private void runOnRender(@Nullable Runnable onDone) {
    if (onDone == null) {
      return;
    }
    this.addon.labyAPI().minecraft().executeOnRenderThread(onDone);
  }

  private @Nullable String optionalString(JsonObject object, String key) {
    if (!object.has(key) || object.get(key).isJsonNull()) {
      return null;
    }
    String value = object.get(key).getAsString();
    return value == null || value.isBlank() ? null : value;
  }

  private boolean isFlagOne(JsonObject object, String key) {
    if (!object.has(key) || object.get(key).isJsonNull()) {
      return false;
    }
    return "1".equals(object.get(key).getAsString());
  }

  private boolean isOnAirFlag(JsonObject sendung) {
    if (!sendung.has("onair") || sendung.get("onair").isJsonNull()) {
      return false;
    }
    String value = sendung.get("onair").getAsString();
    return value != null && !value.isBlank();
  }

  private @Nullable LocalDate parseDate(String dateStr) {
    try {
      return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
    } catch (Exception e) {
      this.logging.warn("Konnte Datum nicht parsen: " + dateStr);
      return null;
    }
  }

  private @Nullable LocalTime parseTime(String timeStr) {
    try {
      return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
    } catch (Exception e) {
      this.logging.warn("Konnte Uhrzeit nicht parsen: " + timeStr);
      return null;
    }
  }

  public void resetNotification() {
    this.lastNotifiedShowKey = null;
  }
}
