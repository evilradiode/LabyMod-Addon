package de.evilradio.core.hudwidget.widget;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.EvilTextures;
import de.evilradio.core.activity.widget.MarqueeComponentWidget;
import de.evilradio.core.activity.widget.MarqueeCoordinator;
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
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;

@Link("widget/song-widget-modern.lss")
@AutoWidget
public class ModernCurrentSongWidget extends FlexibleContentWidget implements Updatable {

  private final EvilRadioAddon addon;
  private final CurrentSongHudWidget hudWidget;
  private final boolean isEditorContext;

  private static final String MAX_WIDTH_VARIABLE_KEY = "--modern-song-widget-max-width";
  private static final String MIN_WIDTH_VARIABLE_KEY = "--modern-song-widget-min-width";
  private static final String PROGRESS_FILL_WIDTH_KEY = "--modern-song-widget-progress-width";
  private static final String PROGRESS_MAX_WIDTH_VARIABLE_KEY = "--modern-song-widget-progress-max-width";

  private static final String BACKGROUND_VARIABLE_KEY = "--modern-song-widget-bg";
  private static final String BORDER_COLOR_VARIABLE_KEY = "--modern-song-widget-border-color";
  private static final String BACKGROUND_BLUR_VARIABLE_KEY = "--modern-song-widget-blur";
  private static final String PROGRESS_BAR_COLOR_VARIABLE_KEY = "--modern-song-widget-progress-bar-color";

  private ComponentWidget streamWidget;
  private ComponentWidget statusWidget;
  private MarqueeComponentWidget trackWidget;
  private MarqueeComponentWidget artistWidget;
  private final MarqueeCoordinator marqueeCoordinator = new MarqueeCoordinator();

  private FlexibleContentWidget previousSongContainer;
  private IconWidget previousSongIconWidget;
  private MarqueeComponentWidget previousTrackWidget;
  private MarqueeComponentWidget previousArtistWidget;

  private IconWidget coverWidget;
  private DivWidget progressTrack;
  private DivWidget progressFill;

  private static final float MAX_PLAYER_WIDTH = 220f;

  private String appliedCoverUrl;
  private long appliedArtworkGeneration = -1L;
  private long lastRenderedElapsed = -1L;
  private int lastRenderedProgressPercent = -1;
  private boolean lastRenderedHadDuration;
  private float progressTrackMaxWidth = 280f;
  private String lastTrackName = "";
  private String lastArtistName = "";
  private Component lastLivePrefix = null;

  public ModernCurrentSongWidget(EvilRadioAddon addon, CurrentSongHudWidget hudWidget, boolean isEditorContext) {
    this.addon = addon;
    this.hudWidget = hudWidget;
    this.isEditorContext = isEditorContext;
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
    this.lastLivePrefix = null;

    this.setVariable(MAX_WIDTH_VARIABLE_KEY, MAX_PLAYER_WIDTH);
    this.setVariable(MIN_WIDTH_VARIABLE_KEY, 160);
    this.setVariable(PROGRESS_MAX_WIDTH_VARIABLE_KEY, this.progressTrackMaxWidth);
    this.setVariable(PROGRESS_FILL_WIDTH_KEY, 0);
    this.applyBackgroundColor();

    boolean showCover = this.hudWidget.getConfig().showCover().get();
    if (!showCover) {
      this.addId("no-cover");
    }

    FlexibleContentWidget songContainer = new FlexibleContentWidget().addId("song-container");

    FlexibleContentWidget content = new FlexibleContentWidget().addId("content");

    this.coverWidget = new IconWidget(EvilTextures.LOGO);
    this.coverWidget.addId("cover");
    this.coverWidget.setVisible(showCover);
    content.addContent(this.coverWidget);

    FlexibleContentWidget player = new FlexibleContentWidget().addId("player");

    this.streamWidget = ComponentWidget.empty();
    this.streamWidget.addId("stream-name");
    player.addContent(this.streamWidget);

    this.trackWidget = new MarqueeComponentWidget();
    this.trackWidget.addId("track");
    player.addContent(this.trackWidget);

    this.artistWidget = new MarqueeComponentWidget();
    this.artistWidget.addId("artist");
    player.addContent(this.artistWidget);

    this.marqueeCoordinator.clear();
    this.marqueeCoordinator.register(this.trackWidget);
    this.marqueeCoordinator.register(this.artistWidget);

    FlexibleContentWidget progressRow = new FlexibleContentWidget().addId("progress-row");
    this.progressTrack = new DivWidget();
    this.progressTrack.addId("progress-track");
    this.progressFill = new DivWidget();
    this.progressFill.addId("progress-fill");
    this.progressTrack.addChild(this.progressFill);
    progressRow.addContent(this.progressTrack);

    this.statusWidget = ComponentWidget.empty();
    this.statusWidget.addId("status");
    progressRow.addContent(this.statusWidget);
    player.addContent(progressRow);

    content.addFlexibleContent(player);

    songContainer.addContent(content);
    this.addContent(songContainer);

    if(this.hudWidget.getConfig().showLastSong().get()) {
      this.previousSongContainer = new FlexibleContentWidget().addId("previous-song-container");
      this.previousSongContainer.setVisible(this.hudWidget.getConfig().showLastSong().get());

      FlexibleContentWidget previousSongContent = new FlexibleContentWidget().addId("previous-song-content");

      this.previousSongIconWidget = new IconWidget(EvilTextures.LOGO).addId("previous-song-cover");
      previousSongContent.addContent(this.previousSongIconWidget);

      FlexibleContentWidget previousSongPlayer = new FlexibleContentWidget().addId("previous-player");

      previousSongPlayer.addContent(ComponentWidget.i18n("evilradio.widget.previousSong").addId("previous-song-label"));

      this.previousArtistWidget = new MarqueeComponentWidget();
      this.previousArtistWidget.addId("previous-song-artist");
      this.previousTrackWidget = new MarqueeComponentWidget();
      this.previousTrackWidget.addId("previous-song-track");
      previousSongPlayer.addContent(this.previousArtistWidget);
      previousSongPlayer.addContent(this.previousTrackWidget);
      previousSongContent.addFlexibleContent(previousSongPlayer);
      this.previousSongContainer.addContent(previousSongContent);
      this.addContent(this.previousSongContainer);
    }

    this.marqueeCoordinator.register(this.previousArtistWidget);
    this.marqueeCoordinator.register(this.previousTrackWidget);

    this.applyScrollMode();

    this.updateTrack(this.addon.currentSongService().getCurrentSong(), this.addon.currentSongService()
        .getPreviousSong());
  }

