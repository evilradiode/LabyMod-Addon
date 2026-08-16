package de.evilradio.core.hudwidget.widget;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.EvilTextures;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.song.CurrentSong;
import de.evilradio.core.song.NowPlayingConnectionState;
import de.evilradio.core.song.artwork.ArtworkCache;
import net.labymod.api.Laby;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.TextColor;
import net.labymod.api.client.gfx.pipeline.renderer.text.FontRenderer;
import net.labymod.api.client.gui.hud.hudwidget.HudWidget.Updatable;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;

@Link("widget/song-widget.lss")
@AutoWidget
public class CurrentSongWidget extends FlexibleContentWidget implements Updatable {

  private final EvilRadioAddon addon;
  private final CurrentSongHudWidget hudWidget;

  private static final String MAX_PLAYER_WIDTH_KEY = "--current-song-widget-max-player-width";
  private static final String PROGRESS_FILL_WIDTH_KEY = "--current-song-progress-width";
  private static final String BACKGROUND_VARIABLE_KEY = "--song-widget-bg";
  private static final String PROGRESS_BAR_COLOR_VARIABLE_KEY = "--song-widget-progress-bar-color";

  private static final float MAX_PLAYER_WIDTH = 160f;

  private ComponentWidget streamWidget;
  private ComponentWidget statusWidget;
  private ComponentWidget trackWidget;
  private ComponentWidget artistWidget;

  private IconWidget coverWidget;
  private DivWidget progressTrack;
  private DivWidget progressFill;

  private String appliedCoverUrl;
  private long appliedArtworkGeneration = -1L;
  private long lastRenderedElapsed = -1L;
  private int lastRenderedProgressPercent = -1;
  private boolean lastRenderedHadDuration;
  private float progressTrackMaxWidth = 200f;
  private String lastTrackName = "";
  private String lastArtistName = "";
  private Component lastLivePrefix = Component.empty();
  private boolean lastLiveBadgeTwitchPhase;

  public CurrentSongWidget(EvilRadioAddon addon, CurrentSongHudWidget hudWidget) {
    this.addon = addon;
    this.hudWidget = hudWidget;
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);
    this.children.clear();
    this.appliedCoverUrl = null;
    this.appliedArtworkGeneration = -1L;
    this.lastRenderedElapsed = -1L;
    this.lastRenderedProgressPercent = -1;
    this.lastRenderedHadDuration = false;
    this.lastTrackName = "";
    this.lastArtistName = "";
    this.lastLivePrefix = Component.empty();
    this.lastLiveBadgeTwitchPhase = LiveStatusLine.showTwitchPhase(System.currentTimeMillis());

    this.setVariable(MAX_PLAYER_WIDTH_KEY, MAX_PLAYER_WIDTH);
    this.setVariable(PROGRESS_FILL_WIDTH_KEY, 0);
    this.applyBackgroundColor();

    boolean showCover = this.hudWidget.getConfig().showCover().get();
    if (!showCover) {
      this.addId("no-cover");
    }

    boolean leftAligned = this.hudWidget.anchor().isLeft();
    this.addId(leftAligned ? "left" : "right");

    this.coverWidget = new IconWidget(EvilTextures.LOGO);
    this.coverWidget.addId("cover");
    this.coverWidget.setVisible(showCover);

    if (leftAligned) {
      this.addContent(this.coverWidget);
    }

    FlexibleContentWidget player = new FlexibleContentWidget().addId("player");

    VerticalListWidget<ComponentWidget> text = new VerticalListWidget<>().addId("text");

    this.streamWidget = ComponentWidget.empty();
    text.addChild(this.streamWidget);

    this.trackWidget = ComponentWidget.empty().addId("track");
    text.addChild(this.trackWidget);

    this.artistWidget = ComponentWidget.empty().addId("artist");
    text.addChild(this.artistWidget);

    this.statusWidget = ComponentWidget.empty();
    text.addChild(this.statusWidget);

    player.addFlexibleContent(text);

