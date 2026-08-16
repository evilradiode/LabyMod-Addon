package de.evilradio.core.activity.picker.widget;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.EvilTextures;
import de.evilradio.core.activity.picker.StationPickerController;
import de.evilradio.core.configuration.StationPickerSubSettings;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.song.CurrentSong;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.TextColor;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.client.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@AutoWidget
@Link("widget/radio-station-row.lss")
public class RadioStationRowWidget extends DivWidget {

  private final RadioStream stream;
  private final boolean playing;
  private IconWidget stationIconWidget;
  private IconWidget coverWidget;
  private ComponentWidget nameWidget;
  private ComponentWidget songWidget;
  private ComponentWidget artistWidget;
  private ComponentWidget timeWidget;
  private DivWidget progressTrack;
  private DivWidget progressFill;
  private boolean focused;
  private boolean onAir;
  private boolean twitch;
  private @Nullable CurrentSong song;
  private @Nullable String appliedCoverKey;
  private long lastRenderedElapsed = -1L;
  private boolean lastRenderedHadDuration;
  private static final String PROGRESS_WIDTH_KEY = "--row-progress-width";
  private static final float PROGRESS_TRACK_WIDTH = 120f;

  public RadioStationRowWidget(RadioStream stream, boolean playing) {
    this.stream = stream;
    this.playing = playing;
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);
    this.children.clear();

    Icon stationIcon = this.stream.getIcon();
    if (stationIcon == null) {
      stationIcon = Icon.texture(
          ResourceLocation.create("evilradio", "textures/stations/comingsoon.png"));
    }
    this.stationIconWidget = new IconWidget(stationIcon).addId("row-station-icon");
    this.addChild(this.stationIconWidget);

