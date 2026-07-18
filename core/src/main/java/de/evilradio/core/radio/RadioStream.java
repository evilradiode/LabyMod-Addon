package de.evilradio.core.radio;

import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.resources.ResourceLocation;

public class RadioStream {

  private final int id;
  private final String url;
  private final String name;
  private final String iconPath;
  private final String displayName;
  private final String iconUrl;
  private final String azuraCastShortcode;

  private Icon icon;

  public RadioStream(
      int id,
      String name,
      String displayName,
      String streamUrl,
      String iconPath,
      String iconUrl
  ) {
    this(id, name, displayName, streamUrl, iconPath, iconUrl, null);
  }

  public RadioStream(
      int id,
      String name,
      String displayName,
      String streamUrl,
      String iconPath,
      String iconUrl,
      String azuraCastShortcode
  ) {
    this.id = id;
    this.name = name;
    this.displayName = displayName;
    this.url = streamUrl;
    this.iconPath = iconPath;
    this.iconUrl = iconUrl;
    this.azuraCastShortcode = StationShortcodes.resolve(azuraCastShortcode, name, displayName);
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

  /**
   * AzuraCast-Shortcode (z.B. {@code oldi}, {@code pop_und_rap}). Nie den Anzeigenamen verwenden.
   */
  public String getAzuraCastShortcode() {
    return azuraCastShortcode;
  }

  public boolean hasAzuraCastShortcode() {
    return StationShortcodes.isValidShortcode(azuraCastShortcode);
  }

  @Override
  public String toString() {
    return name;
  }
}