    this.progressTrack = new DivWidget().addId("progress-track");
    this.progressFill = new DivWidget().addId("progress-fill");
    this.progressTrack.addChild(this.progressFill);
    player.addContent(this.progressTrack);

    this.addContent(player);

    if (!leftAligned) {
      this.addContent(this.coverWidget);
    }

    this.updateTrack(this.addon.currentSongService().getCurrentSong());
  }

  @Override
  public void tick() {
    super.tick();

    CurrentSong song = this.addon.currentSongService().getCurrentSong();
    if (song != null) {
      this.updateProgress(song);
      boolean twitchPhase = LiveStatusLine.showTwitchPhase(System.currentTimeMillis());
      if (twitchPhase != this.lastLiveBadgeTwitchPhase
          && LiveStatusLine.hasLiveBadges(song)
          && song.isOnAir()
          && song.isTwitch()) {
        this.lastLiveBadgeTwitchPhase = twitchPhase;
        this.lastLivePrefix = LiveStatusLine.buildPrefix(
            this.addon.radioManager().getCurrentStream(), song, twitchPhase);
        if (this.lastLivePrefix == null) {
          this.lastLivePrefix = Component.empty();
        }
        this.renderStatusLine(song);
      }
    }
  }

  @Override
  public void update(String reason) {
    if (reason == null) {
      this.reInitialize();
      return;
    }

    if (reason.equals(CurrentSongHudWidget.SONG_CHANGE_REASON)) {
      this.updateTrack(this.addon.currentSongService().getCurrentSong());
    }

    if (reason.equals(CurrentSongHudWidget.COVER_VISIBILITY_REASON)) {
      boolean showCover = this.hudWidget.getConfig().showCover().get();
      if (showCover) {
        this.removeId("no-cover");
        if (this.coverWidget != null) {
          this.coverWidget.setVisible(true);
        }
      } else {
        this.addId("no-cover");
        if (this.coverWidget != null) {
          this.coverWidget.setVisible(false);
        }
      }
    }

    if (reason.equals(CurrentSongHudWidget.COLOR_REASON)) {
      this.applyBackgroundColor();
      this.updateTrack(this.addon.currentSongService().getCurrentSong());
    }
  }

  private void updateTrack(CurrentSong currentSong) {
    if (this.trackWidget == null || this.artistWidget == null || this.streamWidget == null
        || this.statusWidget == null) {
      return;
    }

    boolean isPlaying = this.addon.radioManager().isPlaying();
    RadioStream currentStream = this.addon.radioManager().getCurrentStream();
    NowPlayingConnectionState state = this.addon.currentSongService().getConnectionState();

    // Alle 4 Zeilen immer sichtbar halten
    this.streamWidget.setVisible(true);
    this.statusWidget.setVisible(true);
    this.trackWidget.setVisible(true);
    this.artistWidget.setVisible(true);

    if (currentSong == null) {
      this.lastTrackName = "";
      this.lastArtistName = "";
      this.lastLivePrefix = Component.empty();
      if (isPlaying && currentStream != null) {
        this.streamWidget.setComponent(Component.text(stationLabel(currentStream)).color(this.stationTextColor()));
        if (state == NowPlayingConnectionState.RECONNECTING) {
          this.statusWidget.setComponent(Component.translatable("evilradio.widget.reconnecting")
              .color(NamedTextColor.DARK_GRAY));
          this.trackWidget.setComponent(Component.translatable("evilradio.widget.loadingSong"));
          this.artistWidget.setComponent(Component.translatable("evilradio.widget.reconnectingHint"));
        } else {
          this.statusWidget.setComponent(Component.translatable("evilradio.widget.loadingSong")
              .color(NamedTextColor.DARK_GRAY));
          this.trackWidget.setComponent(Component.translatable("evilradio.widget.fetchingSongInfo"));
          this.artistWidget.setComponent(Component.empty());
        }
        this.setProgressVisible(false);
      } else {
        this.streamWidget.setComponent(Component.empty());
        this.statusWidget.setComponent(Component.empty());
        this.trackWidget.setComponent(Component.empty());
        this.artistWidget.setComponent(Component.empty());
        this.setProgressVisible(false);
      }
      return;
    }

    String streamDisplayName = stationLabel(currentStream);
    if (streamDisplayName.isBlank() && currentSong.getStationName() != null) {
      streamDisplayName = "EvilRadio - " + currentSong.getStationName();
    }
    this.streamWidget.setComponent(Component.text(streamDisplayName).color(this.stationTextColor()));

    this.lastLiveBadgeTwitchPhase = LiveStatusLine.showTwitchPhase(System.currentTimeMillis());
    Component live = LiveStatusLine.buildPrefix(
        currentStream, currentSong, this.lastLiveBadgeTwitchPhase);
    this.lastLivePrefix = live == null ? Component.empty() : live;
    this.renderStatusLine(currentSong);

    this.lastTrackName = currentSong.getDisplayTitle();
    this.lastArtistName = currentSong.getArtist() == null ? "" : currentSong.getArtist();
    this.trackWidget.setComponent(Component.text(this.lastTrackName, this.songTextColor()));
    this.artistWidget.setComponent(Component.text(this.lastArtistName, this.artistTextColor()));

    FontRenderer fontRenderer = Laby.references().minecraftFontRenderer();
    String statusSample = statusLinePlain(currentSong);
    float streamNameWidth = fontRenderer.getWidth(streamDisplayName);
    float statusWidth = fontRenderer.getWidth(statusSample);
    float trackWidth = fontRenderer.getWidth(this.lastTrackName);
    float artistWidth = fontRenderer.getWidth(this.lastArtistName);
    float naturalWidth = Math.max(
        Math.max(streamNameWidth, trackWidth),
        Math.max(artistWidth, statusWidth)
    );
    float playerWidth = Math.clamp(naturalWidth + 4f, 160f, MAX_PLAYER_WIDTH);

    this.progressTrackMaxWidth = Math.max(40f, playerWidth);

    this.applyCover(currentSong);
    this.updateProgress(currentSong);
  }

  private void renderStatusLine(CurrentSong song) {
    String timeLabel = formatTimeLabel(song);
    String streamName = this.addon.radioManager().getCurrentStream() != null
        ? this.addon.radioManager().getCurrentStream().getName()
        : null;
    boolean isMashup = streamName != null && streamName.equalsIgnoreCase("Mashup");
    boolean hasLive = isMashup && (song.isOnAir() || song.isTwitch());

    Component status;
    if (hasLive) {
      status = this.lastLivePrefix;
      if (!timeLabel.isEmpty()) {
        status = status
            .append(Component.text(" | ").color(NamedTextColor.GRAY))
            .append(Component.text(timeLabel).color(NamedTextColor.DARK_GRAY));
      }
    } else if (!timeLabel.isEmpty()) {
      status = Component.text(timeLabel).color(NamedTextColor.DARK_GRAY);
    } else {
      status = Component.empty();
    }
    this.statusWidget.setComponent(status);
  }

  private String statusLinePlain(CurrentSong song) {
    String timeLabel = formatTimeLabel(song);
    RadioStream stream = this.addon.radioManager().getCurrentStream();
    String livePlain = LiveStatusLine.plainPrefix(stream, song, this.lastLiveBadgeTwitchPhase);
    if (!livePlain.isEmpty()) {
      return timeLabel.isEmpty() ? livePlain : livePlain + " | " + timeLabel;
    }
    return timeLabel;
  }

  private static String formatTimeLabel(CurrentSong song) {
    if (song == null) {
      return "";
    }
    String label = song.getPlaytimeLabel();
    if (label != null && !label.isBlank()) {
      return label;
    }
    return CurrentSong.formatTime(song.getCurrentElapsedSeconds());
  }

  private void applyCover(CurrentSong currentSong) {
    if (this.coverWidget == null || currentSong == null) {
      return;
    }

    ArtworkCache cache = this.addon.currentSongService().artworkCache();
    long generation = cache.currentGeneration();
    String url = currentSong.getImageUrl();
    String cacheKey = ArtworkCache.key(
        currentSong.getStationShortcode(),
        currentSong.getSongId(),
        url
    );
    cache.put(cacheKey, url);

    cache.applyIfCurrent(generation, url, artworkUrl -> {
      if (generation != cache.currentGeneration()) {
        return;
      }
      if (artworkUrl.equals(this.appliedCoverUrl) && generation == this.appliedArtworkGeneration) {
        return;
      }
      this.appliedCoverUrl = artworkUrl;
      this.appliedArtworkGeneration = generation;
      this.coverWidget.icon().set(Icon.url(artworkUrl));
    });

    if (url == null || url.isBlank()) {
      if (this.appliedCoverUrl != null) {
        return;
      }
      this.coverWidget.icon().set(EvilTextures.LOGO);
    }
  }

  private void updateProgress(CurrentSong song) {
    if (this.statusWidget == null || this.progressTrack == null || this.progressFill == null) {
      return;
    }

    long elapsed = song.getCurrentElapsedSeconds();
    boolean hasDuration = song.hasKnownDuration();
    int percent = 0;
    if (hasDuration) {
      percent = (int) Math.round(Math.clamp(song.getProgress(), 0.0d, 1.0d) * 100.0d);
    }

    if (elapsed == this.lastRenderedElapsed
        && percent == this.lastRenderedProgressPercent
        && hasDuration == this.lastRenderedHadDuration) {
      return;
    }
    this.lastRenderedElapsed = elapsed;
    this.lastRenderedProgressPercent = percent;
    this.lastRenderedHadDuration = hasDuration;

    this.renderStatusLine(song);

    float fillWidth;
    if (hasDuration) {
      fillWidth = this.progressTrackMaxWidth * (percent / 100.0f);
      this.progressTrack.removeId("indeterminate");
    } else {
      fillWidth = this.progressTrackMaxWidth * 0.3f;
      this.progressTrack.addId("indeterminate");
    }
    this.setVariable(PROGRESS_FILL_WIDTH_KEY, fillWidth);
    this.progressFill.setVariable(PROGRESS_FILL_WIDTH_KEY, fillWidth);
    this.setProgressVisible(true);
  }

  private void setProgressVisible(boolean visible) {
    if (!visible) {
      this.lastRenderedElapsed = -1L;
      this.lastRenderedProgressPercent = -1;
      this.lastRenderedHadDuration = false;
      this.setVariable(PROGRESS_FILL_WIDTH_KEY, 0);
      if (this.progressFill != null) {
        this.progressFill.setVariable(PROGRESS_FILL_WIDTH_KEY, 0);
      }
    }
    if (this.progressTrack != null) {
      this.progressTrack.setVisible(visible);
      if (!visible) {
        this.progressTrack.removeId("indeterminate");
      }
    }
  }

  private void applyBackgroundColor() {
    this.setVariable(BACKGROUND_VARIABLE_KEY, this.hudWidget.getConfig().backgroundColor().get().get());
    int progressColor = this.hudWidget.getConfig().progressBarColor().get().get();
    this.setVariable(PROGRESS_BAR_COLOR_VARIABLE_KEY, progressColor);
    if (this.progressFill != null) {
      this.progressFill.setVariable(PROGRESS_BAR_COLOR_VARIABLE_KEY, progressColor);
    }
  }

  private TextColor stationTextColor() {
    return CurrentSongHudWidget.toTextColor(this.hudWidget.getConfig().stationColor().get());
  }

  private TextColor songTextColor() {
    return CurrentSongHudWidget.toTextColor(this.hudWidget.getConfig().songColor().get());
  }

  private TextColor artistTextColor() {
    return CurrentSongHudWidget.toTextColor(this.hudWidget.getConfig().artistColor().get());
  }

  private static String stationLabel(RadioStream stream) {
    if (stream == null) {
      return "";
    }
    String name = stream.getDisplayName() != null && !stream.getDisplayName().isBlank()
        ? stream.getDisplayName()
        : stream.getName();
    return name == null ? "" : "EvilRadio - " + name;
  }
}
