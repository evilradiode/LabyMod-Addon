package de.evilradio.core.group;

import com.google.gson.JsonObject;
import de.evilradio.core.EvilConstants;
import net.labymod.api.util.io.web.request.Request;
import net.labymod.api.util.logging.Logging;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GroupService {

  private final Logging logging = Logging.create("EvilRadioAddon-GroupService");

  private static Map<String, Group> groups = new HashMap<>();
  private static Map<UUID, Group> users = new HashMap<>();

  public void loadGroups() {
    groups.clear();
    users.clear();
    Request.ofGson(JsonObject.class)
        .url(EvilConstants.API_BASE_URL + "/groups")
        .async()
        .connectTimeout(5000)
        .readTimeout(5000)
        .addHeader("User-Agent", "EvilRadio LabyMod 4 Addon")
        .execute(response -> {
          if(response.hasException()) {
            this.logging.error("Failed to load groups", response.exception());
            return;
          }
          JsonObject object = response.get();

          if(object.has("groups") && object.get("groups").isJsonArray()) {
            object.get("groups").getAsJsonArray().forEach(jsonElement -> {
              if(jsonElement.isJsonObject()) {
                JsonObject groupObject = jsonElement.getAsJsonObject();
                Group group = new Group(
                    groupObject.get("id").getAsString(),
                    groupObject.get("sorting").getAsInt(),
                    !groupObject.get("display_name").getAsString().isEmpty() ? groupObject.get("display_name").getAsString() : null,
                    groupObject.get("color").getAsString()
                );
                group.initialize();
                groups.put(group.getId(), group);
              }
            });
          }

          if(object.has("users") && object.get("users").isJsonArray()) {
            object.get("users").getAsJsonArray().forEach(jsonElement -> {
              if(jsonElement.isJsonObject()) {
                JsonObject userObject = jsonElement.getAsJsonObject();
                UUID uuid = UUID.fromString(userObject.get("uuid").getAsString());
                Group group = getGroup(userObject.get("group").getAsString());
                if(group != null) {
                  users.put(uuid, group);
                }
              }
            });
          }

          logging.info("Loaded {} groups and {} users", groups.size(), users.size());

        });
  }

  public static Group getGroup(String id) {
    return groups.getOrDefault(id, null);
  }

  public static Group getGroupFromUser(UUID uuid) {
    return users.getOrDefault(uuid, null);
  }

  public Collection<Group> getGroups() {
    return groups.values();
  }

}
