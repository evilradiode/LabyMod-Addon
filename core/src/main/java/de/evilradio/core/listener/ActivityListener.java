package de.evilradio.core.listener;

import de.evilradio.core.EvilConstants;
import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.EvilTextures;
import de.evilradio.core.EvilTextures.SpriteCommon;
import de.evilradio.core.EvilTextures.SpriteControls;
import de.evilradio.core.activity.picker.StationPickerController;
import de.evilradio.core.activity.widget.MashupLiveBannerWidget;
import de.evilradio.core.configuration.EvilRadioConfiguration.MenuPlayerPosition;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import de.evilradio.core.hudwidget.widget.LiveStatusLine;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.schedule.ScheduleShow;
import de.evilradio.core.song.CurrentSong;
import de.evilradio.core.song.CurrentSongService.ShowStatus;
import java.util.List;
import java.util.concurrent.TimeUnit;
import de.evilradio.core.song.azuracast.AzuraCastNowPlayingService;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.models.OperatingSystem;
import net.labymod.api.client.gui.hud.hudwidget.HudWidget.Updatable;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.activity.Activity;
import net.labymod.api.client.gui.screen.widget.Widget;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.activity.Document;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.HorizontalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.event.Phase;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.gui.screen.ActivityInitializeEvent;
import net.labymod.api.event.client.lifecycle.GameTickEvent;
import net.labymod.api.util.concurrent.task.Task;

public class ActivityListener implements Updatable {

  private EvilRadioAddon addon;

  public ActivityListener(EvilRadioAddon addon) {
    this.addon = addon;
    this.addon.configuration().menuPlayerPosition().addChangeListener(menuPlayerPosition ->
        this.addon.labyAPI().minecraft().executeOnRenderThread(
        () -> this.applyMainMenuPlayerVisibility(menuPlayerPosition != MenuPlayerPosition.DISABLED)));

    this.addon.configuration().debugForceMashupLive().addChangeListener(enabled ->
        this.addon.labyAPI().minecraft().executeOnRenderThread(() -> {
          this.lastBannerMode = -1;
          if (Boolean.TRUE.equals(enabled) && this.debugForceMashupLive()) {
            this.applyMashupLiveBanner(DEBUG_MASHUP_LIVE);
          } else {
            this.refreshMashupLiveBanner();
          }
        }));
  }

  @Subscribe
  public void onActivityInitialize(ActivityInitializeEvent event) {
    if (!isMenuPlayerHost(event.getIdentifier())) return;
    this.mainMenuActivity = event.activity();
    this.detachMenuPlayerWidgets();
    if (this.addon.configuration().menuPlayerPosition().get() == MenuPlayerPosition.DISABLED) return;
    this.addRadioController(event.activity());
  }

  @Subscribe
  public void onGameTick(GameTickEvent event) {
    if (event.phase() != Phase.PRE || this.songContainer == null) return;

    CurrentSong song = this.addon.currentSongService().getCurrentSong();
    if (song != null) {
      this.updateProgress(song);
    } else {
      this.setProgressVisible(false);
    }

    boolean twitchPhase = LiveStatusLine.showTwitchPhase(System.currentTimeMillis());
    if (twitchPhase != this.lastLiveBadgeTwitchPhase && this.hasRotatingLiveLine(song)) {
      this.lastLiveBadgeTwitchPhase = twitchPhase;
      this.refreshLiveLine(song);
    }
  }

  @Override
  public void update(String reason) {
    if (reason == null || reason.equals(CurrentSongHudWidget.SONG_CHANGE_REASON)) {
      this.refreshPlayPauseIcon();
      this.updateTrack(this.addon.currentSongService().getCurrentSong());
    }
  }

  private Activity mainMenuActivity;
  private FlexibleContentWidget songContainer;
  private FlexibleContentWidget songContent;
  private MashupLiveBannerWidget mashupLiveBanner;
  private IconWidget restoreWidget;
  private IconWidget coverWidget;
  private ComponentWidget streamWidget;
  private ComponentWidget liveWidget;
  private ComponentWidget trackWidget;
  private ComponentWidget artistWidget;
  private FlexibleContentWidget progressRow;
  private DivWidget progressTrack;
  private DivWidget progressFill;
  private ComponentWidget statusWidget;

