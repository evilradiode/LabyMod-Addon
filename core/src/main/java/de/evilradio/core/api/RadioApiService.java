package de.evilradio.core.api;

import com.google.gson.JsonObject;
import net.labymod.api.util.io.web.request.Request;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class RadioApiService {
  
  private static final String API_BASE_URL = "https://api.evil-radio.de/?radioInfo=";
  
  // Mapping von Kategorie-Namen zu internen API-Namen
  private static final Map<String, String> CATEGORY_TO_INTERNAL_NAME = new HashMap<>();
  
  static {
    CATEGORY_TO_INTERNAL_NAME.put("POP & RAP", "POP");
    CATEGORY_TO_INTERNAL_NAME.put("MASHUP", "Mashup");
    CATEGORY_TO_INTERNAL_NAME.put("SCHLAGER", "Schlager");
    CATEGORY_TO_INTERNAL_NAME.put("OLDIE", "Oldie");
    CATEGORY_TO_INTERNAL_NAME.put("SOMMER", "Summer");
    CATEGORY_TO_INTERNAL_NAME.put("ANIME", "Anime");
    CATEGORY_TO_INTERNAL_NAME.put("XMAS", "Xmas");
  }
  
  /**
   * Konvertiert einen Kategorie-Namen zu einem internen API-Namen.
   */
  public static String getInternalName(String category) {
    return CATEGORY_TO_INTERNAL_NAME.getOrDefault(category, category);
  }
  
  /**
   * Ruft die API auf, um den aktuellen Song eines Senders abzurufen.
   * 
   * @param internalName Der interne Name des Senders (z.B. "Mashup", "POP", etc.)
   * @param callback Callback, das mit der API-Antwort aufgerufen wird
   */
  public static void fetchCurrentSong(String internalName, Consumer<RadioApiResponse> callback) {
    if (internalName == null || internalName.isEmpty()) {
      callback.accept(null);
      return;
    }
    
    String url = API_BASE_URL + internalName;
    
    Request.ofGson(JsonObject.class)
        .url(url)
        .async()
        .connectTimeout(5000)
        .readTimeout(5000)
        .userAgent("EvilRadio LabyMod 4 Addon")
        .execute(response -> {
          if (response.getStatusCode() != 200 || response.hasException()) {
            callback.accept(null);
            return;
          }
          
          try {
            JsonObject json = response.get();
            RadioApiResponse apiResponse = new RadioApiResponse(json);
            callback.accept(apiResponse);
          } catch (Exception e) {
            callback.accept(null);
          }
        });
  }
}

