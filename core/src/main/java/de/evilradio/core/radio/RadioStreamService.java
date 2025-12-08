package de.evilradio.core.radio;

import com.google.gson.JsonObject;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.logging.Logging;
import java.util.ArrayList;
import java.util.List;

public class RadioStreamService {

  private final Logging logging = Logging.create("EvilRadio-RadioStreamService");

  private List<RadioStream> streams = new ArrayList<>();

  public void loadStreams() {
    streams.clear();
    Request.ofGson(JsonObject.class)
        .url("https://api.evil-radio.de/laby-addon/")
        .async()
        .connectTimeout(5000)
        .readTimeout(5000)
        .addHeader("User-Agent", "EvilRadio LabyMod 4 Addon")
        .execute(response -> {
          if(response.hasException()) {
            logging.error("Failed to load streams", response.exception());
            return;
          }
          JsonObject object = response.get();
          if(object.has("streams") && object.get("streams").isJsonArray()) {
            object.get("streams").getAsJsonArray().forEach(jsonElement -> {
              if(jsonElement.isJsonObject()) {
                JsonObject streamObject = jsonElement.getAsJsonObject();
                RadioStream radioStream = new RadioStream(
                    streamObject.get("id").getAsInt(),
                    streamObject.get("name").getAsString(),
                    streamObject.get("displayName").getAsString(),
                    streamObject.get("streamUrl").getAsString(),
                    streamObject.get("iconPath").getAsString()
                );
                radioStream.initialize();
                streams.add(radioStream);
              }
            });
          }
          logging.info("Loaded " + streams.size() + " radio streams");
        });
  }

  public List<RadioStream> streams() {
    return streams;
  }

}