  private ButtonWidget playPauseButton;

  private static final String MAIN_MENU_ACTIVITY_ID = "labymod:main_menu";
  private static final String MULTIPLAYER_ACTIVITY_ID = "labymod:multiplayer";
  private static final String PROGRESS_FILL_WIDTH_KEY = "--menu-song-progress-width";
  private static final float PROGRESS_TRACK_WIDTH = 120f;

  private Component lastLivePrefix = null;
  private boolean lastLiveBadgeTwitchPhase;
  private long lastRenderedElapsed = -1L;
  private int lastRenderedProgressPercent = -1;
  private boolean lastRenderedHadDuration;
  private ShowStatus mashupShowStatus;
  private Task mashupLiveTask;
  private boolean menuPlayerMinimized;
  private int lastBannerMode;
  private boolean menuRelayoutScheduled;
  private boolean menuPlayerAttached;

  private static final ShowStatus DEBUG_MASHUP_LIVE =
      new ShowStatus(true, true, "Debug-DJ", 0L, 0L, "20:00", "22:00");

  private boolean debugForceMashupLive() {
    return this.addon.configuration().debugForceMashupLive().get()
        && this.addon.isUuidAllowed(this.addon.labyAPI().getUniqueId());
  }

  private void applyMainMenuPlayerVisibility(boolean enabled) {
    if (this.mainMenuActivity == null) return;
    if (enabled) {
      this.rebuildMenuPlayer();
      return;
    }
    this.removeMenuPlayerFromDocument();
    this.stopMashupLiveUpdates();
    this.detachMenuPlayerWidgets();
  }

  private void rebuildMenuPlayer() {
    if (this.mainMenuActivity == null ||
        this.addon.configuration().menuPlayerPosition().get() == MenuPlayerPosition.DISABLED) return;
    this.removeMenuPlayerFromDocument();
    this.detachMenuPlayerWidgets();
    this.addRadioController(this.mainMenuActivity);
  }

  private void removeMenuPlayerFromDocument() {
    if (this.mainMenuActivity == null) return;
    Document document = this.mainMenuActivity.document();
    document.removeChild("song-container");
    document.removeChild("player-restore");
  }

  private void clearMainMenuPlayerWidgets() {
    this.stopMashupLiveUpdates();
    this.detachMenuPlayerWidgets();
    this.mashupShowStatus = null;
    this.lastBannerMode = 0;
    this.menuRelayoutScheduled = false;
    this.menuPlayerAttached = false;
    this.lastRenderedElapsed = -1L;
    this.lastRenderedProgressPercent = -1;
    this.lastRenderedHadDuration = false;
  }

  private void detachMenuPlayerWidgets() {
    this.menuPlayerAttached = false;
    this.songContainer = null;
    this.songContent = null;
    this.mashupLiveBanner = null;
    this.restoreWidget = null;
    this.coverWidget = null;
    this.streamWidget = null;
    this.liveWidget = null;
    this.trackWidget = null;
    this.artistWidget = null;
    this.progressRow = null;
    this.progressTrack = null;
    this.progressFill = null;
    this.statusWidget = null;
    this.playPauseButton = null;
  }

