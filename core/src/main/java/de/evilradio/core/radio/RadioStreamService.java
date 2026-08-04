package de.evilradio.core.radio;

import com.google.gson.JsonObject;
import de.evilradio.core.EvilRadioAddon;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.logging.Logging;

public class RadioStreamService {

  private final Logging logging = Logging.create("EvilRadio-RadioStreamService");

  private RadioStream lastSelectedStream;

  private final List<RadioStream> streams = new ArrayList<>();
  private final CopyOnWriteArrayList<Runnable> changeListeners = new CopyOnWriteArrayList<>();
  private final EvilRadioAddon addon;

  public RadioStreamService(EvilRadioAddon addon) {
    this.addon = addon;
  }

  public void loadStreams() {
    loadStreams(null);
  }

  public void loadStreams(Runnable callback) {
    String uuid = this.addon.labyAPI().getUniqueId().toString();
    Request.ofGson(JsonObject.class)
        .url("https://api.evil-radio.de/streams?uuid=" + uuid)
        .async()
        .connectTimeout(5000)
        .readTimeout(5000)
        .addHeader("User-Agent", this.addon.apiUserAgent())
        .addHeader("X-Addon-Version", this.addon.addonVersion())
        .execute(response -> {
          if (response.hasException()) {
            logging.error("Failed to load streams", response.exception());
            if (callback != null) {
              this.runOnRenderThread(callback);
            }
            return;
          }
          JsonObject object = response.get();
          List<RadioStream> loaded = new ArrayList<>();
          if (object.has("streams") && object.get("streams").isJsonArray()) {
            object.get("streams").getAsJsonArray().forEach(jsonElement -> {
              if (jsonElement.isJsonObject()) {
                JsonObject streamObject = jsonElement.getAsJsonObject();
                String internalName = null;
                if (streamObject.has("internal_name") && !streamObject.get("internal_name").isJsonNull()) {
                  internalName = streamObject.get("internal_name").getAsString();
                }
                String iconUrl = null;
                if (streamObject.has("iconUrl") && !streamObject.get("iconUrl").isJsonNull()) {
                  iconUrl = streamObject.get("iconUrl").getAsString();
                }
                RadioStream radioStream = new RadioStream(
                    streamObject.get("id").getAsInt(),
                    internalName,
                    streamObject.get("name").getAsString(),
                    streamObject.get("displayName").getAsString(),
                    streamObject.get("streamUrl").getAsString(),
                    iconUrl
                );
                radioStream.initialize();
                loaded.add(radioStream);
              }
            });
          }

          synchronized (this.streams) {
            this.streams.clear();
            this.streams.addAll(loaded);
            this.sortStreamsByUsageLocked();
          }
          logging.info("Loaded " + loaded.size() + " radio streams");
          this.notifyStreamsChanged();
          if (callback != null) {
            this.runOnRenderThread(callback);
          }
        });
  }

  public void addChangeListener(Runnable listener) {
    if (listener != null) {
      this.changeListeners.addIfAbsent(listener);
    }
  }

  public void removeChangeListener(Runnable listener) {
    if (listener != null) {
      this.changeListeners.remove(listener);
    }
  }

  public RadioStream getLastSelectedStream() {
    return lastSelectedStream;
  }

  public void setLastSelectedStream(RadioStream radioStream) {
    this.lastSelectedStream = radioStream;
  }

  /**
   * Snapshot der aktuellen Senderliste (bereits sortiert). Nie die interne Liste zurückgeben.
   */
  public List<RadioStream> streams() {
    synchronized (this.streams) {
      return Collections.unmodifiableList(new ArrayList<>(this.streams));
    }
  }

  public RadioStream findStreamById(int id) {
    synchronized (this.streams) {
      for (RadioStream stream : this.streams) {
        if (stream.getId() == id) {
          return stream;
        }
      }
      return null;
    }
  }

  /**
   * Neu sortieren nach Usage-Änderung (z. B. nach Play).
   */
  public void refreshSortOrder() {
    synchronized (this.streams) {
      this.sortStreamsByUsageLocked();
    }
    this.notifyStreamsChanged();
  }

  private void notifyStreamsChanged() {
    this.runOnRenderThread(() -> {
      for (Runnable listener : this.changeListeners) {
        try {
          listener.run();
        } catch (RuntimeException exception) {
          this.logging.error("Stream change listener failed", exception);
        }
      }
    });
  }

  private void runOnRenderThread(Runnable runnable) {
    if (this.addon.labyAPI().minecraft().isOnRenderThread()) {
      runnable.run();
      return;
    }
    this.addon.labyAPI().minecraft().executeOnRenderThread(runnable);
  }

  private void sortStreamsByUsageLocked() {
    if (this.streams.isEmpty()) {
      return;
    }

    if (!this.addon.configuration().usageBasedSorting().get()) {
      this.streams.sort((stream1, stream2) -> Integer.compare(stream1.getId(), stream2.getId()));
      return;
    }

    this.streams.sort((stream1, stream2) -> {
      int usage1 = this.addon.configuration().usageStatistics().getStreamUsageCount(stream1.getId());
      int usage2 = this.addon.configuration().usageStatistics().getStreamUsageCount(stream2.getId());
      if (usage1 != usage2) {
        return Integer.compare(usage2, usage1);
      }
      return Integer.compare(stream1.getId(), stream2.getId());
    });
  }
}
