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
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;

@Link("widget/song-widget-modern.lss")
@AutoWidget
public class ModernCurrentSongWidget extends FlexibleContentWidget implements Updatable {

  private final EvilRadioAddon addon;
  private final CurrentSongHudWidget hudWidget;

  private static final String PROGRESS_FILL_WIDTH_KEY = "--modern-song-widget-progress-width";
  private static final String MAX_PLAYER_WIDTH_KEY = "--modern-song-widget-max-player-width";

  private static final String BACKGROUND_VARIABLE_KEY = "--modern-song-widget-bg";
  private static final String BORDER_COLOR_VARIABLE_KEY = "--modern-song-widget-border-color";
  private static final String BACKGROUND_BLUR_VARIABLE_KEY = "--modern-song-widget-blur";
  private static final String PROGRESS_BAR_COLOR_VARIABLE_KEY = "--modern-song-widget-progress-bar-color";

  private ComponentWidget streamWidget;
  private ComponentWidget statusWidget;
  private ComponentWidget trackWidget;
  private ComponentWidget artistWidget;

  private FlexibleContentWidget previousSongContainer;
  private IconWidget previousSongIconWidget;
  private ComponentWidget previousTrackWidget;
  private ComponentWidget previousArtistWidget;

  private IconWidget coverWidget;
  private DivWidget progressTrack;
  private DivWidget progressFill;

  private static final float MAX_PLAYER_WIDTH = 160f;

  private String appliedCoverUrl;
  private long appliedArtworkGeneration = -1L;
  private long lastRenderedElapsed = -1L;
  private int lastRenderedProgressPercent = -1;
  private boolean lastRenderedHadDuration;
  private float progressTrackMaxWidth = 280f;
  private String lastTrackName = "";
  private String lastArtistName = "";
  private Component lastLivePrefix = null;
  private boolean lastLiveBadgeTwitchPhase;
  /** Ob der Previous-Block aktuell im Tree hängt (für HUD-Höhe / Snapping). */
  private boolean previousSectionMounted;

  public ModernCurrentSongWidget(EvilRadioAddon addon, CurrentSongHudWidget hudWidget) {
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
    this.lastLivePrefix = null;
    this.previousSectionMounted = false;
    this.previousSongContainer = null;
    this.previousSongIconWidget = null;
    this.previousTrackWidget = null;
    this.previousArtistWidget = null;
    this.lastLiveBadgeTwitchPhase = LiveStatusLine.showTwitchPhase(System.currentTimeMillis());

    this.setVariable(MAX_PLAYER_WIDTH_KEY, MAX_PLAYER_WIDTH);
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

    this.trackWidget = ComponentWidget.empty();
    this.trackWidget.addId("track");
    player.addContent(this.trackWidget);

    this.artistWidget = ComponentWidget.empty();
    this.artistWidget.addId("artist");
    player.addContent(this.artistWidget);

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

    CurrentSong currentSong = this.addon.currentSongService().getCurrentSong();
    CurrentSong previousSong = this.addon.currentSongService().getPreviousSong();
    // Nur mounten wenn wirklich Inhalt da ist – sonst bleibt HUD-Höhe/Snapping zu groß
    if (shouldMountPreviousSection(currentSong, previousSong)) {
      this.mountPreviousSongSection();
    }

    this.updateTrack(currentSong, previousSong);
  }

  private void mountPreviousSongSection() {
    this.previousSongContainer = new FlexibleContentWidget().addId("previous-song-container");

    FlexibleContentWidget previousSongContent = new FlexibleContentWidget().addId("previous-song-content");

    this.previousSongIconWidget = new IconWidget(EvilTextures.LOGO).addId("previous-song-cover");
    previousSongContent.addContent(this.previousSongIconWidget);

    FlexibleContentWidget previousSongPlayer = new FlexibleContentWidget().addId("previous-player");

    previousSongPlayer.addContent(ComponentWidget.i18n("evilradio.widget.previousSong").addId("previous-song-label"));

    this.previousTrackWidget = ComponentWidget.empty();
    this.previousTrackWidget.addId("previous-song-track");
    previousSongPlayer.addContent(this.previousTrackWidget);

    this.previousArtistWidget = ComponentWidget.empty();
    this.previousArtistWidget.addId("previous-song-artist");
    previousSongPlayer.addContent(this.previousArtistWidget);

    previousSongContent.addFlexibleContent(previousSongPlayer);
    this.previousSongContainer.addContent(previousSongContent);
    this.addContent(this.previousSongContainer);

    this.previousSectionMounted = true;
  }

  private boolean shouldMountPreviousSection(CurrentSong currentSong, CurrentSong previousSong) {
    if (!this.hudWidget.getConfig().showLastSong().get()) {
      return false;
    }
    if (currentSong != null && currentSong.isOnAir()) {
      return false;
    }
    return previousSong != null && previousSong.isUsableAsPreviousSong();
  }