  private void addRadioController(Activity activity) {
    Document document = activity.document();
    activity.addStyle("evilradio", "activity/menu.lss");

    this.lastLivePrefix = null;
    this.lastRenderedElapsed = -1L;
    this.lastRenderedProgressPercent = -1;
    this.lastRenderedHadDuration = false;
    if (this.menuPlayerMinimized) {
      this.restoreWidget = new IconWidget(EvilTextures.LOGO).addId("player-restore");
      this.applyMenuPlayerPosition(this.restoreWidget);
      this.restoreWidget.setHoverComponent(
          Component.translatable("evilradio.widget.restorePlayer").color(NamedTextColor.GRAY));
      this.restoreWidget.setPressable(() -> {
        this.menuPlayerMinimized = false;
        this.rebuildMenuPlayer();
      });
      document.addChildInitialized(this.restoreWidget);
      this.startMashupLiveUpdates();
      return;
    }

    this.songContainer = new FlexibleContentWidget().addId("song-container");
    this.applyMenuPlayerPosition(this.songContainer);

    HorizontalListWidget chrome = new HorizontalListWidget().addId("player-chrome");

    ButtonWidget settingsButton = ButtonWidget.icon(SpriteCommon.SETTINGS, () -> {
      this.addon.labyAPI().coreSettingRegistry().findSetting((CharSequence) this.addon.labyAPI().getNamespace(this.addon))
          .ifPresent(this.addon.labyAPI()::showSetting);
    }).addId("player-settings");
    settingsButton.setHoverComponent(
        Component.translatable("evilradio.widget.playerSettings")
            .color(NamedTextColor.GRAY));
    chrome.addEntry(settingsButton);

    boolean left = this.isMenuPlayerLeft();
    ButtonWidget moveButton = ButtonWidget.icon(left ? SpriteCommon.ARROW_RIGHT : SpriteCommon.ARROW_LEFT, this::toggleMenuPlayerSide)
        .addId("player-move");
    moveButton.setHoverComponent(
        Component.translatable(
                left ? "evilradio.widget.movePlayerRight" : "evilradio.widget.movePlayerLeft")
            .color(NamedTextColor.GRAY));
    chrome.addEntry(moveButton);

    ButtonWidget closeButton = ButtonWidget.icon(EvilTextures.SpriteCommon.X, () -> {
      this.menuPlayerMinimized = true;
      this.rebuildMenuPlayer();
    }).addId("player-close");
    closeButton.setHoverComponent(
        Component.translatable("evilradio.widget.minimizePlayer").color(NamedTextColor.GRAY));
    chrome.addEntry(closeButton);
    this.songContainer.addContent(chrome);

    this.mashupLiveBanner = new MashupLiveBannerWidget();
    this.mashupLiveBanner.addId("mashup-live-banner");
    this.mashupLiveBanner.bind(this::playMashup, () -> openUrl(EvilConstants.WISH_BOX_URL), () -> openUrl(EvilConstants.TWITCH_URL));
    this.lastBannerMode = this.currentBannerMode();
    this.mashupLiveBanner.apply(
        this.lastBannerMode,
        this.showWishBoxButton(),
        this.showTwitchButton(),
        this.mashupShowStatus,
        this.upcomingShow());

    this.songContent = new FlexibleContentWidget().addId("content");

    this.coverWidget = new IconWidget(this.stationIcon());
    this.coverWidget.addId("cover");
    this.songContent.addContent(this.coverWidget);

    FlexibleContentWidget player = new FlexibleContentWidget().addId("player");

    this.streamWidget = ComponentWidget.empty();
    this.streamWidget.addId("stream-name");
    this.setLayoutHidden(this.streamWidget, true);
    player.addContent(this.streamWidget);

    this.liveWidget = ComponentWidget.empty();
    this.liveWidget.addId("live-status");
    player.addContent(this.liveWidget);

    this.trackWidget = ComponentWidget.empty();
    this.trackWidget.addId("track");
    player.addContent(this.trackWidget);

    this.artistWidget = ComponentWidget.empty();
    this.artistWidget.addId("artist");
    this.setLayoutHidden(this.artistWidget, true);
    player.addContent(this.artistWidget);

    this.progressRow = new FlexibleContentWidget().addId("progress-row");
    this.progressTrack = new DivWidget().addId("progress-track");
    this.progressFill = new DivWidget().addId("progress-fill");
    this.progressFill.setVariable(PROGRESS_FILL_WIDTH_KEY, 0f);
    this.progressTrack.addChild(this.progressFill);
    this.progressRow.addContent(this.progressTrack);
    this.statusWidget = ComponentWidget.empty().addId("status");
    this.progressRow.addContent(this.statusWidget);
    this.setLayoutHidden(this.progressRow, true);
    player.addContent(this.progressRow);

    HorizontalListWidget controlsContainer = new HorizontalListWidget().addId("controls");

    ButtonWidget previousButton = ButtonWidget.icon(SpriteControls.PREVIOUS).addId("previous");
    previousButton.setPressable(() -> {
      this.switchStream(-1);
    });
    controlsContainer.addEntry(previousButton);

    this.playPauseButton = ButtonWidget.icon(this.addon.radioManager().isPlaying() ? SpriteControls.STOP : SpriteControls.PLAY).addId("play-pause");
    this.playPauseButton.setPressable(this::togglePlayback);
    controlsContainer.addEntry(this.playPauseButton);

    ButtonWidget nextButton = ButtonWidget.icon(SpriteControls.NEXT).addId("next");
    nextButton.setPressable(() -> {
      this.switchStream(1);
    });
    controlsContainer.addEntry(nextButton);

    player.addContent(controlsContainer);

    ButtonWidget stationPickerButton = ButtonWidget.component(
        Component.translatable("evilradio.widget.openPicker"),
        this.addon::openStationPicker)
        .addId("station-picker");
    player.addContent(stationPickerButton);

    SliderWidget volumeSlider = new SliderWidget(1.0F, newVolume -> {
      float rounded = Math.round(newVolume);
      this.addon.configuration().volume().set(rounded);
    })
        .range(0.0F, 100.0F)
        .withFormatter(value -> Component.text(Math.round(value) + "%"))
        .addId("volume-slider");
    volumeSlider.setValue(this.addon.configuration().volume().get());
    player.addContent(volumeSlider);

    this.songContent.addContent(player);
    this.songContainer.addContent(this.mashupLiveBanner);
    this.songContainer.addContent(this.songContent);

    this.refreshPlayPauseIcon();
    this.updateTrack(this.addon.currentSongService().getCurrentSong());
    document.addChildInitialized(this.songContainer);
    this.menuPlayerAttached = true;
    this.startMashupLiveUpdates();
  }

