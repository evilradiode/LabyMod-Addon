package de.evilradio.core.activity.picker;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.radio.RadioManager;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.song.CurrentSong;
import de.evilradio.core.song.CurrentSongService.ShowStatus;
import java.util.List;
import java.util.function.Consumer;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.gui.icon.Icon;
import org.jetbrains.annotations.Nullable;

/**
 * Shared play / volume / controls logic for the station list picker.
 */
public final class StationPickerController {

  private static final long MIDDLE_CLICK_DEBOUNCE_MS = 200L;

  private final EvilRadioAddon addon;
  private final RadioManager radioManager;
  private long lastMiddleClickTime;

  public StationPickerController(EvilRadioAddon addon) {
    this.addon = addon;
    this.radioManager = addon.radioManager();
  }

  public EvilRadioAddon addon() {
    return this.addon;
  }

  public RadioManager radioManager() {
    return this.radioManager;
  }

  public Component controlsLine() {
    int volumeInt = Math.round(this.addon.configuration().volume().get());
    Component playStopStatus = this.radioManager.isPlaying()
        ? Component.translatable("evilradio.picker.playing").color(NamedTextColor.GREEN)
        : Component.translatable("evilradio.picker.stopped").color(NamedTextColor.GRAY);

    return Component.translatable("evilradio.picker.volumeLabel", Component.text(String.valueOf(volumeInt)))
        .color(NamedTextColor.YELLOW)
        .append(Component.text(" | ").color(NamedTextColor.GRAY))
        .append(playStopStatus);
  }

  public Component scrollInfoLine() {
    return Component.translatable("evilradio.picker.scrollInfo").color(NamedTextColor.GRAY);
  }

  public void adjustVolumeByScroll(double scrollDelta) {
    float currentVolume = this.addon.configuration().volume().get();
    int direction = scrollDelta > 0 ? 1 : -1;
    float newVolume = Math.round(Math.clamp(currentVolume + direction, 0.0f, 100.0f));
    this.addon.configuration().volume().set(newVolume);
  }

  /**
   * @return {@code true} if the middle-click was handled (including debounce cancel)
   */
  public boolean handleMiddleClick() {
    long currentTime = System.currentTimeMillis();
    if (currentTime - this.lastMiddleClickTime < MIDDLE_CLICK_DEBOUNCE_MS) {
      return true;
    }
    this.lastMiddleClickTime = currentTime;
    this.radioManager.togglePlayStop();
    return true;
  }

  public void playStream(@Nullable RadioStream stream, @Nullable Runnable closeMenu) {
    if (stream != null && stream.getUrl() != null && !stream.getUrl().isEmpty()) {
      boolean alreadyPlayingSame = this.radioManager.isPlaying()
          && this.radioManager.getCurrentStream() != null
          && this.radioManager.getCurrentStream().getId() == stream.getId();
      this.radioManager.playStream(stream);
      if (alreadyPlayingSame) {
        this.notifyStreamSelected(stream, this.addon.currentSongService().getCurrentSong());
      } else {
        this.addon.currentSongService().armStreamSelectedNotification();
      }
    }

    if (closeMenu != null) {
      closeMenu.run();
    }
  }

  public void fetchMashupStatus(Consumer<ShowStatus> onResult) {
    // API erwartet exakt "Mashup" (siehe php-api/index.php) – nicht "mashup".
    RadioStream mashup = this.findMashupStream();
    String radioInfoName = mashup != null && mashup.getName() != null && !mashup.getName().isBlank()
        ? mashup.getName()
        : "Mashup";
    this.addon.currentSongService().fetchLiveFlags(radioInfoName, show -> {
      if (show == null) {
        return;
      }
      this.addon.labyAPI().minecraft().executeOnRenderThread(() -> onResult.accept(show));
    });
  }

  public @Nullable RadioStream findMashupStream() {
    List<RadioStream> streams = this.addon.radioStreamService().streams();
    for (RadioStream stream : streams) {
      if (stream != null && stream.getName() != null && stream.getName().equalsIgnoreCase("mashup")) {
        return stream;
      }
    }
    return null;
  }

  public static boolean isPlayable(@Nullable RadioStream stream) {
    return stream != null && stream.getUrl() != null && !stream.getUrl().isEmpty();
  }

  public static boolean isMashup(@Nullable RadioStream stream) {
    return stream != null && stream.getName() != null && stream.getName().equalsIgnoreCase("mashup");
  }

  private void notifyStreamSelected(RadioStream stream, @Nullable CurrentSong currentSong) {
    Component notificationTitle;
    Component notificationText;
    Icon icon = null;

    if (currentSong != null && !currentSong.isAdBreak()) {
      String songText = currentSong.getFormatted();
      if (!songText.isEmpty()) {
        notificationTitle = Component.translatable(
            "evilradio.notification.streamSelected.titleWithStation",
            Component.text(stream.getDisplayName())
        );
        notificationText = Component.translatable(
            "evilradio.notification.streamSelected.textWithSong",
            Component.text(songText)
        );
        icon = Icon.url(currentSong.getImageUrl());
      } else {
        notificationTitle = Component.translatable(
            "evilradio.notification.streamSelected.titleWithStation",
            Component.text(stream.getDisplayName())
        );
        notificationText = Component.translatable("evilradio.notification.streamSelected.text");
      }
    } else {
      notificationTitle = Component.translatable(
          "evilradio.notification.streamSelected.titleWithStation",
          Component.text(stream.getDisplayName())
      );
      notificationText = Component.translatable("evilradio.notification.streamSelected.text");
    }

    this.addon.notification(notificationTitle, notificationText, icon, stream.getIcon());
  }
}
