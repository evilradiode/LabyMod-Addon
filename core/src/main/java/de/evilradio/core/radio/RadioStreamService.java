package de.evilradio.core.radio;

import com.google.gson.JsonObject;
import de.evilradio.core.EvilRadioAddon;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.logging.Logging;
import java.util.ArrayList;
import java.util.List;

public class RadioStreamService {

  private final Logging logging = Logging.create("EvilRadio-RadioStreamService");

  private List<RadioStream> streams = new ArrayList<>();
  private EvilRadioAddon addon;

  public RadioStreamService(EvilRadioAddon addon) {
    this.addon = addon;
  }

  public void loadStreams() {
    loadStreams(null);
  }

  public void loadStreams(Runnable callback) {
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
            if (callback != null) {
              callback.run();
            }
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
            // Sortiere Streams nach Nutzung
            sortStreamsByUsage();
          }
          logging.info("Loaded " + streams.size() + " radio streams");
          if (callback != null) {
            callback.run();
          }
        });
  }

  public List<RadioStream> streams() {
    // Sortiere Streams nach Nutzung, bevor sie zurückgegeben werden
    sortStreamsByUsage();
    return streams;
  }
  
  public RadioStream findStreamById(int id) {
    return streams.stream()
        .filter(stream -> stream.getId() == id)
        .findFirst()
        .orElse(null);
  }
  
  private void sortStreamsByUsage() {
    if (addon == null || streams == null || streams.isEmpty()) {
      return;
    }
    
    // Prüfe, ob Usage-basierte Sortierung aktiviert ist
    if (!addon.configuration().usageBasedSorting().get()) {
      // Wenn deaktiviert: Sortiere nach ID (Standard-Sortierung)
      streams.sort((stream1, stream2) -> Integer.compare(stream1.getId(), stream2.getId()));
      return;
    }
    
    // Usage-basierte Sortierung
    streams.sort((stream1, stream2) -> {
      int usage1 = addon.configuration().getStreamUsageCount(stream1.getId());
      int usage2 = addon.configuration().getStreamUsageCount(stream2.getId());
      
      // Sortiere absteigend nach Nutzung (höchste zuerst)
      // Bei gleicher Nutzung: sortiere nach ID für konsistente Reihenfolge
      if (usage1 != usage2) {
        return Integer.compare(usage2, usage1);
      }
      return Integer.compare(stream1.getId(), stream2.getId());
    });
  }

}