  private void switchStream(int direction) {
    List<RadioStream> streams = this.addon.radioStreamService().streams();
    if (streams.isEmpty()) return;

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

    if (nextPlayable == null) return;

    this.playPauseButton.updateIcon(SpriteControls.STOP);
    this.addon.radioManager().playStream(nextPlayable);
    this.refreshPlayPauseIcon();
    this.updateTrack(this.addon.currentSongService().getCurrentSong());
  }

  private void togglePlayback() {
    boolean startPlayback = !this.addon.radioManager().isPlaying();
    this.playPauseButton.updateIcon(startPlayback ? SpriteControls.STOP : SpriteControls.PLAY);

    if (startPlayback && this.addon.radioManager().getCurrentStream() == null) {
      RadioStream lastStream = this.addon.radioStreamService().findStreamById(
          this.addon.configuration().lastStreamId().get());
      if (lastStream != null) {
        this.addon.radioManager().playStream(lastStream);
      } else {
        this.addon.radioManager().togglePlayStop();
      }
    } else {
      this.addon.radioManager().togglePlayStop();
    }

    this.refreshPlayPauseIcon();
    this.updateTrack(this.addon.currentSongService().getCurrentSong());
  }

  private void refreshLiveLine(CurrentSong currentSong) {
    if (this.liveWidget == null) return;
    RadioStream currentStream = this.addon.radioManager().getCurrentStream();
    this.lastLivePrefix = LiveStatusLine.buildPrefix(
        currentStream, currentSong, this.lastLiveBadgeTwitchPhase);
    if (this.lastLivePrefix == null && this.isListeningToMashup() && this.mashupShowStatus != null) {
      this.lastLivePrefix = LiveStatusLine.buildBadges(
          this.mashupShowStatus.onAir(),
          this.mashupShowStatus.twitch(),
          this.mashupShowStatus.moderatorName(),
          this.lastLiveBadgeTwitchPhase);
    }
    boolean active = this.lastLivePrefix != null;
    boolean wasActive = this.liveWidget.hasId("active");
    if (!active) {
      this.liveWidget.setComponent(Component.empty());
      this.liveWidget.removeId("active");
    } else {
      this.liveWidget.setComponent(this.lastLivePrefix);
      this.liveWidget.addId("active");
    }
    if (wasActive != active) {
      this.scheduleMenuRelayout();
    }
  }

