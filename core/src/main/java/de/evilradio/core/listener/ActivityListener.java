package de.evilradio.core.listener;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.EvilTextures;
import de.evilradio.core.EvilTextures.SpriteControls;
import de.evilradio.core.activity.picker.StationPickerController;
import de.evilradio.core.activity.widget.MashupLiveBannerWidget;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import de.evilradio.core.hudwidget.widget.LiveStatusLine;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.schedule.ScheduleShow;
import de.evilradio.core.song.CurrentSong;
import de.evilradio.core.song.CurrentSongService.ShowStatus;
import de.evilradio.core.song.NowPlayingConnectionState;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.models.OperatingSystem;
import net.labymod.api.client.gui.hud.hudwidget.HudWidget.Updatable;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.activity.Activity;
import net.labymod.api.client.gui.screen.widget.attributes.PriorityLayer;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.activity.Document;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.HorizontalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.gui.screen.ActivityInitializeEvent;
import net.labymod.api.event.client.lifecycle.GameTickEvent;
import net.labymod.api.util.concurrent.task.Task;

public class ActivityListener implements Updatable {

  private EvilRadioAddon addon;

  public ActivityListener(EvilRadioAddon addon) {
    this.addon = addon;
    this.addon.configuration().showMainMenuPlayer().addChangeListener(enabled ->
        this.addon.labyAPI().minecraft().executeOnRenderThread(
            () -> this.applyMainMenuPlayerVisibility(enabled)));
  }

  @Subscribe
  public void onActivityInitialize(ActivityInitializeEvent event) {
    if (!isMenuPlayerHost(event.getIdentifier())) {
      return;
    }
    this.mainMenuActivity = event.activity();
    this.detachMenuPlayerWidgets();
    if (!this.addon.configuration().showMainMenuPlayer().get()) {
      return;
    }
    this.addRadioController(event.activity());
  }

