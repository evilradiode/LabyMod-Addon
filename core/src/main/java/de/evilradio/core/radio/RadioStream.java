package de.evilradio.core.radio;

import de.evilradio.core.EvilTextures;
import java.util.Locale;
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
  private final String iconWithLogoUrl;

  private Icon icon;

  public RadioStream(
      int id,
      String azuraCastShortcode,
      String name,
      String displayName,
      String streamUrl,
      String iconUrl,
      String iconWithLogoUrl
  ) {
    this.id = id;
    this.azuraCastShortcode = azuraCastShortcode;
    this.name = name;
    this.displayName = displayName;
    this.url = streamUrl;
    this.iconUrl = iconUrl;
    this.iconWithLogoUrl = iconWithLogoUrl;
  }

  public RadioStream initialize() {
    String resolved = resolveIconUrl(this.iconUrl, this.iconWithLogoUrl);
    if (resolved != null) {
      this.icon = Icon.url(resolved, FALLBACK_ICON);
    } else {
      this.icon = EvilTextures.LOGO;
    }
    return this;
  }

  /**
   * Bevorzugt {@code iconUrl} (Sender-Artwork). Wenn die URL nicht zum Sender passt, aber
   * {@code iconWithLogo} schon (z. B. TechTime noch mit Anime-URL), nimm iconWithLogo.
   */
  String resolveIconUrl(String iconUrl, String iconWithLogoUrl) {
    boolean hasIconUrl = iconUrl != null && !iconUrl.isBlank();
    boolean hasWithLogo = iconWithLogoUrl != null && !iconWithLogoUrl.isBlank();
    if (!hasIconUrl) {
      return hasWithLogo ? iconWithLogoUrl.trim() : null;
    }
    if (!hasWithLogo) {
      return iconUrl.trim();
    }

    String icon = iconUrl.trim();
    String withLogo = iconWithLogoUrl.trim();
    if (urlMatchesStation(icon)) {
      return icon;
    }
    if (urlMatchesStation(withLogo)) {
      return withLogo;
    }
    return icon;
  }

  private boolean urlMatchesStation(String url) {
    String lower = url.toLowerCase(Locale.ROOT);
    for (String token : stationTokens()) {
      if (token.length() >= 3 && lower.contains(token)) {
        return true;
      }
    }
    return false;
  }

  private String[] stationTokens() {
    return new String[]{
        normalizeToken(this.azuraCastShortcode),
        normalizeToken(this.name),
        normalizeToken(this.displayName)
    };
  }

  private static String normalizeToken(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.toLowerCase(Locale.ROOT)
        .replace('&', ' ')
        .replace('-', '_')
        .replace(' ', '_')
        .replace("__", "_");
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

  public String getIconWithLogoUrl() {
    return iconWithLogoUrl;
  }

  /** Für Debug-Logs: welche URL tatsächlich fürs Icon verwendet wird. */
  public String resolvedIconUrl() {
    return resolveIconUrl(this.iconUrl, this.iconWithLogoUrl);
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