  private boolean hasRotatingLiveLine(CurrentSong song) {
    if (LiveStatusLine.hasLiveBadges(song) && song.isOnAir() && song.isTwitch()) {
      return true;
    }
    return this.isListeningToMashup()
        && this.mashupShowStatus != null
        && this.mashupShowStatus.onAir()
        && this.mashupShowStatus.twitch();
  }

  private void updateTrack(CurrentSong currentSong) {
    if (this.trackWidget == null || this.artistWidget == null || this.streamWidget == null) return;

    boolean isPlaying = this.addon.radioManager().isPlaying();
    RadioStream currentStream = this.addon.radioManager().getCurrentStream();
    AzuraCastNowPlayingService.NowPlayingConnectionState state = this.addon.currentSongService().getConnectionState();

    this.streamWidget.setVisible(true);
    this.trackWidget.setVisible(true);
    this.setLayoutHidden(this.streamWidget, false);
    this.setLayoutHidden(this.artistWidget, false);
    this.trackWidget.removeId("idle");

    if (currentSong == null) {
      this.applyStationIcon();
      this.refreshLiveLine(null);
      if (isPlaying && currentStream != null) {
        this.streamWidget.setComponent(Component.text(stationLabel(currentStream)).color(NamedTextColor.WHITE));
        if (state == AzuraCastNowPlayingService.NowPlayingConnectionState.RECONNECTING) {
          this.trackWidget.setComponent(Component.translatable("evilradio.widget.reconnecting")
              .color(NamedTextColor.DARK_GRAY));
          this.artistWidget.setComponent(Component.translatable("evilradio.widget.reconnectingHint")
              .color(NamedTextColor.DARK_GRAY));
        } else {
          this.trackWidget.setComponent(Component.translatable("evilradio.widget.loadingSong")
              .color(NamedTextColor.DARK_GRAY));
          this.artistWidget.setComponent(Component.empty());
          this.setLayoutHidden(this.artistWidget, true);
        }
      } else {
        this.streamWidget.setComponent(Component.empty());
        this.setLayoutHidden(this.streamWidget, true);
        this.trackWidget.addId("idle");
        this.trackWidget.setComponent(Component.translatable("evilradio.widget.clickPlayToStart")
            .color(NamedTextColor.GRAY));
        this.artistWidget.setComponent(Component.empty());
        this.setLayoutHidden(this.artistWidget, true);
      }
      this.setProgressVisible(false);
      this.applyMashupLiveBanner(this.mashupShowStatus);
      return;
    }

    String streamDisplayName = stationLabel(currentStream);
    if (streamDisplayName.isBlank() && currentSong.getStationName() != null) {
      streamDisplayName = currentSong.getStationName();
    }
    this.lastLiveBadgeTwitchPhase = LiveStatusLine.showTwitchPhase(System.currentTimeMillis());
    this.streamWidget.setComponent(Component.text(streamDisplayName).color(NamedTextColor.GRAY));
    this.refreshLiveLine(currentSong);

    this.trackWidget.setComponent(Component.text(currentSong.getDisplayTitle(), NamedTextColor.WHITE));
    String artist = currentSong.getArtist() == null ? "" : currentSong.getArtist();
    this.artistWidget.setComponent(Component.text(artist, NamedTextColor.WHITE));
    this.setLayoutHidden(this.artistWidget, artist.isBlank());

    this.applyStationIcon();
    this.updateProgress(currentSong);
    this.applyMashupLiveBanner(this.mashupShowStatus);
  }

