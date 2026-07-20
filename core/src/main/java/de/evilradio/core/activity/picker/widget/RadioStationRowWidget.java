package de.evilradio.core.activity.picker.widget;

import de.evilradio.core.EvilTextures;
import de.evilradio.core.activity.picker.StationPickerController;
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
  private boolean focused;
  private boolean onAir;
  private boolean twitch;
  private @Nullable CurrentSong song;
  private @Nullable String appliedCoverKey;

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

    this.nameWidget = ComponentWidget.component(this.buildName());
    this.nameWidget.addId("row-name");
    this.addChild(this.nameWidget);

    if (StationPickerController.isPlayable(this.stream)) {
      this.coverWidget = new IconWidget(EvilTextures.LOGO).addId("row-cover");
      this.addChild(this.coverWidget);
      this.songWidget = ComponentWidget.empty().addId("row-song");
      this.addChild(this.songWidget);
      this.artistWidget = ComponentWidget.empty().addId("row-artist");
      this.addChild(this.artistWidget);
      this.applySong();
    } else {
      this.coverWidget = null;
      this.songWidget = null;
      this.artistWidget = null;
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
      this.nameWidget.setComponent(this.buildName());
    }
  }

  public void setSong(@Nullable CurrentSong song) {
    this.song = song;
    this.applySong();
  }

  private void applySong() {
    if (this.songWidget == null || this.artistWidget == null) {
      return;
    }

    if (this.song == null || !this.song.isValid()) {
      this.songWidget.setComponent(
          Component.translatable("evilradio.picker.loadingSong").color(NamedTextColor.DARK_GRAY));
      this.artistWidget.setComponent(Component.empty());
      this.applyCover(null);
      return;
    }

    String title = this.song.getTitle();
    String artist = this.song.getArtist();
    this.songWidget.setComponent(
        Component.text(title == null || title.isBlank() ? "—" : title).color(NamedTextColor.GRAY));
    if (artist == null || artist.isBlank()) {
      this.artistWidget.setComponent(Component.empty());
    } else {
      this.artistWidget.setComponent(Component.text(artist).color(NamedTextColor.DARK_GRAY));
    }
    this.applyCover(this.song.getImageUrl());
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

  private Component buildName() {
    TextColor color = this.playing ? NamedTextColor.GREEN : NamedTextColor.WHITE;
    Component name = Component.text(this.stream.getDisplayName()).color(color);

    if (StationPickerController.isMashup(this.stream)) {
      if (this.onAir) {
        name = name
            .append(Component.translatable("evilradio.widget.statusSeparator").color(NamedTextColor.GRAY))
            .append(Component.translatable("evilradio.widget.onAir").color(NamedTextColor.RED));
      }
      if (this.twitch) {
        name = name
            .append(Component.translatable("evilradio.widget.statusSeparator").color(NamedTextColor.GRAY))
            .append(Component.translatable("evilradio.widget.twitch").color(TextColor.color(145, 70, 255)));
      }
    }
    return name;
  }
}
