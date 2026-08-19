package de.evilradio.core.listener;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.EvilTextures;
import de.evilradio.core.EvilTextures.SpriteControls;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import de.evilradio.core.hudwidget.widget.LiveStatusLine;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.song.CurrentSong;
import de.evilradio.core.song.NowPlayingConnectionState;
import java.util.List;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.gui.hud.hudwidget.HudWidget.Updatable;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.activity.Activity;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.activity.Document;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.HorizontalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.gui.screen.ActivityInitializeEvent;
import net.labymod.api.event.client.lifecycle.GameTickEvent;

public class ActivityListener implements Updatable {

  private EvilRadioAddon addon;

  public ActivityListener(EvilRadioAddon addon) {
    this.addon = addon;
  }

  @Subscribe
  public void onActivityInitialize(ActivityInitializeEvent event) {
    if(!event.getIdentifier().equals("labymod:main_menu")) return;
    this.addRadioController(event.activity());
  }

  @Subscribe
  public void onGameTick(GameTickEvent event) {

    CurrentSong song = this.addon.currentSongService().getCurrentSong();
    if (song != null) {
      boolean twitchPhase = LiveStatusLine.showTwitchPhase(System.currentTimeMillis());
      if (twitchPhase != this.lastLiveBadgeTwitchPhase
          && LiveStatusLine.hasLiveBadges(song)
          && song.isOnAir()
          && song.isTwitch()) {
        this.lastLiveBadgeTwitchPhase = twitchPhase;
        this.refreshStreamLine(song);
      }
    }

  }

  @Override
  public void update(String reason) {
    if (reason == null || reason.equals(CurrentSongHudWidget.SONG_CHANGE_REASON)) {
      this.refreshPlayPauseIcon();
      this.updateTrack(
          this.addon.currentSongService().getCurrentSong(),
          this.addon.currentSongService().getPreviousSong());
    }
  }

  private IconWidget coverWidget;
  private ComponentWidget streamWidget;
  private ComponentWidget trackWidget;
  private ComponentWidget artistWidget;

  private ButtonWidget playPauseButton;

  private static final float MAX_PLAYER_WIDTH = 160f;

  private static final String MAX_PLAYER_WIDTH_KEY = "--modern-song-widget-max-player-width";

  private Component lastLivePrefix = null;
  private boolean lastLiveBadgeTwitchPhase;