  @Override
  public void tick() {
    super.tick();

    CurrentSong song = this.addon.currentSongService().getCurrentSong();
    if (song != null) {
      this.updateProgress(song);
      this.addon.currentSongService().refreshIfStuckAtEnd();
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

    if(reason.equals(CurrentSongHudWidget.TOGGLE_PREVIOUS_SONG_REASON)) {
      this.reInitialize();
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
      if (this.syncPreviousSectionMount(null, previousSong)) {
        return;
      }
      if (isPlaying && currentStream != null) {
        this.streamWidget.setComponent(Component.text(stationLabel(currentStream, false)).color(this.stationTextColor()));
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

    String streamDisplayName = stationLabel(currentStream, LiveStatusLine.hasLiveBadges(currentSong));
    if (streamDisplayName.isBlank() && currentSong.getStationName() != null) {
      streamDisplayName = currentSong.getStationName();
    }
    this.lastLiveBadgeTwitchPhase = LiveStatusLine.showTwitchPhase(System.currentTimeMillis());
    this.lastLivePrefix = LiveStatusLine.buildPrefix(
        currentStream, currentSong, this.lastLiveBadgeTwitchPhase);
    Component streamLine = Component.text(streamDisplayName).color(this.stationTextColor());
    if (this.lastLivePrefix != null) {
      streamLine = streamLine
          .append(Component.text(" | ").color(NamedTextColor.GRAY))
          .append(this.lastLivePrefix);
    }
    this.streamWidget.setComponent(streamLine);

    this.renderStatusLine(currentSong);

    this.lastTrackName = currentSong.getDisplayTitle();
    this.lastArtistName = currentSong.getArtist() == null ? "" : currentSong.getArtist();
    this.trackWidget.setComponent(Component.text(this.lastTrackName, this.songTextColor()));
    this.artistWidget.setComponent(Component.text(this.lastArtistName, this.artistTextColor()));

    FontRenderer fontRenderer = Laby.references().minecraftFontRenderer();
    String timeSample = formatTimeLabel(currentSong);
    String streamLinePlain = streamDisplayName;
    String livePlain = LiveStatusLine.plainPrefix(
        currentStream, currentSong, this.lastLiveBadgeTwitchPhase);
    if (!livePlain.isEmpty()) {
      streamLinePlain = streamDisplayName + " | " + livePlain;
    }
    float streamNameWidth = fontRenderer.getWidth(streamLinePlain);
    float timeWidth = fontRenderer.getWidth(timeSample) * 0.7f;
    float trackWidth = fontRenderer.getWidth(this.lastTrackName);
    float artistWidth = fontRenderer.getWidth(this.lastArtistName);
    float naturalWidth = Math.max(
        Math.max(streamNameWidth, trackWidth),
        Math.max(artistWidth, timeWidth > 0 ? timeWidth + 40f : 0)
    );
    float playerWidth = Math.clamp(naturalWidth + 4f, 160f, MAX_PLAYER_WIDTH);

    this.progressTrackMaxWidth = Math.max(40f, playerWidth - timeWidth);

    // Mount-Status muss zur HUD-Höhe passen – setVisible(false) reicht für Snapping nicht
    if (this.syncPreviousSectionMount(currentSong, previousSong)) {
      return;
    }

    if (this.previousSectionMounted
        && this.previousArtistWidget != null
        && this.previousTrackWidget != null
        && previousSong != null) {
      String previousTrackName = previousSong.getDisplayTitle();
      String previousArtistName = previousSong.getArtist();

      this.previousTrackWidget.setComponent(Component.text(previousTrackName, this.songTextColor()));
      this.previousArtistWidget.setComponent(Component.text(previousArtistName == null ? "" : previousArtistName, this.artistTextColor()));
      this.applyCover(previousSong, this.previousSongIconWidget);
    }

    this.applyCover(currentSong, this.coverWidget);
    this.updateProgress(currentSong);
  }

  /**
   * Hängt den Previous-Block an bzw. entfernt ihn per Reinit, damit die HUD-Bounds schrumpfen.
   *
   * @return {@code true} wenn ein Reinit ausgelöst wurde (Caller soll abbrechen)
   */
  private boolean syncPreviousSectionMount(CurrentSong currentSong, CurrentSong previousSong) {
    boolean shouldMount = shouldMountPreviousSection(currentSong, previousSong);
    if (shouldMount == this.previousSectionMounted) {
      return false;
    }
    this.reInitialize();
    return true;
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
    Component streamLine = Component.text(streamDisplayName).color(this.stationTextColor());
    if (this.lastLivePrefix != null) {
      streamLine = streamLine
          .append(Component.text(" | ").color(NamedTextColor.GRAY))
          .append(this.lastLivePrefix);
    }
    this.streamWidget.setComponent(streamLine);
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
    String label = song.getPlaytimeLabel();
    if (label != null && !label.isBlank()) {
      return label;
    }
    return CurrentSong.formatTime(song.getCurrentElapsedSeconds());
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