    if (StationPickerController.isPlayable(this.stream)) {
      this.coverWidget = new IconWidget(EvilTextures.LOGO).addId("row-cover");
      this.addChild(this.coverWidget);
      this.nameWidget = ComponentWidget.component(this.buildStationLine()).addId("row-name");
      this.addChild(this.nameWidget);
      this.songWidget = ComponentWidget.empty().addId("row-song");
      this.addChild(this.songWidget);
      this.artistWidget = ComponentWidget.empty().addId("row-artist");
      this.addChild(this.artistWidget);
      this.progressTrack = new DivWidget().addId("row-progress-track");
      this.progressFill = new DivWidget().addId("row-progress-fill");
      this.progressFill.setVariable(PROGRESS_WIDTH_KEY, 0f);
      this.progressTrack.addChild(this.progressFill);
      this.progressTrack.setVisible(false);
      this.addChild(this.progressTrack);
      this.timeWidget = ComponentWidget.empty().addId("row-time");
      this.timeWidget.setVisible(false);
      this.addChild(this.timeWidget);
      this.applySong();
    } else {
      this.coverWidget = null;
      this.nameWidget = ComponentWidget.component(this.buildStationLine());
      this.nameWidget.addId("row-name");
      this.addChild(this.nameWidget);
      this.songWidget = null;
      this.artistWidget = null;
      this.timeWidget = null;
      this.progressTrack = null;
      this.progressFill = null;
      this.addId("coming-soon");
    }
    if (this.playing) {
      this.addId("playing");
    }
    this.applyFocusStyle();
  }

  public RadioStream getStream() {
    return this.stream;
  }

  public void setFocusedRow(boolean focused) {
    if (this.focused == focused) {
      return;
    }
    this.focused = focused;
    this.applyFocusStyle();
  }

  public void updateOnAirAndTwitchStatus(boolean onAir, boolean twitch) {
    this.onAir = onAir;
    this.twitch = twitch;
    if (this.nameWidget != null) {
      this.nameWidget.setComponent(this.buildStationLine());
    }
  }

  public void setSong(@Nullable CurrentSong song) {
    this.song = song;
    this.lastRenderedElapsed = -1L;
    this.lastRenderedHadDuration = false;
    this.applySong();
  }

  /**
   * Aktualisiert nur die Playtime-Anzeige, wenn sich die Sekunde geändert hat.
   */
  public void tickPlaytime() {
    this.updatePlaytime(false);
  }

  private void applySong() {
    if (this.songWidget == null || this.artistWidget == null) {
      return;
    }

    StationPickerSubSettings picker = EvilRadioAddon.instance().configuration().stationPicker();
    TextColor songColor = CurrentSongHudWidget.toTextColor(picker.songColor().get());
    TextColor artistColor = CurrentSongHudWidget.toTextColor(picker.artistColor().get());

    if (this.song == null || !this.song.isValid()) {
      this.songWidget.setComponent(
          Component.translatable("evilradio.picker.loadingSong").color(artistColor));
      this.artistWidget.setComponent(Component.empty());
      this.applyCover(null);
      this.updatePlaytime(true);
      return;
    }

    String title = this.song.getDisplayTitle();
    String artist = this.song.getArtist();
    this.songWidget.setComponent(
        Component.text(title == null || title.isBlank() ? "—" : title).color(songColor));
    if (artist == null || artist.isBlank()) {
      this.artistWidget.setComponent(Component.empty());
    } else {
      this.artistWidget.setComponent(Component.text(artist).color(artistColor));
    }
    this.applyCover(this.song.getImageUrl());
    this.updatePlaytime(true);
  }

  private void updatePlaytime(boolean force) {
    if (this.timeWidget == null) {
      return;
    }
    if (this.song == null || !this.song.isValid() || !this.song.hasKnownDuration()) {
      if (force || this.lastRenderedHadDuration || this.lastRenderedElapsed >= 0L) {
        this.timeWidget.setComponent(Component.empty());
        this.timeWidget.setVisible(false);
        this.setProgressVisible(false);
        this.lastRenderedElapsed = -1L;
        this.lastRenderedHadDuration = false;
      }
      return;
    }

    long elapsed = this.song.getCurrentElapsedSeconds();
    if (!force && this.lastRenderedHadDuration && elapsed == this.lastRenderedElapsed) {
      return;
    }
    this.lastRenderedElapsed = elapsed;
    this.lastRenderedHadDuration = true;
    String label = this.song.getPlaytimeLabel();
    if (label == null || label.isBlank()) {
      this.timeWidget.setVisible(false);
      this.setProgressVisible(false);
      return;
    }
    TextColor timeColor = CurrentSongHudWidget.toTextColor(
        EvilRadioAddon.instance().configuration().stationPicker().timeColor().get());
    this.timeWidget.setComponent(Component.text(label).color(timeColor));
    this.timeWidget.setVisible(true);
    this.updateProgressBar();
  }

  private void updateProgressBar() {
    if (this.progressTrack == null || this.progressFill == null || this.song == null) {
      return;
    }
    double progress = this.song.getProgress();
    if (progress < 0.0d) {
      this.setProgressVisible(false);
      return;
    }
    this.progressFill.setVariable(PROGRESS_WIDTH_KEY, (float) (PROGRESS_TRACK_WIDTH * progress));
    this.setProgressVisible(true);
  }

  private void setProgressVisible(boolean visible) {
    if (this.progressTrack == null) {
      return;
    }
    this.progressTrack.setVisible(visible);
    if (!visible && this.progressFill != null) {
      this.progressFill.setVariable(PROGRESS_WIDTH_KEY, 0f);
    }
  }

  private void applyCover(@Nullable String imageUrl) {
    if (this.coverWidget == null) {
      return;
    }
    String coverKey;
    Icon icon;
    if (imageUrl != null && !imageUrl.isBlank()) {
      coverKey = imageUrl;
      icon = Icon.url(imageUrl);
    } else {
      coverKey = "logo";
      icon = EvilTextures.LOGO;
    }
    if (coverKey.equals(this.appliedCoverKey)) {
      return;
    }
    this.appliedCoverKey = coverKey;
    this.coverWidget.icon().set(icon);
  }

  private void applyFocusStyle() {
    if (this.focused) {
      this.addId("focused");
    } else {
      this.removeId("focused");
    }
  }

  private Component buildStationLine() {
    TextColor color = this.playing ? NamedTextColor.GREEN : NamedTextColor.WHITE;
    Component name = Component.text(this.stream.getDisplayName()).color(color);
    if (!StationPickerController.isMashup(this.stream)) {
      return name;
    }
    if (this.onAir) {
      name = name
          .append(Component.text(" | ").color(NamedTextColor.GRAY))
          .append(Component.translatable("evilradio.widget.onAir").color(NamedTextColor.RED));
    }
    if (this.twitch) {
      name = name
          .append(Component.text(" | ").color(NamedTextColor.GRAY))
          .append(Component.translatable("evilradio.widget.twitch").color(TextColor.color(145, 70, 255)));
    }
    return name;
  }
}
