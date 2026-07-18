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

@Link("widget/song-widget-modern.lss")
@AutoWidget
public class ModernCurrentSongWidget extends FlexibleContentWidget implements Updatable {

  private final EvilRadioAddon addon;
  private final CurrentSongHudWidget hudWidget;
  private final boolean isEditorContext;

  private static final String MAX_WIDTH_VARIABLE_KEY = "--modern-song-widget-max-width";
  private static final String MIN_WIDTH_VARIABLE_KEY = "--modern-song-widget-min-width";
  private static final String PROGRESS_VARIABLE_KEY = "--modern-song-widget-progress";
  private static final String PROGRESS_MAX_WIDTH_VARIABLE_KEY = "--modern-song-widget-progress-max-width";
  private static final String BACKGROUND_VARIABLE_KEY = "--modern-song-widget-bg";
  private static final String BORDER_COLOR_VARIABLE_KEY = "--modern-song-widget-border-color";

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
  private String lastTrackName = "";
  private Component lastLivePrefix = Component.empty();

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
    this.lastLivePrefix = Component.empty();

    this.setVariable(MAX_WIDTH_VARIABLE_KEY, 300);
    this.setVariable(MIN_WIDTH_VARIABLE_KEY, 200);
    this.setVariable(PROGRESS_VARIABLE_KEY, 0);
    this.applyBackgroundColor();

    boolean showCover = this.hudWidget.getConfig().showCover().get();
    if (!showCover) {
      this.addId("no-cover");
    }

    FlexibleContentWidget content = new FlexibleContentWidget().addId("content");

    this.coverWidget = new IconWidget(EvilTextures.LOGO);
    this.coverWidget.addId("cover");
    this.coverWidget.setVisible(showCover);
    content.addContent(coverWidget);

    FlexibleContentWidget player = new FlexibleContentWidget().addId("player");

    VerticalListWidget<ComponentWidget> text = new VerticalListWidget<>();
    text.addId("text");

    // Immer 4 Zeilen – wie im Original-Layout
    this.streamWidget = ComponentWidget.empty();
    text.addChild(this.streamWidget);

    this.statusWidget = ComponentWidget.empty();
    text.addChild(this.statusWidget);

    this.trackWidget = ComponentWidget.empty();
    text.addChild(this.trackWidget);

    this.artistWidget = ComponentWidget.empty();
    text.addChild(this.artistWidget);

    player.addFlexibleContent(text);

    this.progressTrack = new DivWidget();
    this.progressTrack.addId("progress-track");
    this.progressFill = new DivWidget();
    this.progressFill.addId("progress-fill");
    this.progressTrack.addChild(this.progressFill);

    content.addFlexibleContent(player);

    this.addContent(content);
    this.addContent(this.progressTrack);
    this.updateTrack(this.addon.currentSongService().getCurrentSong());
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
      this.updateTrack(this.addon.currentSongService().getCurrentSong());
    }

    if (reason.equals(CurrentSongHudWidget.TITLE_LENGTH_CHANGE_REASON)) {
      this.updateTitleLength(this.addon.currentSongService().getCurrentSong());
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

    if (reason.equals(CurrentSongHudWidget.BACKGROUND_COLOR_REASON)) {
      this.applyBackgroundColor();
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
      this.lastLivePrefix = Component.empty();
      if (isPlaying && currentStream != null) {
        this.streamWidget.setComponent(Component.text(stationLabel(currentStream)).color(NamedTextColor.GRAY));
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
    this.streamWidget.setComponent(Component.text(streamDisplayName).color(NamedTextColor.GRAY));

    this.lastLivePrefix = buildLivePrefix(currentStream, currentSong);
    this.renderStatusLine(currentSong);

    this.lastTrackName = limitedTitle(currentSong.getTitle());
    this.trackWidget.setComponent(Component.text(this.lastTrackName).color(NamedTextColor.WHITE));

    String artistName = currentSong.getArtist() == null ? "" : currentSong.getArtist();
    this.artistWidget.setComponent(Component.text(artistName).color(NamedTextColor.GRAY));

    FontRenderer fontRenderer = Laby.references().minecraftFontRenderer();
    float minWidgetWidth = (!hasId("no-cover") ? 44 : 0) + 30;
    String statusSample = statusLinePlain(currentSong);
    float streamNameWidth = fontRenderer.getWidth(streamDisplayName);
    float statusWidth = fontRenderer.getWidth(statusSample);
    float trackWidth = fontRenderer.getWidth(this.lastTrackName);
    float artistWidth = fontRenderer.getWidth(artistName);
    float contentWidth = Math.max(
        Math.max(streamNameWidth, statusWidth),
        Math.max(trackWidth, artistWidth)
    );

    this.setVariable(MIN_WIDTH_VARIABLE_KEY, Math.max(minWidgetWidth, streamNameWidth));
    this.setVariable(MAX_WIDTH_VARIABLE_KEY, (minWidgetWidth + contentWidth));
    this.setVariable(PROGRESS_MAX_WIDTH_VARIABLE_KEY, (minWidgetWidth + contentWidth) - 20);

    this.applyCover(currentSong);
    this.updateProgress(currentSong);
  }

  private Component buildLivePrefix(RadioStream currentStream, CurrentSong currentSong) {
    String streamName = currentStream != null ? currentStream.getName() : null;
    boolean isMashup = streamName != null && streamName.equalsIgnoreCase("Mashup");
    if (!isMashup) {
      return Component.empty();
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
    return hasContent ? onAirComponent : Component.empty();
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
            .append(Component.translatable("evilradio.widget.statusSeparator").color(NamedTextColor.GRAY))
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
    String streamName = this.addon.radioManager().getCurrentStream() != null
        ? this.addon.radioManager().getCurrentStream().getName()
        : null;
    boolean isMashup = streamName != null && streamName.equalsIgnoreCase("Mashup");
    if (isMashup && (song.isOnAir() || song.isTwitch())) {
      String live = "ON AIR";
      if (song.getModeratorName() != null && !song.getModeratorName().isEmpty()) {
        live = live + " | " + song.getModeratorName();
      }
      return timeLabel.isEmpty() ? live : live + " | " + timeLabel;
    }
    return timeLabel;
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

  private void updateTitleLength(CurrentSong currentSong) {
    if (currentSong == null || this.trackWidget == null) return;
    this.lastTrackName = limitedTitle(currentSong.getTitle());
    this.trackWidget.setComponent(Component.text(this.lastTrackName).color(NamedTextColor.WHITE));
    this.addon.labyAPI().minecraft().executeOnRenderThread(() -> this.trackWidget.updateComponent());
  }

  private String limitedTitle(String title) {
    if (title == null) return "";
    if (this.hudWidget.getConfig().limitTitleLength().get() && this.hudWidget.getConfig().maxTitleLength().get() > 0) {
      return title.substring(0, Math.min(title.length(), this.hudWidget.getConfig().maxTitleLength().get()));
    }
    return title;
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

    if (hasDuration) {
      this.setVariable(PROGRESS_VARIABLE_KEY, percent);
      this.progressTrack.removeId("indeterminate");
      this.setProgressVisible(true);
    } else {
      this.setVariable(PROGRESS_VARIABLE_KEY, 0);
      this.setProgressVisible(true);
      this.progressTrack.addId("indeterminate");
    }
  }

  private void setProgressVisible(boolean visible) {
    if (!visible) {
      this.lastRenderedElapsed = -1L;
      this.lastRenderedProgressPercent = -1;
      this.lastRenderedHadDuration = false;
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
