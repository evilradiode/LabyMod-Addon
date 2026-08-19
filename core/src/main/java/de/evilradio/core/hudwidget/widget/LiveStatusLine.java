package de.evilradio.core.hudwidget.widget;

import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.song.CurrentSong;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.TextColor;
import net.labymod.api.util.I18n;

/**
 * On-Air-/Twitch-Zeile für Mashup. Bei beiden Flags abwechselnd nur eines anzeigen,
 * damit die Statuszeile kurz bleibt und der Songtitel Platz hat.
 */
public final class LiveStatusLine {

  static final long ROTATE_MS = 3000L;
  private static final TextColor TWITCH_COLOR = TextColor.color(145, 70, 255);

  private LiveStatusLine() {
  }

  public static boolean showTwitchPhase(long nowMs) {
    return ((nowMs / ROTATE_MS) & 1L) == 1L;
  }

  static boolean isMashup(RadioStream stream) {
    return stream != null && stream.getName() != null && stream.getName().equalsIgnoreCase("Mashup");
  }

  public static boolean hasLiveBadges(CurrentSong song) {
    return song != null && (song.isOnAir() || song.isTwitch());
  }

  /**
   * @param preferTwitch {@code true} → Twitch-Phase, {@code false} → On-Air-Phase
   *                     (nur relevant wenn beides aktiv)
   */
  public static Component buildPrefix(RadioStream stream, CurrentSong song, boolean preferTwitch) {
    if (!isMashup(stream) || song == null) {
      return null;
    }
    return buildBadges(song.isOnAir(), song.isTwitch(), song.getModeratorName(), preferTwitch);
  }

  public static Component buildBadges(
      boolean onAir, boolean twitch, String moderatorName, boolean preferTwitch) {
    if (!onAir && !twitch) {
      return null;
    }

    boolean showTwitch;
    boolean showOnAir;
    if (onAir && twitch) {
      showTwitch = preferTwitch;
      showOnAir = !preferTwitch;
    } else {
      showTwitch = twitch;
      showOnAir = onAir;
    }

    Component line = Component.empty();
    boolean hasContent = false;
    if (showOnAir) {
      line = line.append(Component.translatable("evilradio.widget.onAir").color(NamedTextColor.RED));
      hasContent = true;
    }
    if (showTwitch) {
      if (hasContent) {
        line = line.append(Component.text(" | ").color(NamedTextColor.GRAY));
      }
      line = line.append(Component.translatable("evilradio.widget.twitch").color(TWITCH_COLOR));
      hasContent = true;
    }
    if (moderatorName != null && !moderatorName.isEmpty()) {
      if (hasContent) {
        line = line.append(Component.text(" | ").color(NamedTextColor.GRAY));
      }
      line = line.append(Component.text(moderatorName).color(NamedTextColor.WHITE));
      hasContent = true;
    }
    return hasContent ? line : null;
  }

  static String plainPrefix(RadioStream stream, CurrentSong song, boolean preferTwitch) {
    Component component = buildPrefix(stream, song, preferTwitch);
    if (component == null) {
      return "";
    }
    boolean onAir = song.isOnAir();
    boolean twitch = song.isTwitch();
    boolean showTwitch = onAir && twitch ? preferTwitch : twitch;
    boolean showOnAir = onAir && twitch ? !preferTwitch : onAir;

    StringBuilder builder = new StringBuilder();
    boolean hasContent = false;
    if (showOnAir) {
      builder.append(I18n.translate("evilradio.widget.onAir"));
      hasContent = true;
    }
    if (showTwitch) {
      if (hasContent) {
        builder.append(" | ");
      }
      builder.append(I18n.translate("evilradio.widget.twitch"));
      hasContent = true;
    }
    if (onAir && song.getModeratorName() != null && !song.getModeratorName().isEmpty()) {
      if (hasContent) {
        builder.append(" | ");
      }
      builder.append(song.getModeratorName());
    }
    return builder.toString();
  }

}