  private void updateProgress(CurrentSong song) {
    if (this.progressTrack == null || this.progressFill == null || this.statusWidget == null
        || song == null) {
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
      if (this.progressRow != null && this.progressRow.hasId("hidden")) {
        this.setProgressVisible(true);
      }
      return;
    }
    this.lastRenderedElapsed = elapsed;
    this.lastRenderedProgressPercent = percent;
    this.lastRenderedHadDuration = hasDuration;

    this.renderStatusLine(song);

    float fillWidth;
    if (hasDuration) {
      fillWidth = PROGRESS_TRACK_WIDTH * (percent / 100.0f);
      this.progressTrack.removeId("indeterminate");
    } else {
      fillWidth = PROGRESS_TRACK_WIDTH * 0.3f;
      this.progressTrack.addId("indeterminate");
    }
    this.progressFill.setVariable(PROGRESS_FILL_WIDTH_KEY, fillWidth);
    this.setProgressVisible(true);
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

  private void setProgressVisible(boolean visible) {
    if (!visible) {
      this.lastRenderedElapsed = -1L;
      this.lastRenderedProgressPercent = -1;
      this.lastRenderedHadDuration = false;
      if (this.progressFill != null) {
        this.progressFill.setVariable(PROGRESS_FILL_WIDTH_KEY, 0f);
      }
      if (this.statusWidget != null) {
        this.statusWidget.setComponent(Component.empty());
      }
    }
    if (this.progressRow != null) {
      this.setLayoutHidden(this.progressRow, !visible);
    }
    if (this.progressTrack != null && !visible) {
      this.progressTrack.removeId("indeterminate");
    }
  }

  private void applyStationIcon() {
    if (this.coverWidget == null) return;
    Icon icon = this.stationIcon();
    if (this.coverWidget.icon().get() == icon) return;
    this.coverWidget.icon().set(icon);
  }

  private void refreshPlayPauseIcon() {
    if (this.playPauseButton == null) return;
    this.playPauseButton.updateIcon(
        this.addon.radioManager().isPlaying() ? SpriteControls.STOP : SpriteControls.PLAY);
  }

  private Icon stationIcon() {
    RadioStream stream = this.addon.radioManager().getCurrentStream();
    if (stream != null && stream.getIcon() != null) {
      return stream.getIcon();
    }
    return EvilTextures.LOGO;
  }

  private void startMashupLiveUpdates() {
    this.stopMashupLiveUpdates();
    this.refreshMashupLiveBanner();
    this.mashupLiveTask = Task.builder(this::refreshMashupLiveBanner)
        .repeat(30, TimeUnit.SECONDS)
        .build();
    this.mashupLiveTask.execute();
  }

  private void stopMashupLiveUpdates() {
    if (this.mashupLiveTask != null) {
      this.mashupLiveTask.cancel();
      this.mashupLiveTask = null;
    }
  }

  private void refreshMashupLiveBanner() {
    if (this.debugForceMashupLive()) {
      this.addon.labyAPI().minecraft().executeOnRenderThread(
          () -> this.applyMashupLiveBanner(DEBUG_MASHUP_LIVE));
      return;
    }
    RadioStream mashup = this.findMashupStream();
    String radioInfoName = mashup != null && mashup.getName() != null && !mashup.getName().isBlank()
        ? mashup.getName()
        : "Mashup";
    this.addon.currentSongService().fetchLiveFlags(radioInfoName, show ->
        this.addon.labyAPI().minecraft().executeOnRenderThread(() -> this.applyMashupLiveBanner(show)));
  }

  private void applyMashupLiveBanner(ShowStatus show) {
    if (this.debugForceMashupLive()) {
      this.mashupShowStatus = DEBUG_MASHUP_LIVE;
    } else if (show != null) {
      this.mashupShowStatus = show;
    }
    if (this.mashupLiveBanner == null) return;
    int previousMode = this.lastBannerMode;
    this.lastBannerMode = this.currentBannerMode();
    boolean structureChanged = this.mashupLiveBanner.apply(
        this.lastBannerMode,
        this.showWishBoxButton(),
        this.showTwitchButton(),
        this.mashupShowStatus,
        this.upcomingShow());
    this.refreshLiveLine(this.addon.currentSongService().getCurrentSong());
    if (structureChanged || previousMode != this.lastBannerMode) {
      this.scheduleMenuRelayout();
    }
  }

  private void setLayoutHidden(Widget widget, boolean hidden) {
    if (widget == null) return;
    boolean wasHidden = widget.hasId("hidden");
    widget.setVisible(!hidden);
    if (hidden) {
      widget.addId("hidden");
    } else {
      widget.removeId("hidden");
    }
    if (wasHidden != hidden && widget != this.progressRow) {
      this.scheduleMenuRelayout();
    }
  }

  private void scheduleMenuRelayout() {
    if (this.menuRelayoutScheduled || !this.menuPlayerAttached) return;
    this.menuRelayoutScheduled = true;
    this.addon.labyAPI().minecraft().executeNextTick(() -> {
      this.menuRelayoutScheduled = false;
      if (this.menuPlayerAttached) {
        this.rebuildMenuPlayer();
      }
    });
  }

  private int currentBannerMode() {
    boolean live = this.mashupShowStatus != null
        && (this.mashupShowStatus.onAir() || this.mashupShowStatus.twitch());
    if (live && !this.isListeningToMashup()) {
      return MashupLiveBannerWidget.MODE_SWITCH;
    }
    if (live && this.hasLiveActionButtons()) {
      return MashupLiveBannerWidget.MODE_ACTIONS;
    }
    if (!live && this.upcomingShow() != null) {
      return MashupLiveBannerWidget.MODE_UPCOMING;
    }
    return MashupLiveBannerWidget.MODE_HIDDEN;
  }

  private boolean hasLiveActionButtons() {
    return this.showWishBoxButton() || this.showTwitchButton();
  }

  private boolean showTwitchButton() {
    if (this.debugForceMashupLive()) return true;
    if (this.mashupShowStatus != null && this.mashupShowStatus.twitch()) return true;
    ScheduleShow liveShow = this.addon.scheduleService().currentOnAirShow();
    return liveShow != null && liveShow.isTwitch();
  }

  private boolean showWishBoxButton() {
    if (this.debugForceMashupLive()) return true;
    ScheduleShow liveShow = this.addon.scheduleService().currentOnAirShow();
    return liveShow != null && liveShow.isGrussbox();
  }

  private static void openUrl(String url) {
    OperatingSystem.getPlatform().openUrl(url);
  }

  private ScheduleShow upcomingShow() {
    if (this.debugForceMashupLive()) return null;
    return this.addon.scheduleService().nextUpcomingShowToday();
  }

  private void applyMenuPlayerPosition(Widget widget) {
    widget.removeId("left");
    widget.removeId("right");
    widget.addId(this.isMenuPlayerLeft() ? "left" : "right");
  }

  private boolean isMenuPlayerLeft() {
    MenuPlayerPosition position = this.addon.configuration().menuPlayerPosition().get();
    return position != null && position.isLeft();
  }

  private void toggleMenuPlayerSide() {
    this.addon.configuration().menuPlayerPosition().set(
        this.isMenuPlayerLeft()
            ? MenuPlayerPosition.BOTTOM_RIGHT
            : MenuPlayerPosition.BOTTOM_LEFT);
  }

  private boolean isListeningToMashup() {
    return StationPickerController.isMashup(this.addon.radioManager().getCurrentStream());
  }

  private RadioStream findMashupStream() {
    for (RadioStream stream : this.addon.radioStreamService().streams()) {
      if (StationPickerController.isMashup(stream)) {
        return stream;
      }
    }
    return null;
  }

  private void playMashup() {
    RadioStream mashup = this.findMashupStream();
    if (!StationPickerController.isPlayable(mashup)) return;
    this.addon.radioManager().playStream(mashup);
    this.refreshPlayPauseIcon();
    this.updateTrack(this.addon.currentSongService().getCurrentSong());
  }

  private static boolean isMenuPlayerHost(String identifier) {
    return MAIN_MENU_ACTIVITY_ID.equals(identifier)
        || MULTIPLAYER_ACTIVITY_ID.equals(identifier);
  }

  private static String stationLabel(RadioStream stream) {
    if (stream == null) return "";
    String name = stream.getDisplayName() != null && !stream.getDisplayName().isBlank()
        ? stream.getDisplayName()
        : stream.getName();
    if (name == null || name.isBlank()) return "";
    return "Evil-Radio - " + name;
  }

}
