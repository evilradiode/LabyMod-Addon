package de.evilradio.core.schedule;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.evilradio.core.EvilRadioAddon;
import net.labymod.api.client.component.Component;
import net.labymod.api.util.concurrent.task.Task;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.logging.Logging;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ScheduleService {

  private final String SCHEDULE_API_URL = "https://sp.evil-radio.de/index.php?page=infos&mode=json&opt=sendeplan";
  
  private final Logging logging = Logging.create("EvilRadio-ScheduleService");
  
  private EvilRadioAddon addon;
  private Task scheduleCheckTask;
  private Task hourlyUpdateTask;
  
  // Lokal gespeicherte Sendungen (beim Start abgerufen)
  private List<ScheduleShow> cachedShows = new ArrayList<>();
  
  // Trackt, welche Sendung bereits benachrichtigt wurde (Datum + Startzeit als Key)
  private String lastNotifiedShowKey = null;
  
  public ScheduleService(EvilRadioAddon addon) {
    this.addon = addon;
  }
  
  public void startScheduleChecker() {
    // Beim Start einmal die Zeiten abrufen und lokal speichern
    loadAndCacheSchedule();
    
    // Prüfe alle 5 Minuten den Sendeplan (für Live-Erkennung)
    // Benachrichtigungen werden unabhängig davon gesendet, ob der Radio läuft
    this.scheduleCheckTask = Task.builder(() -> {
      checkSchedule();
    }).repeat(5, TimeUnit.MINUTES).build();
    this.scheduleCheckTask.execute();
    
    // Stündliches Update von der API (falls ein Stream ausfällt)
    this.hourlyUpdateTask = Task.builder(() -> {
      loadAndCacheSchedule();
    }).repeat(1, TimeUnit.HOURS).build();
    this.hourlyUpdateTask.execute();
    
    // Führe auch sofort eine Prüfung durch
    checkSchedule();
  }
  
  public void stopScheduleChecker() {
    if (this.scheduleCheckTask != null) {
      this.scheduleCheckTask.cancel();
    }
    if (this.hourlyUpdateTask != null) {
      this.hourlyUpdateTask.cancel();
    }
  }
  
  /**
   * Lädt den Sendeplan von der API und speichert ihn lokal
   */
  private void loadAndCacheSchedule() {
    Request.ofGson(JsonArray.class)
        .url(SCHEDULE_API_URL)
        .async()
        .connectTimeout(5000)
        .readTimeout(5000)
        .userAgent("EvilRadio LabyMod 4 Addon")
        .execute(response -> {
          if (response.hasException() || response.getStatusCode() != 200) {
            logging.error("Failed to load schedule", response.hasException() ? response.exception() : new Exception("HTTP " + response.getStatusCode()));
            return;
          }
          
          JsonArray scheduleArray = response.get();
          if (scheduleArray == null || scheduleArray.size() == 0) {
            return;
          }
          
          // Speichere alle Sendungen lokal
          this.cachedShows = parseAllShows(scheduleArray);
          logging.info("Sendeplan geladen und lokal gespeichert: " + this.cachedShows.size() + " Sendungen");
        });
  }
  
  /**
   * Prüft den Sendeplan auf anstehende Sendungen und sendet Benachrichtigungen
   */
  private void checkSchedule() {
    // Verwende zuerst die lokal gespeicherten Sendungen
    if (!this.cachedShows.isEmpty()) {
      checkCachedShows();
    }
    
    // TODO: Beim Senden der Ankündigung nochmal in dem Moment die API gegenprüfen,
    // damit nicht eine Show angekündigt wird, die kurzfristig abgesagt wurde
    // Aktualisiere auch die lokalen Daten für die nächste Prüfung
    Request.ofGson(JsonArray.class)
        .url(SCHEDULE_API_URL)
        .async()
        .connectTimeout(5000)
        .readTimeout(5000)
        .userAgent("EvilRadio LabyMod 4 Addon")
        .execute(response -> {
          if (response.hasException() || response.getStatusCode() != 200) {
            logging.error("Failed to load schedule", response.hasException() ? response.exception() : new Exception("HTTP " + response.getStatusCode()));
            return;
          }
          
          JsonArray scheduleArray = response.get();
          if (scheduleArray == null || scheduleArray.size() == 0) {
            return;
          }
          
          // Finde die aktuelle oder nächste Sendung (auch ohne Twitch)
          ScheduleShow currentShow = findCurrentOrNextShow(scheduleArray);
          
          if (currentShow != null) {
            // Prüfe, ob die Sendung gerade startet (live geht)
            if (shouldSendNotification(currentShow)) {
              String showKey = currentShow.getDate() + "_" + currentShow.getStartTime();
              
              // Sende nur, wenn diese Sendung noch nicht benachrichtigt wurde
              if (!showKey.equals(this.lastNotifiedShowKey)) {
                // TODO: Hier nochmal die API gegenprüfen, ob die Sendung nicht abgesagt wurde
                // Prüfe die aktuelle API-Antwort, ob die Sendung noch existiert und nicht abgesagt wurde
                if (isShowStillValid(scheduleArray, currentShow)) {
                  this.lastNotifiedShowKey = showKey;
                  sendLiveNotification(currentShow);
                } else {
                  logging.info("Sendung wurde kurzfristig abgesagt: " + currentShow.getShowName());
                }
              }
            } else {
              // Wenn die Sendung vorbei ist, setze die Flag zurück für die nächste Sendung
              if (isShowFinished(currentShow)) {
                this.lastNotifiedShowKey = null;
              }
            }
          }
          
          // Aktualisiere auch die lokalen Daten
          this.cachedShows = parseAllShows(scheduleArray);
        });
  }
  
  /**
   * Prüft die lokal gespeicherten Sendungen auf anstehende Live-Sendungen
   */
  private void checkCachedShows() {
    LocalDate today = LocalDate.now();
    LocalTime now = LocalTime.now();
    
    for (ScheduleShow show : this.cachedShows) {
      LocalDate showDate = parseDate(show.getDate());
      LocalTime startTime = parseTime(show.getStartTime());
      
      if (showDate == null || startTime == null) {
        continue;
      }
      
      // Prüfe nur Sendungen von heute
      if (!showDate.equals(today)) {
        continue;
      }
      
      // Prüfe, ob die Sendung gerade startet (innerhalb von 10 Minuten vor Start bis 5 Minuten nach Start)
      LocalTime startTimeMinus10 = startTime.minusMinutes(10);
      LocalTime startTimePlus5 = startTime.plusMinutes(5);
      
      if ((now.isAfter(startTimeMinus10) || now.equals(startTimeMinus10)) && 
          (now.isBefore(startTimePlus5) || now.equals(startTimePlus5))) {
        String showKey = show.getDate() + "_" + show.getStartTime();
        
        // Sende nur, wenn diese Sendung noch nicht benachrichtigt wurde
        if (!showKey.equals(this.lastNotifiedShowKey)) {
          this.lastNotifiedShowKey = showKey;
          sendLiveNotification(show);
        }
      }
    }
  }
  
  /**
   * Parst alle Sendungen aus der API-Antwort
   */
  private List<ScheduleShow> parseAllShows(JsonArray scheduleArray) {
    List<ScheduleShow> shows = new ArrayList<>();
    LocalDate today = LocalDate.now();
    
    // Durchsuche alle Tage im Sendeplan
    for (JsonElement dayElement : scheduleArray) {
      if (!dayElement.isJsonObject()) {
        continue;
      }
      
      JsonObject dayObject = dayElement.getAsJsonObject();
      
      // Iteriere über alle Keys im Tag-Objekt (z.B. "0", "1", "2", etc.)
      for (String dayKey : dayObject.keySet()) {
        JsonElement dayDataElement = dayObject.get(dayKey);
        if (!dayDataElement.isJsonArray()) {
          continue;
        }
        
        JsonArray dayDataArray = dayDataElement.getAsJsonArray();
        if (dayDataArray.size() == 0) {
          continue;
        }
        
        // Hole das erste Element, das die Tagesinformationen enthält
        JsonElement firstElement = dayDataArray.get(0);
        if (!firstElement.isJsonObject()) {
          continue;
        }
        
        JsonObject dayInfo = firstElement.getAsJsonObject();
        if (!dayInfo.has("datum")) {
          continue;
        }
        
        String dateStr = dayInfo.get("datum").getAsString();
        LocalDate showDate = parseDate(dateStr);
        
        // Speichere nur Sendungen von heute und den nächsten 7 Tagen
        if (showDate == null || showDate.isBefore(today) || showDate.isAfter(today.plusDays(7))) {
          continue;
        }
        
        // Durchsuche alle Sendungen an diesem Tag
        for (int i = 0; i < dayDataArray.size(); i++) {
          JsonElement showElement = dayDataArray.get(i);
          if (!showElement.isJsonObject()) {
            continue;
          }
          
          JsonObject showObj = showElement.getAsJsonObject();
          if (!showObj.has("sendungen")) {
            continue;
          }
          
          JsonElement sendungenElement = showObj.get("sendungen");
          if (!sendungenElement.isJsonObject()) {
            continue;
          }
          
          JsonObject sendung = sendungenElement.getAsJsonObject();
          
          if (!sendung.has("von")) {
            continue;
          }
          
          String startTimeStr = sendung.get("von").getAsString();
          String endTimeStr = sendung.has("bis") ? sendung.get("bis").getAsString() : null;
          String showname = sendung.has("showname") ? sendung.get("showname").getAsString() : "Unbekannte Sendung";
          String moderator = sendung.has("moderator") ? sendung.get("moderator").getAsString() : "Unbekannt";
          
          // Prüfe, ob es eine Twitch-Sendung ist
          boolean isTwitch = sendung.has("twitch") && sendung.get("twitch").getAsString().equals("1");
          
          // Überspringe abgesagte Sendungen (Dauer ≤ 10 Minuten)
          if (isShowCancelled(startTimeStr, endTimeStr)) {
            continue;
          }
          
          shows.add(new ScheduleShow(dateStr, startTimeStr, endTimeStr, showname, moderator, isTwitch));
        }
      }
    }
    
    return shows;
  }
  
  /**
   * Prüft, ob eine Sendung abgesagt wurde (Dauer zwischen "von" und "bis" ≤ 10 Minuten)
   */
  private boolean isShowCancelled(String startTimeStr, String endTimeStr) {
    if (endTimeStr == null || endTimeStr.isEmpty()) {
      // Wenn kein "bis" Feld vorhanden ist, ist die Sendung nicht abgesagt
      return false;
    }
    
    LocalTime startTime = parseTime(startTimeStr);
    LocalTime endTime = parseTime(endTimeStr);
    
    if (startTime == null || endTime == null) {
      return false;
    }
    
    // Berechne die Dauer in Minuten
    long durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
    
    // Wenn die Dauer ≤ 10 Minuten ist, ist die Sendung abgesagt
    return durationMinutes <= 10;
  }
  
  /**
   * Prüft, ob eine Sendung in der API-Antwort noch gültig ist (nicht abgesagt wurde)
   */
  private boolean isShowStillValid(JsonArray scheduleArray, ScheduleShow show) {
    LocalDate today = LocalDate.now();
    
    for (JsonElement dayElement : scheduleArray) {
      if (!dayElement.isJsonObject()) {
        continue;
      }
      
      JsonObject dayObject = dayElement.getAsJsonObject();
      
      for (String dayKey : dayObject.keySet()) {
        JsonElement dayDataElement = dayObject.get(dayKey);
        if (!dayDataElement.isJsonArray()) {
          continue;
        }
        
        JsonArray dayDataArray = dayDataElement.getAsJsonArray();
        if (dayDataArray.size() == 0) {
          continue;
        }
        
        JsonElement firstElement = dayDataArray.get(0);
        if (!firstElement.isJsonObject()) {
          continue;
        }
        
        JsonObject dayInfo = firstElement.getAsJsonObject();
        if (!dayInfo.has("datum")) {
          continue;
        }
        
        String dateStr = dayInfo.get("datum").getAsString();
        if (!dateStr.equals(show.getDate())) {
          continue;
        }
        
        for (int i = 0; i < dayDataArray.size(); i++) {
          JsonElement showElement = dayDataArray.get(i);
          if (!showElement.isJsonObject()) {
            continue;
          }
          
          JsonObject showObj = showElement.getAsJsonObject();
          if (!showObj.has("sendungen")) {
            continue;
          }
          
          JsonElement sendungenElement = showObj.get("sendungen");
          if (!sendungenElement.isJsonObject()) {
            continue;
          }
          
          JsonObject sendung = sendungenElement.getAsJsonObject();
          
          if (!sendung.has("von")) {
            continue;
          }
          
          String startTimeStr = sendung.get("von").getAsString();
          String endTimeStr = sendung.has("bis") ? sendung.get("bis").getAsString() : null;
          
          if (startTimeStr.equals(show.getStartTime())) {
            // Sendung gefunden - prüfe, ob sie abgesagt wurde (Dauer ≤ 10 Minuten)
            if (isShowCancelled(startTimeStr, endTimeStr)) {
              return false; // Sendung ist abgesagt
            }
            // Sendung gefunden und nicht abgesagt - sie ist noch gültig
            return true;
          }
        }
      }
    }
    
    // Sendung nicht gefunden - möglicherweise abgesagt
    return false;
  }
  
  /**
   * Findet die aktuelle oder nächste Sendung (auch ohne Twitch)
   */
  private ScheduleShow findCurrentOrNextShow(JsonArray scheduleArray) {
    LocalDate today = LocalDate.now();
    LocalTime now = LocalTime.now();
    
    // Durchsuche alle Tage im Sendeplan
    for (JsonElement dayElement : scheduleArray) {
      if (!dayElement.isJsonObject()) {
        continue;
      }
      
      JsonObject dayObject = dayElement.getAsJsonObject();
      
      // Iteriere über alle Keys im Tag-Objekt (z.B. "0", "1", "2", etc.)
      for (String dayKey : dayObject.keySet()) {
        JsonElement dayDataElement = dayObject.get(dayKey);
        if (!dayDataElement.isJsonArray()) {
          continue;
        }
        
        JsonArray dayDataArray = dayDataElement.getAsJsonArray();
        if (dayDataArray.size() == 0) {
          continue;
        }
        
        // Hole das erste Element, das die Tagesinformationen enthält
        JsonElement firstElement = dayDataArray.get(0);
        if (!firstElement.isJsonObject()) {
          continue;
        }
        
        JsonObject dayInfo = firstElement.getAsJsonObject();
        if (!dayInfo.has("datum")) {
          continue;
        }
        
        String dateStr = dayInfo.get("datum").getAsString();
        LocalDate showDate = parseDate(dateStr);
        
        // Prüfe nur heute und morgen (um nicht zu weit in die Zukunft zu schauen)
        if (showDate.isBefore(today) || showDate.isAfter(today.plusDays(1))) {
          continue;
        }
        
        // Durchsuche alle Sendungen an diesem Tag
        for (int i = 0; i < dayDataArray.size(); i++) {
          JsonElement showElement = dayDataArray.get(i);
          if (!showElement.isJsonObject()) {
            continue;
          }
          
          JsonObject showObj = showElement.getAsJsonObject();
          if (!showObj.has("sendungen")) {
            continue;
          }
          
          JsonElement sendungenElement = showObj.get("sendungen");
          if (!sendungenElement.isJsonObject()) {
            continue;
          }
          
          JsonObject sendung = sendungenElement.getAsJsonObject();
          
          if (!sendung.has("von")) {
            continue;
          }
          
          String startTimeStr = sendung.get("von").getAsString();
          String endTimeStr = sendung.has("bis") ? sendung.get("bis").getAsString() : null;
          
          // Überspringe abgesagte Sendungen (Dauer ≤ 10 Minuten)
          if (isShowCancelled(startTimeStr, endTimeStr)) {
            continue;
          }
          
          LocalTime startTime = parseTime(startTimeStr);
          
          if (startTime == null) {
            continue;
          }
          
          // Wenn es heute ist, prüfe ob die Sendung bereits gestartet hat oder gerade startet
          // Wenn es morgen ist, nimm die erste Sendung
          if (showDate.equals(today)) {
            // Prüfe, ob die Sendung bereits gestartet hat oder in den nächsten 10 Minuten startet
            if (now.isAfter(startTime.minusMinutes(10)) || now.equals(startTime) || now.isBefore(startTime.plusHours(4))) {
              String showname = sendung.has("showname") ? sendung.get("showname").getAsString() : "Unbekannte Sendung";
              String moderator = sendung.has("moderator") ? sendung.get("moderator").getAsString() : "Unbekannt";
              
              // Prüfe, ob es eine Twitch-Sendung ist
              boolean isTwitch = sendung.has("twitch") && sendung.get("twitch").getAsString().equals("1");
              
              return new ScheduleShow(dateStr, startTimeStr, endTimeStr, showname, moderator, isTwitch);
            }
          } else if (showDate.equals(today.plusDays(1))) {
            // Für morgen: Nimm die erste Sendung
            String showname = sendung.has("showname") ? sendung.get("showname").getAsString() : "Unbekannte Sendung";
            String moderator = sendung.has("moderator") ? sendung.get("moderator").getAsString() : "Unbekannt";
            
            // Prüfe, ob es eine Twitch-Sendung ist
            boolean isTwitch = sendung.has("twitch") && sendung.get("twitch").getAsString().equals("1");
            
            return new ScheduleShow(dateStr, startTimeStr, endTimeStr, showname, moderator, isTwitch);
          }
        }
      }
    }
    
    return null;
  }
  
  private boolean shouldSendNotification(ScheduleShow show) {
    LocalDate today = LocalDate.now();
    LocalTime now = LocalTime.now();
    
    LocalDate showDate = parseDate(show.getDate());
    LocalTime startTime = parseTime(show.getStartTime());
    
    if (showDate == null || startTime == null) {
      return false;
    }
    
    // Wenn es heute ist und die Startzeit erreicht wurde (mit 10 Minuten Toleranz)
    if (showDate.equals(today)) {
      LocalTime startTimeMinus10 = startTime.minusMinutes(10);
      LocalTime startTimePlus30 = startTime.plusMinutes(30);
      
      // Sende, wenn wir innerhalb von 10 Minuten vor Start bis 30 Minuten nach Start sind
      return (now.isAfter(startTimeMinus10) || now.equals(startTimeMinus10)) && 
             (now.isBefore(startTimePlus30) || now.equals(startTimePlus30));
    }
    
    return false;
  }
  
  private boolean isShowFinished(ScheduleShow show) {
    LocalDate today = LocalDate.now();
    LocalTime now = LocalTime.now();
    
    LocalDate showDate = parseDate(show.getDate());
    LocalTime startTime = parseTime(show.getStartTime());
    
    if (showDate == null || startTime == null) {
      return false;
    }
    
    // Wenn es heute ist und die Startzeit + 30 Minuten vorbei ist
    if (showDate.equals(today)) {
      return now.isAfter(startTime.plusMinutes(30));
    }
    
    // Wenn es ein vergangener Tag ist
    return showDate.isBefore(today);
  }
  
  /**
   * Sendet eine Chat-Nachricht, wenn jemand live geht
   */
  private void sendLiveNotification(ScheduleShow show) {
    this.addon.labyAPI().minecraft().executeOnRenderThread(() -> {
      Component message = Component.text("EvilRadio ist jetzt live! ")
          .color(net.labymod.api.client.component.format.NamedTextColor.GRAY);
      
      // Wenn twitch=1 in der API, füge Twitch-Link hinzu
      if (show.isTwitch()) {
        message = message.append(Component.text("https://www.twitch.tv/evilradiode")
            .color(net.labymod.api.client.component.format.TextColor.color(145, 70, 255))); // Twitch-Farbe
      }
      
      this.addon.labyAPI().minecraft().chatExecutor().displayClientMessage(message);
    });
    
    logging.info("Live-Benachrichtigung gesendet für Sendung: " + show.getShowName() + " um " + show.getStartTime() + 
        (show.isTwitch() ? " (mit Twitch-Link)" : ""));
  }
  
  private LocalDate parseDate(String dateStr) {
    try {
      return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
    } catch (Exception e) {
      logging.warn("Konnte Datum nicht parsen: " + dateStr);
      return null;
    }
  }
  
  private LocalTime parseTime(String timeStr) {
    try {
      return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
    } catch (Exception e) {
      logging.warn("Konnte Uhrzeit nicht parsen: " + timeStr);
      return null;
    }
  }
  
  /**
   * Setzt die Benachrichtigungs-Flag zurück (z.B. wenn der Stream gestoppt wird)
   */
  public void resetNotification() {
    this.lastNotifiedShowKey = null;
  }
  
  private static class ScheduleShow {
    private final String date;
    private final String startTime;
    private final String endTime;
    private final String showName;
    private final String moderator;
    private final boolean isTwitch;
    
    public ScheduleShow(String date, String startTime, String endTime, String showName, String moderator, boolean isTwitch) {
      this.date = date;
      this.startTime = startTime;
      this.endTime = endTime;
      this.showName = showName;
      this.moderator = moderator;
      this.isTwitch = isTwitch;
    }
    
    public String getDate() {
      return date;
    }
    
    public String getStartTime() {
      return startTime;
    }
    
    public String getEndTime() {
      return endTime;
    }
    
    public String getShowName() {
      return showName;
    }
    
    public String getModerator() {
      return moderator;
    }
    
    public boolean isTwitch() {
      return isTwitch;
    }
  }
}

