package de.evilradio.core.sharing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.radio.RadioManager;
import de.evilradio.core.radio.RadioStream;
import net.labymod.api.client.session.Session;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.network.playerinfo.PlayerInfoRemoveEvent;
import net.labymod.api.event.client.world.WorldLeaveEvent;
import net.labymod.api.event.client.world.WorldLoadEvent;
import net.labymod.api.event.labymod.labyconnect.LabyConnectStateUpdateEvent;
import net.labymod.api.event.labymod.labyconnect.session.LabyConnectBroadcastEvent;
import net.labymod.api.event.labymod.labyconnect.session.LabyConnectBroadcastEvent.Action;
import net.labymod.api.labyconnect.LabyConnectSession;
import net.labymod.api.labyconnect.protocol.LabyConnectState;
import net.labymod.api.util.Debounce;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RadioStreamSharingController {

  public static final long BROADCAST_DELAY = 1000L;

  private final Map<UUID, SharedRadioStream> sharedRadioStreams = new HashMap<>();

  private EvilRadioAddon addon;

  public RadioStreamSharingController(EvilRadioAddon addon) {
    this.addon = addon;

    this.addon.configuration().enabled().addChangeListener((enabled) -> this.broadcastCurrentStream());
    this.addon.configuration().nameTagSettings().shareCurrentStream().addChangeListener((enabled) -> this.broadcastCurrentStream());
  }

  public void broadcastCurrentStream() {
    Debounce.of("evilRadioBroadcast", BROADCAST_DELAY, () -> {
      RadioManager radioManager = addon.radioManager();
      RadioStream radioStream = radioManager.getCurrentStream();

      boolean visible = radioStream != null
          && this.addon.configuration().enabled().get()
          && this.addon.configuration().nameTagSettings().shareCurrentStream().get()
          && radioManager.isPlaying();

      JsonObject jsonObject = new JsonObject();
      jsonObject.addProperty("streamId", visible ? radioStream.getName() : null);

      LabyConnectSession session = this.addon.labyAPI().labyConnect().getSession();
      if(session != null && session.isAuthenticated()) {
        session.sendBroadcastPayload("evilradio-stream-sharing", jsonObject);
      }

    });
  }

  @Subscribe
  public void onBroadcastReceive(LabyConnectBroadcastEvent event) {
    Session session = this.addon.labyAPI().minecraft().sessionAccessor().getSession();
    boolean isSelf = session != null && session.getUniqueId().equals(event.getSender());
    if(event.action() != Action.RECEIVE && !isSelf) return;
    if(!event.getKey().equals("evilradio-stream-sharing")) return;
    JsonElement payload = event.getPayload();
    if(!payload.isJsonObject()) return;
    JsonObject jsonObject = payload.getAsJsonObject();

    String streamId = null;
    if(jsonObject.has("streamId") && jsonObject.get("streamId").isJsonPrimitive()) {
      JsonPrimitive jsonPrimitive = jsonObject.get("streamId").getAsJsonPrimitive();
      if(jsonPrimitive.isString()) {
        streamId = jsonPrimitive.getAsString();
      }
    }

    if(streamId == null || this.addon.radioStreamService().findStreamByName(streamId) == null) {
      this.sharedRadioStreams.remove(event.getSender());
      return;
    }

    SharedRadioStream sharedRadioStream = this.sharedRadioStreams.get(event.getSender());
    if(sharedRadioStream == null || !sharedRadioStream.getStreamId().equals(streamId)) {
      this.sharedRadioStreams.put(event.getSender(), new SharedRadioStream(streamId, event.getSender()));
    }
  }

  @Subscribe
  public void onWorldLoad(WorldLoadEvent event) {
    this.broadcastCurrentStream();
  }

  @Subscribe
  public void onWorldLeave(WorldLeaveEvent event) {
    this.sharedRadioStreams.clear();
  }

  @Subscribe
  public void onLabyConnectStateUpdate(LabyConnectStateUpdateEvent event) {
    if(event.state() != LabyConnectState.PLAY) return;
    this.broadcastCurrentStream();
  }

  @Subscribe
  public void onPlayerInfoRemove(PlayerInfoRemoveEvent event) {
    this.sharedRadioStreams.remove(event.playerInfo().profile().getUniqueId());
  }

  public SharedRadioStream getSharedStreamOf(UUID uuid) {
    return this.sharedRadioStreams.get(uuid);
  }

  public class SharedRadioStream {

    private final String streamId;
    private final UUID uuid;

    public SharedRadioStream(String streamId, UUID uuid) {
      this.streamId = streamId;
      this.uuid = uuid;
    }

    public String getStreamId() {
      return streamId;
    }

    public UUID getUuid() {
      return uuid;
    }
  }

}