  private void addRadioController(Activity activity) {
    Document document = activity.document();
    activity.addStyle("evilradio", "activity/menu.lss");

    activity.setVariable(MAX_PLAYER_WIDTH_KEY, MAX_PLAYER_WIDTH);

    this.lastLivePrefix = null;

    FlexibleContentWidget songContainer = new FlexibleContentWidget().addId("song-container");

    FlexibleContentWidget content = new FlexibleContentWidget().addId("content");

    this.coverWidget = new IconWidget(this.stationIcon());
    this.coverWidget.addId("cover");
    content.addContent(this.coverWidget);

    FlexibleContentWidget player = new FlexibleContentWidget().addId("player");

    this.streamWidget = ComponentWidget.empty();
    this.streamWidget.addId("stream-name");
    player.addContent(this.streamWidget);

    this.trackWidget = ComponentWidget.empty();
    this.trackWidget.addId("track");
    player.addContent(this.trackWidget);

    this.artistWidget = ComponentWidget.empty();
    this.artistWidget.addId("artist");
    player.addContent(this.artistWidget);

    HorizontalListWidget controlsContainer = new HorizontalListWidget().addId("controls");

    ButtonWidget previousButton = ButtonWidget.icon(SpriteControls.PREVIOUS).addId("previous");
    previousButton.setPressable(() -> {
      this.switchStream(-1);
    });
    controlsContainer.addEntry(previousButton);

    this.playPauseButton = ButtonWidget.icon(this.addon.radioManager().isPlaying() ? SpriteControls.PAUSE : SpriteControls.PLAY).addId("play-pause");
    this.playPauseButton.setPressable(() -> {
      if (this.addon.radioManager().getCurrentStream() == null && !this.addon.radioManager().isPlaying()) {
        RadioStream lastStream = this.addon.radioStreamService().findStreamById(this.addon.configuration().lastStreamId().get());
        if (lastStream != null) {
          this.addon.radioManager().playStream(lastStream);
        } else {
          this.addon.radioManager().togglePlayStop();
        }
      } else {
        this.addon.radioManager().togglePlayStop();
      }
      this.playPauseButton.updateIcon(this.addon.radioManager().isPlaying() ? SpriteControls.PAUSE : SpriteControls.PLAY);
      this.addon.radioStationListActivity().startNowPlayingSession();
    });
    controlsContainer.addEntry(this.playPauseButton);

    ButtonWidget nextButton = ButtonWidget.icon(SpriteControls.NEXT).addId("next");
    nextButton.setPressable(() -> {
      this.switchStream(1);
    });
    controlsContainer.addEntry(nextButton);

    SliderWidget volumeSlider = new SliderWidget(1.0F, newVolume -> {
      float rounded = Math.round(newVolume);
      this.addon.configuration().volume().set(rounded);
    })
        .range(0.0F, 100.0F)
        .withFormatter(value -> Component.text(Math.round(value) + "%"))
        .addId("volume-slider");
    volumeSlider.setValue(this.addon.configuration().volume().get());

    controlsContainer.addEntry(volumeSlider);

    player.addContent(controlsContainer);

    content.addFlexibleContent(player);

    songContainer.addContent(content);

    document.addChildInitialized(songContainer);

    this.refreshPlayPauseIcon();
    this.updateTrack(
        this.addon.currentSongService().getCurrentSong(),
        this.addon.currentSongService().getPreviousSong());
  }

  private void switchStream(int direction) {
    List<RadioStream> streams = this.addon.radioStreamService().streams();
    if (streams.isEmpty()) {
      return;
    }

    RadioStream current = this.addon.radioManager().getCurrentStream();
    int currentIndex = -1;
    for (int i = 0; i < streams.size(); i++) {
      if (streams.get(i).equals(current)) {
        currentIndex = i;
        break;
      }
    }

    int index = currentIndex;
    if (index < 0) {
      index = direction > 0 ? -1 : streams.size();
    }

    RadioStream nextPlayable = null;
    for (int i = 0; i < streams.size(); i++) {
      index = Math.floorMod(index + direction, streams.size());
      RadioStream candidate = streams.get(index);
      if (candidate != null && candidate.getUrl() != null && !candidate.getUrl().isBlank()) {
        nextPlayable = candidate;
        break;
      }
    }

    if (nextPlayable == null) {
      return;
    }

    this.addon.radioManager().playStream(nextPlayable);
    this.refreshPlayPauseIcon();
    this.updateTrack(
        this.addon.currentSongService().getCurrentSong(),
        this.addon.currentSongService().getPreviousSong());
    if (this.addon.radioStationListActivity() != null) {
      this.addon.radioStationListActivity().startNowPlayingSession();
    }
  }

  private void refreshStreamLine(CurrentSong currentSong) {
    if (this.streamWidget == null || currentSong == null) {
      return;
    }
    RadioStream currentStream = this.addon.radioManager().getCurrentStream();
    String streamDisplayName = stationLabel(currentStream, LiveStatusLine.hasLiveBadges(currentSong));
    if (streamDisplayName.isBlank() && currentSong.getStationName() != null) {
      streamDisplayName = currentSong.getStationName();
    }
    this.lastLivePrefix = LiveStatusLine.buildPrefix(
        currentStream, currentSong, this.lastLiveBadgeTwitchPhase);
    Component streamLine = Component.text(streamDisplayName).color(NamedTextColor.GRAY);
    if (this.lastLivePrefix != null) {
      streamLine = streamLine
          .append(Component.text(" | ").color(NamedTextColor.GRAY))
          .append(this.lastLivePrefix);
    }
    this.streamWidget.setComponent(streamLine);
  }