  @Subscribe
  public void onGameTick(GameTickEvent event) {

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

    if (this.mashupLiveBanner != null) {
      this.applyMashupLiveBanner(this.mashupShowStatus);
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
  private static final String GRUSSBOX_URL = "https://evil-radio.de/sendeplan/music-request.php";
  private static final String TWITCH_URL = "https://www.twitch.tv/evilradiode";

  private Component lastLivePrefix = null;
  private boolean lastLiveBadgeTwitchPhase;
  private long lastRenderedElapsed = -1L;
  private int lastRenderedProgressPercent = -1;
  private boolean lastRenderedHadDuration;
  private ShowStatus mashupShowStatus;
  private Task mashupLiveTask;
  private boolean debugForceMashupLive;
  private int lastBannerMode;

  private static final ShowStatus DEBUG_MASHUP_LIVE =
      new ShowStatus(true, true, "Debug-DJ", 0L, 0L, "20:00", "22:00");

  public void toggleDebugMashupLiveBanner() {
    this.debugForceMashupLive = !this.debugForceMashupLive;
    this.lastBannerMode = -1;
    this.addon.labyAPI().minecraft().executeOnRenderThread(() -> {
      if (this.debugForceMashupLive) {
        this.applyMashupLiveBanner(DEBUG_MASHUP_LIVE);
        this.addon.notification(
            Component.translatable("evilradio.settings.previewMashupLiveBanner.name"),
            Component.translatable("evilradio.settings.previewMashupLiveBanner.enabledHint"));
      } else {
        this.refreshMashupLiveBanner();
        this.addon.notification(
            Component.translatable("evilradio.settings.previewMashupLiveBanner.name"),
            Component.translatable("evilradio.settings.previewMashupLiveBanner.disabledHint"));
      }
    });
  }

  private void applyMainMenuPlayerVisibility(boolean enabled) {
    if (this.mainMenuActivity == null) {
      return;
    }
    if (enabled) {
      if (this.songContainer == null) {
        this.addRadioController(this.mainMenuActivity);
      } else {
      this.songContainer.setVisible(true);
      this.startMashupLiveUpdates();
      }
      return;
    }
    if (this.songContainer != null) {
      this.songContainer.setVisible(false);
    }
    this.stopMashupLiveUpdates();
  }

  private void clearMainMenuPlayerWidgets() {
    this.stopMashupLiveUpdates();
    this.detachMenuPlayerWidgets();
    this.mashupShowStatus = null;
    this.lastBannerMode = 0;
    this.lastRenderedElapsed = -1L;
    this.lastRenderedProgressPercent = -1;
    this.lastRenderedHadDuration = false;
  }

  private void detachMenuPlayerWidgets() {
    this.songContainer = null;
    this.songContent = null;
    this.mashupLiveBanner = null;
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

    this.songContainer = new FlexibleContentWidget().addId("song-container");
    this.songContainer.priorityLayer().set(PriorityLayer.VERY_FRONT);

    this.mashupLiveBanner = new MashupLiveBannerWidget();
    this.mashupLiveBanner.addId("mashup-live-banner");
    this.mashupLiveBanner.priorityLayer().set(PriorityLayer.VERY_FRONT);
    this.mashupLiveBanner.bind(this::playMashup, () -> openUrl(GRUSSBOX_URL), () -> openUrl(TWITCH_URL));
    this.lastBannerMode = this.currentBannerMode();
    this.mashupLiveBanner.apply(
        this.lastBannerMode,
        this.showGrussboxButton(),
        this.showTwitchButton(),
        this.mashupShowStatus);

    this.songContent = new FlexibleContentWidget().addId("content");

    this.coverWidget = new IconWidget(this.stationIcon());
    this.coverWidget.addId("cover");
    this.songContent.addContent(this.coverWidget);

    FlexibleContentWidget player = new FlexibleContentWidget().addId("player");

    this.streamWidget = ComponentWidget.empty();
    this.streamWidget.addId("stream-name");
    player.addContent(this.streamWidget);

    this.liveWidget = ComponentWidget.empty();
    this.liveWidget.addId("live-status");
    player.addContent(this.liveWidget);

    this.trackWidget = ComponentWidget.empty();
    this.trackWidget.addId("track");
    player.addContent(this.trackWidget);

    this.artistWidget = ComponentWidget.empty();
    this.artistWidget.addId("artist");
    player.addContent(this.artistWidget);

    this.progressRow = new FlexibleContentWidget().addId("progress-row");
    this.progressTrack = new DivWidget().addId("progress-track");
    this.progressFill = new DivWidget().addId("progress-fill");
    this.progressFill.setVariable(PROGRESS_FILL_WIDTH_KEY, 0f);
    this.progressTrack.addChild(this.progressFill);
    this.progressRow.addContent(this.progressTrack);
    this.statusWidget = ComponentWidget.empty().addId("status");
    this.progressRow.addContent(this.statusWidget);
    this.progressRow.setVisible(false);
    player.addContent(this.progressRow);

    HorizontalListWidget controlsContainer = new HorizontalListWidget().addId("controls");

    ButtonWidget previousButton = ButtonWidget.icon(SpriteControls.PREVIOUS).addId("previous");
    previousButton.setPressable(() -> {
      this.switchStream(-1);
    });
    controlsContainer.addEntry(previousButton);

    this.playPauseButton = ButtonWidget.icon(this.addon.radioManager().isPlaying() ? SpriteControls.PAUSE : SpriteControls.PLAY).addId("play-pause");
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

    document.addChildInitialized(this.songContainer);

    this.refreshPlayPauseIcon();
    this.updateTrack(this.addon.currentSongService().getCurrentSong());
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

    this.playPauseButton.updateIcon(SpriteControls.PAUSE);
    this.addon.radioManager().playStream(nextPlayable);
    this.refreshPlayPauseIcon();
    this.updateTrack(this.addon.currentSongService().getCurrentSong());
  }

  private void togglePlayback() {
    boolean startPlayback = !this.addon.radioManager().isPlaying();
    this.playPauseButton.updateIcon(startPlayback ? SpriteControls.PAUSE : SpriteControls.PLAY);

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
    if (this.liveWidget == null) {
      return;
    }
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
    if (this.lastLivePrefix == null) {
      this.liveWidget.setComponent(Component.empty());
      this.liveWidget.removeId("active");
      return;
    }
    this.liveWidget.setComponent(this.lastLivePrefix);
    this.liveWidget.addId("active");
  }

  private boolean hasRotatingLiveLine(CurrentSong song) {
    if (song != null && LiveStatusLine.hasLiveBadges(song) && song.isOnAir() && song.isTwitch()) {
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
    NowPlayingConnectionState state = this.addon.currentSongService().getConnectionState();

    this.streamWidget.setVisible(true);
    this.trackWidget.setVisible(true);
    this.artistWidget.setVisible(true);
    this.trackWidget.removeId("idle");

    if (currentSong == null) {
      this.applyStationIcon();
      this.refreshLiveLine(null);
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
          this.artistWidget.setVisible(false);
        }
      } else {
        this.streamWidget.setComponent(Component.empty());
        this.streamWidget.setVisible(false);
        this.trackWidget.addId("idle");
        this.trackWidget.setComponent(Component.translatable("evilradio.widget.clickPlayToStart")
            .color(NamedTextColor.GRAY));
        this.artistWidget.setComponent(Component.empty());
        this.artistWidget.setVisible(false);
      }
      this.setProgressVisible(false);
      this.applyMashupLiveBanner(this.mashupShowStatus);
      return;
    }

    String streamDisplayName = stationLabel(currentStream, false);
    if (streamDisplayName.isBlank() && currentSong.getStationName() != null) {
      streamDisplayName = currentSong.getStationName();
    }
    this.lastLiveBadgeTwitchPhase = LiveStatusLine.showTwitchPhase(System.currentTimeMillis());
    this.streamWidget.setComponent(Component.text(streamDisplayName).color(NamedTextColor.GRAY));
    this.refreshLiveLine(currentSong);

    this.trackWidget.setComponent(Component.text(currentSong.getDisplayTitle(), NamedTextColor.WHITE));
    String artist = currentSong.getArtist() == null ? "" : currentSong.getArtist();
    this.artistWidget.setComponent(Component.text(artist, NamedTextColor.WHITE));
    this.artistWidget.setVisible(!artist.isBlank());

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
      this.progressRow.setVisible(visible);
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
        this.addon.radioManager().isPlaying() ? SpriteControls.PAUSE : SpriteControls.PLAY);
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
    if (this.debugForceMashupLive) {
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
    if (this.debugForceMashupLive) {
      this.mashupShowStatus = DEBUG_MASHUP_LIVE;
    } else if (show != null) {
      this.mashupShowStatus = show;
    }
    if (this.mashupLiveBanner == null) {
      return;
    }
    this.lastBannerMode = this.currentBannerMode();
    this.mashupLiveBanner.apply(
        this.lastBannerMode,
        this.showGrussboxButton(),
        this.showTwitchButton(),
        this.mashupShowStatus);
    this.refreshLiveLine(this.addon.currentSongService().getCurrentSong());
  }

  private int currentBannerMode() {
    boolean live = this.mashupShowStatus != null
        && (this.mashupShowStatus.onAir() || this.mashupShowStatus.twitch());
    boolean listening = this.isListeningToMashup();
    if (live && !listening) {
      return MashupLiveBannerWidget.MODE_SWITCH;
    }
    if (live && listening && this.hasLiveActionButtons()) {
      return MashupLiveBannerWidget.MODE_ACTIONS;
    }
    return MashupLiveBannerWidget.MODE_HIDDEN;
  }

  private boolean hasLiveActionButtons() {
    return this.showGrussboxButton() || this.showTwitchButton();
  }

  private boolean showTwitchButton() {
    if (this.debugForceMashupLive) {
      return true;
    }
    if (this.mashupShowStatus != null && this.mashupShowStatus.twitch()) {
      return true;
    }
    ScheduleShow liveShow = this.addon.scheduleService().currentOnAirShow();
    return liveShow != null && liveShow.isTwitch();
  }

  private boolean showGrussboxButton() {
    if (this.debugForceMashupLive) {
      return true;
    }
    ScheduleShow liveShow = this.addon.scheduleService().currentOnAirShow();
    return liveShow != null && liveShow.isGrussbox();
  }

  private static void openUrl(String url) {
    OperatingSystem.getPlatform().openUrl(url);
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
    if (!StationPickerController.isPlayable(mashup)) {
      return;
    }
    this.addon.radioManager().playStream(mashup);
    this.refreshPlayPauseIcon();
    this.updateTrack(this.addon.currentSongService().getCurrentSong());
  }

  private static boolean isMenuPlayerHost(String identifier) {
    return MAIN_MENU_ACTIVITY_ID.equals(identifier)
        || MULTIPLAYER_ACTIVITY_ID.equals(identifier);
  }

  private static String stationLabel(RadioStream stream, boolean compact) {
    if (stream == null) return "";
    String name = stream.getDisplayName() != null && !stream.getDisplayName().isBlank()
        ? stream.getDisplayName()
        : stream.getName();
    if (name == null || name.isBlank()) return "";
    return compact ? name : "EvilRadio - " + name;
  }

}
