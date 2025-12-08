package de.evilradio.core.radio;

import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.resources.ResourceLocation;

public class RadioStream {

  private final int id;
  private final String url;
  private final String name;
  private final String iconPath;
  private final String displayName;

  private Icon icon;

  public RadioStream(int id, String name, String displayName, String streamUrl, String iconPath) {
    this.id = id;
    this.name = name;
    this.displayName = displayName;
    this.url = streamUrl;
    this.iconPath = iconPath;
  }

  public RadioStream initialize() {
    if (this.iconPath != null && !this.iconPath.isEmpty()) {
      try {
        // Parse ResourceLocation aus String (Format: "namespace:path")
        String[] parts = this.iconPath.split(":", 2);
        if (parts.length == 2) {
          this.icon = Icon.texture(ResourceLocation.create(parts[0], parts[1]));
        } else {
          // Fallback: verwende als direkten Pfad
          this.icon = Icon.texture(ResourceLocation.create("evilradio", this.iconPath));
        }
      } catch (Exception e) {
        // Bei Fehler: verwende Standard-Icon
        this.icon = Icon.texture(ResourceLocation.create("evilradio", "textures/stations/default.png"));
      }
    }
    return this;
  }

  public int getId() {
    return id;
  }

  public String getUrl() {
    return url;
  }

  public String getName() {
    return name;
  }

  public Icon getIcon() {
    return icon;
  }

  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return name;
  }
}