  @Override
  public void tick() {
    super.tick();

    CurrentSong song = this.addon.currentSongService().getCurrentSong();
    if (song != null) {
      this.updateProgress(song);
    }

  }

  @Override
  public void update(String reason) {
    if (reason == null) {
      this.reInitialize();
      return;
    }

    if (reason.equals(CurrentSongHudWidget.SONG_CHANGE_REASON)) {
      this.updateTrack(this.addon.currentSongService().getCurrentSong(), this.addon.currentSongService()
          .getPreviousSong());
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
      this.updateTrack(this.addon.currentSongService().getCurrentSong(), this.addon.currentSongService()
          .getPreviousSong());
    }

    if (reason.equals(CurrentSongHudWidget.SCROLL_TEXT_REASON)) {
      this.applyScrollMode();
      this.updateTrack(this.addon.currentSongService().getCurrentSong(), this.addon.currentSongService()
          .getPreviousSong());
    }
  }

  private void updateTrack(CurrentSong currentSong, CurrentSong previousSong) {
    if (this.trackWidget == null || this.artistWidget == null || this.streamWidget == null
        || this.statusWidget == null) {
      return;
    }

    boolean isPlaying = this.addon.radioManager().isPlaying();
    RadioStream currentStream = this.addon.radioManager().getCurrentStream();
    NowPlayingConnectionState state = this.addon.currentSongService().getConnectionState();

    this.streamWidget.setVisible(true);
    this.statusWidget.setVisible(true);
    this.trackWidget.setVisible(true);
    this.artistWidget.setVisible(true);

    if (currentSong == null) {
      this.lastTrackName = "";
      this.lastArtistName = "";
      this.lastLivePrefix = null;
      if (isPlaying && currentStream != null) {
        this.streamWidget.setComponent(Component.text(stationLabel(currentStream)).color(this.stationTextColor()));
        this.statusWidget.setComponent(Component.empty());
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
      streamDisplayName = "Evil-Radio - " + currentSong.getStationName();
    }
    this.lastLivePrefix = buildLivePrefix(currentStream, currentSong);
    Component streamLine = Component.text(streamDisplayName).color(this.stationTextColor());
    if (this.lastLivePrefix != null) {
      streamLine = streamLine
          .append(Component.translatable("evilradio.widget.statusSeparator").color(NamedTextColor.GRAY))
          .append(this.lastLivePrefix);
    }
    this.streamWidget.setComponent(streamLine);

    this.renderStatusLine(currentSong);

    this.lastTrackName = limitedTitle(currentSong.getTitle());
    this.lastArtistName = currentSong.getArtist() == null ? "" : currentSong.getArtist();
    this.trackWidget.setMarqueeText(this.lastTrackName, this.songTextColor());
    this.artistWidget.setMarqueeText(this.lastArtistName, this.artistTextColor());
    this.marqueeCoordinator.onContentChanged();

    FontRenderer fontRenderer = Laby.references().minecraftFontRenderer();
    String timeSample = formatTimeLabel(currentSong);
    float streamNameWidth = fontRenderer.getWidth(streamDisplayName);
    float timeWidth = fontRenderer.getWidth(timeSample) * 0.7f;
    float trackWidth = fontRenderer.getWidth(this.lastTrackName);
    float artistWidth = fontRenderer.getWidth(this.lastArtistName);
    float naturalWidth = Math.max(
        Math.max(streamNameWidth, trackWidth),
        Math.max(artistWidth, timeWidth > 0 ? timeWidth + 40f : 0)
    );
    float playerWidth = Math.clamp(naturalWidth, 160f, MAX_PLAYER_WIDTH);

    this.setVariable(MIN_WIDTH_VARIABLE_KEY, 160);
    this.setVariable(MAX_WIDTH_VARIABLE_KEY, playerWidth);
    this.progressTrackMaxWidth = Math.max(40f, playerWidth - (timeWidth > 0 ? timeWidth + 6 : 0));
    this.setVariable(PROGRESS_MAX_WIDTH_VARIABLE_KEY, this.progressTrackMaxWidth);

    if(this.previousArtistWidget != null && this.previousTrackWidget != null) {
      this.previousArtistWidget.setMarqueeText(previousSong.getArtist(), this.artistTextColor());
      this.previousTrackWidget.setMarqueeText(limitedTitle(previousSong.getTitle()), this.songTextColor());
      this.marqueeCoordinator.onContentChanged();
    }

    this.applyCover(currentSong, this.coverWidget);
    this.applyCover(previousSong, this.previousSongIconWidget);
    this.updateProgress(currentSong);
  }

  private Component buildLivePrefix(RadioStream currentStream, CurrentSong currentSong) {
    String streamName = currentStream != null ? currentStream.getName() : null;
    boolean isMashup = streamName != null && streamName.equalsIgnoreCase("Mashup");
    if (!isMashup) {
      return null;
    }

    boolean onAir = currentSong.isOnAir();
    boolean twitch = currentSong.isTwitch();
    Component onAirComponent = Component.empty();
    boolean hasContent = false;

    if (onAir) {
      onAirComponent = Component.translatable("evilradio.widget.onAir").color(NamedTextColor.RED);
      hasContent = true;
    }
    if (twitch) {
      TextColor twitchColor = TextColor.color(145, 70, 255);
      if (hasContent) {
        onAirComponent = onAirComponent.append(
            Component.translatable("evilradio.widget.statusSeparator").color(NamedTextColor.GRAY)
        );
      }
      onAirComponent = onAirComponent.append(
          Component.translatable("evilradio.widget.twitch").color(twitchColor)
      );
      hasContent = true;
    }
    if (onAir && currentSong.getModeratorName() != null && !currentSong.getModeratorName().isEmpty()) {
      onAirComponent = onAirComponent.append(
          Component.translatable("evilradio.widget.statusSeparator").color(NamedTextColor.GRAY)
              .append(Component.text(currentSong.getModeratorName()).color(NamedTextColor.WHITE))
      );
      hasContent = true;
    }
    return hasContent ? onAirComponent : null;
  }

  private void renderStatusLine(CurrentSong song) {
    String timeLabel = formatTimeLabel(song);
    if (timeLabel.isEmpty()) {
      this.statusWidget.setComponent(Component.empty());
    } else {
      this.statusWidget.setComponent(Component.text(timeLabel).color(NamedTextColor.GRAY));
    }
  }

  private static String formatTimeLabel(CurrentSong song) {
    if (song == null) {
      return "";
    }
    long elapsed = song.getCurrentElapsedSeconds();
    if (song.hasKnownDuration()) {
      return CurrentSong.formatTime(elapsed) + " / " + CurrentSong.formatTime(song.getDuration());
    }
    return CurrentSong.formatTime(elapsed);
  }

  private String limitedTitle(String title) {
    if (title == null) {
      return "";
    }
    int max = CurrentSongHudWidget.MAX_TITLE_LENGTH;
    if (title.length() <= max) {
      return title;
    }
    return title.substring(0, max);
  }

  private void applyCover(CurrentSong currentSong, IconWidget coverWidget) {
    if (coverWidget == null || currentSong == null) {
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
      coverWidget.icon().set(Icon.url(artworkUrl));
    });

    if (url == null || url.isBlank()) {
      if (this.appliedCoverUrl != null) {
        return;
      }
      coverWidget.icon().set(EvilTextures.LOGO);
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

    // Pixelbreite statt calc(percent * 1%) – LabyMod-LSS aktualisiert %-Breiten sonst nicht zuverlässig
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
    this.setVariable(BORDER_COLOR_VARIABLE_KEY, this.hudWidget.getConfig().borderColor().get().get());
    this.setVariable(BACKGROUND_BLUR_VARIABLE_KEY, this.hudWidget.getConfig().backgroundBlur().get());
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

  private void applyScrollMode() {
    boolean scroll = this.hudWidget.getConfig().scrollLongText().get();
    if (this.trackWidget != null) {
      this.trackWidget.setScrollMode(scroll);
    }
    if (this.artistWidget != null) {
      this.artistWidget.setScrollMode(scroll);
    }
    if (this.previousTrackWidget != null) {
      this.previousTrackWidget.setScrollMode(scroll);
    }
    if (this.previousArtistWidget != null) {
      this.previousArtistWidget.setScrollMode(scroll);
    }
    this.marqueeCoordinator.onContentChanged();
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
