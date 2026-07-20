package de.evilradio.core.radio;

import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.resources.ResourceLocation;

public class RadioStream {

  private final int id;
  private final String azuraCastShortcode;
  private final String url;
  private final String name;
  private final String iconPath;
  private final String displayName;
  private final String iconUrl;

  private Icon icon;

  public RadioStream(
      int id,
      String azuraCastShortcode,
      String name,
      String displayName,
      String streamUrl,
      String iconPath,
      String iconUrl
  ) {
    this.id = id;
    this.azuraCastShortcode = azuraCastShortcode;
    this.name = name;
    this.displayName = displayName;
    this.url = streamUrl;
    this.iconPath = iconPath;
    this.iconUrl = iconUrl;
  }

  public RadioStream initialize() {
    if (this.iconPath != null && !this.iconPath.isEmpty()) {
      try {
        String[] parts = this.iconPath.split(":", 2);
        if (parts.length == 2) {
          this.icon = Icon.texture(ResourceLocation.create(parts[0], parts[1]));
        } else {
          this.icon = Icon.texture(ResourceLocation.create("evilradio", this.iconPath));
        }
      } catch (Exception e) {
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

  public String getIconUrl() {
    return iconUrl;
  }

  public String getAzuraCastShortcode() {
    return azuraCastShortcode;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof RadioStream stream)) {
      return false;
    }
    return this.id == stream.id;
  }

  @Override
  public int hashCode() {
    return Integer.hashCode(this.id);
  }
}
