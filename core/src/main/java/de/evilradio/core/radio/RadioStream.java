package de.evilradio.core.radio;

import de.evilradio.core.EvilTextures;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.resources.ResourceLocation;

public class RadioStream {

  private static final ResourceLocation FALLBACK_ICON =
      ResourceLocation.create("evilradio", "textures/logo.png");

  private final int id;
  private final String azuraCastShortcode;
  private final String url;
  private final String name;
  private final String displayName;
  private final String iconUrl;

  private Icon icon;

  public RadioStream(
      int id,
      String azuraCastShortcode,
      String name,
      String displayName,
      String streamUrl,
      String iconUrl
  ) {
    this.id = id;
    this.azuraCastShortcode = azuraCastShortcode;
    this.name = name;
    this.displayName = displayName;
    this.url = streamUrl;
    this.iconUrl = iconUrl;
  }

  public RadioStream initialize() {
    if (this.iconUrl != null && !this.iconUrl.isBlank()) {
      this.icon = Icon.url(this.iconUrl, FALLBACK_ICON);
    } else {
      this.icon = EvilTextures.LOGO;
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