  private void updateTrack(CurrentSong currentSong, CurrentSong previousSong) {
    if (this.trackWidget == null || this.artistWidget == null || this.streamWidget == null) {
      return;
    }

    boolean isPlaying = this.addon.radioManager().isPlaying();
    RadioStream currentStream = this.addon.radioManager().getCurrentStream();
    NowPlayingConnectionState state = this.addon.currentSongService().getConnectionState();

    this.streamWidget.setVisible(true);
    this.trackWidget.setVisible(true);
    this.artistWidget.setVisible(true);

    if (currentSong == null) {
      this.lastLivePrefix = null;
      this.applyStationIcon();
      if (isPlaying && currentStream != null) {
        this.streamWidget.setComponent(Component.text(stationLabel(currentStream, false)).color(NamedTextColor.WHITE));
        if (state == NowPlayingConnectionState.RECONNECTING) {
          this.trackWidget.setComponent(Component.translatable("evilradio.widget.reconnecting")
              .color(NamedTextColor.DARK_GRAY));
          this.artistWidget.setComponent(Component.translatable("evilradio.widget.reconnectingHint")
              .color(NamedTextColor.DARK_GRAY));
        } else {
          this.trackWidget.setComponent(Component.translatable("evilradio.widget.loadingSong")
              .color(NamedTextColor.DARK_GRAY));
          this.artistWidget.setComponent(Component.empty());
        }
      } else {
        this.streamWidget.setComponent(Component.empty());
        this.trackWidget.setComponent(Component.empty());
        this.artistWidget.setComponent(Component.empty());
      }
      return;
    }

    String streamDisplayName = stationLabel(currentStream, LiveStatusLine.hasLiveBadges(currentSong));
    if (streamDisplayName.isBlank() && currentSong.getStationName() != null) {
      streamDisplayName = currentSong.getStationName();
    }
    this.lastLiveBadgeTwitchPhase = LiveStatusLine.showTwitchPhase(System.currentTimeMillis());
    this.lastLivePrefix = LiveStatusLine.buildPrefix(
        currentStream, currentSong, this.lastLiveBadgeTwitchPhase);
    Component streamLine = Component.text(streamDisplayName).color(NamedTextColor.GRAY);
    if (this.lastLivePrefix != null) {
      streamLine = streamLine
          .append(Component.text(" | ").color(NamedTextColor.GRAY))
          .append(this.lastLivePrefix);
    }
    this.streamWidget.setComponent(streamLine);

    this.trackWidget.setComponent(Component.text(currentSong.getDisplayTitle(), NamedTextColor.WHITE));
    this.artistWidget.setComponent(Component.text(currentSong.getArtist() == null ? "" : currentSong.getArtist(), NamedTextColor.WHITE));

    this.applyStationIcon();
  }

  private void applyStationIcon() {
    if (this.coverWidget == null) {
      return;
    }
    Icon icon = this.stationIcon();
    if (this.coverWidget.icon().get() == icon) {
      return;
    }
    this.coverWidget.icon().set(icon);
  }

  private void refreshPlayPauseIcon() {
    if (this.playPauseButton == null) {
      return;
    }
    this.playPauseButton.updateIcon(
        this.addon.radioManager().isPlaying() ? SpriteControls.PAUSE : SpriteControls.PLAY);
  }

  private Icon stationIcon() {
    RadioStream stream = this.addon.radioManager().getCurrentStream();
    if (stream == null) {
      stream = this.addon.radioStreamService().findStreamById(
          this.addon.configuration().lastStreamId().get());
    }
    if (stream != null && stream.getIcon() != null) {
      return stream.getIcon();
    }
    return EvilTextures.LOGO;
  }

  private static String stationLabel(RadioStream stream, boolean compact) {
    if (stream == null) {
      return "";
    }
    String name = stream.getDisplayName() != null && !stream.getDisplayName().isBlank()
        ? stream.getDisplayName()
        : stream.getName();
    if (name == null || name.isBlank()) {
      return "";
    }
    return compact ? name : "EvilRadio - " + name;
  }

}
