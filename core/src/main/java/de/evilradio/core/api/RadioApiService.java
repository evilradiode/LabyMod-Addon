package de.evilradio.core.api;

import com.google.gson.JsonObject;
import net.labymod.api.util.io.web.request.Request;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class RadioApiService {

  private static final String API_BASE_URL = "https://api.evil-radio.de/?radioInfo=";

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

