package de.evilradio.core.activity.picker;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.EvilTextures;
import de.evilradio.core.EvilTextures.SpriteCommon;
import de.evilradio.core.EvilTextures.SpriteControls;
import de.evilradio.core.activity.picker.widget.RadioStationRowWidget;
import de.evilradio.core.activity.picker.widget.ScheduleShowRowWidget;
import de.evilradio.core.configuration.EvilRadioConfiguration;
import de.evilradio.core.configuration.StationPickerSubSettings;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import de.evilradio.core.radio.AudioEqualizer;
import de.evilradio.core.radio.AudioSpectrumAnalyzer;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.schedule.ScheduleService;
import de.evilradio.core.song.CurrentSong;
import de.evilradio.core.song.CurrentSongService;
import de.evilradio.core.song.azuracast.PickerNowPlayingSession;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.TextColor;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.mouse.MutableMouse;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.AutoActivity;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.activity.types.SimpleActivity;
import net.labymod.api.client.gui.screen.key.InputType;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.key.MouseButton;
import net.labymod.api.client.gui.screen.widget.Widget;
import net.labymod.api.client.gui.screen.widget.attributes.bounds.BoundsType;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.ScrollWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.HorizontalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.util.I18n;
import net.labymod.api.util.concurrent.task.Task;
import org.jetbrains.annotations.Nullable;

/**
 * Eigenständige Sender-Auswahl als echter Minecraft-Screen (Maus frei).
 */
@Link("activity/radio-station-list.lss")
@AutoActivity
public class RadioStationListActivity extends SimpleActivity {

  private enum PickerTab {
    STATIONS,
    SCHEDULE
  }

  private static final String OPEN_ANIMATION_ID = "picker-open";
  private static final String PICKER_PANEL_BG_VAR = "--picker-panel-bg";
  private static final String PICKER_PANEL_BORDER_VAR = "--picker-panel-border";
  private static final String PICKER_PANEL_BLUR_VAR = "--picker-panel-blur";
  private static final String PICKER_ROW_BG_VAR = "--picker-row-bg";
  private static final String EQ_BAR_HEIGHT_VAR = "--eq-bar-height";
  private static final String EQ_BAR_BOTTOM_VAR = "--eq-bar-bottom";
  private static final String EQ_BAR_LEFT_VAR = "--eq-bar-left";
  private static final String EQ_PEAK_BOTTOM_VAR = "--eq-peak-bottom";
  private static final int EQ_BAR_COUNT = AudioSpectrumAnalyzer.BAND_COUNT;
  private static final float EQ_BAR_MIN_HEIGHT = 2.0F;
  private static final float EQ_BAR_MAX_HEIGHT = 24.0F;
  private static final float EQ_BAR_WIDTH = 3.0F;
  private static final float EQ_PEAK_DECAY = 0.012F;
  private static final float EQ_PEAK_MARKER = 2.0F;
  private static final DateTimeFormatter CHIP_DAY_FORMAT = DateTimeFormatter.ofPattern("dd.MM");

  private final EvilRadioAddon addon;
  private final StationPickerController controller;

  private final List<RadioStationRowWidget> rows = new ArrayList<>();
  private final List<RadioStream> displayStreams = new ArrayList<>();
  private final Map<String, CurrentSong> songByShortcode = new ConcurrentHashMap<>();
  private final List<ButtonWidget> scheduleDayChipButtons = new ArrayList<>();

  private PickerTab activeTab = PickerTab.STATIONS;
  private int selectedScheduleDayIndex;

  private VerticalListWidget<Widget> panelWidget;
  private DivWidget nowPlayingStrip;
  private ScrollWidget stationScroll;
  private HorizontalListWidget scheduleDayChips;
  private ScrollWidget scheduleScroll;
  private ButtonWidget stationsTabButton;
  private ButtonWidget scheduleTabButton;
  private SliderWidget volumeSlider;
  private ButtonWidget playPauseButton;
  private ButtonWidget equalizerStyleButton;
  private IconWidget coverWidget;
  private ComponentWidget coverSongWidget;
  private ComponentWidget coverArtistWidget;
  private ComponentWidget coverStationWidget;
  private ComponentWidget coverTimeWidget;
  private DivWidget coverProgressTrack;
  private DivWidget coverProgressFill;
  private DivWidget equalizerWidget;
  private final List<DivWidget> equalizerBars = new ArrayList<>();
  private final List<DivWidget> equalizerPeaks = new ArrayList<>();
  private final float[] spectrumBands = new float[EQ_BAR_COUNT];
  private final float[] waveformSamples = new float[EQ_BAR_COUNT];
  private final float[] peakHolds = new float[EQ_BAR_COUNT];
  private @Nullable EvilRadioConfiguration.EqualizerStyle appliedEqualizerStyle;
  private @Nullable String appliedCoverKey;
  private long lastNowPlayingElapsed = -1L;
  private boolean lastNowPlayingHadDuration;
  private float equalizerLayoutWidth = -1.0F;
  private static final String COVER_PROGRESS_WIDTH_KEY = "--picker-progress-width";
  private static final String STATION_SCROLL_HEIGHT_VAR = "--station-scroll-height";
  private static final String SCHEDULE_SCROLL_HEIGHT_VAR = "--schedule-scroll-height";
  private static final float STATION_SCROLL_MAX_HEIGHT = 250.0F;
  private static final float STATION_SCROLL_MIN_HEIGHT = 120.0F;
  private static final float SCHEDULE_SCROLL_MAX_HEIGHT = 280.0F;
  private static final float SCHEDULE_SCROLL_MIN_HEIGHT = 120.0F;
  /**
   * Padding + Header + Tabs + Now Playing + VerticalList-Gaps (space-between 7).
   * Siehe radio-station-list.lss (.picker-panel / .picker-header / .picker-tabs / .picker-now-playing).
   */
  private static final float STATIONS_CHROME_HEIGHT = 163.0F;
  /**
   * Padding + Header + Tabs + Day-Chips + Gaps (inkl. schedule-panel space-between 8).
   */
  private static final float SCHEDULE_CHROME_HEIGHT = 128.0F;
  private static final float SCREEN_HEIGHT_USAGE = 0.92F;
  private static final float COVER_PROGRESS_TRACK_WIDTH = 140f;
  private static final float COVER_PROGRESS_TRACK_WIDTH_NO_EQ = 170f;

  private int selectedIndex;
  private boolean keyboardFocus;
  private boolean mashupOnAir;
  private boolean mashupTwitch;
  private @Nullable CurrentSongService.ShowStatus mashupShowStatus;
  private Task mashupTask;
  private Task nowPlayingSnapshotTask;
  private long lastEndedSongRefreshAt;
  private @Nullable PickerNowPlayingSession nowPlayingSession;
  private final Runnable streamsChangedListener = this::onStreamsChanged;

  public RadioStationListActivity(EvilRadioAddon addon) {
    this.addon = addon;
    this.controller = new StationPickerController(addon);
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);
    this.document.getChildren().clear();
    this.rows.clear();
    this.scheduleDayChipButtons.clear();
    this.equalizerBars.clear();
    this.equalizerPeaks.clear();
    this.appliedEqualizerStyle = null;
    this.equalizerLayoutWidth = -1.0F;
    this.keyboardFocus = false;
    this.lastNowPlayingElapsed = -1L;
    this.lastNowPlayingHadDuration = false;

    RadioStream previouslySelected = null;
    if (!this.displayStreams.isEmpty()
        && this.selectedIndex >= 0
        && this.selectedIndex < this.displayStreams.size()) {
      previouslySelected = this.displayStreams.get(this.selectedIndex);
    }
    this.displayStreams.clear();
    this.displayStreams.addAll(this.buildDisplayStreams());
    if (previouslySelected != null) {
      int kept = this.indexOfStream(previouslySelected);
      if (kept >= 0) {
        this.selectedIndex = kept;
      }
    }
    if (this.selectedIndex >= this.displayStreams.size()) {
      this.selectedIndex = Math.max(0, this.displayStreams.size() - 1);
    }

    VerticalListWidget<Widget> panel = new VerticalListWidget<>().addId("picker-panel");
    panel.animationDuration().set(180);
    this.panelWidget = panel;
    this.applyPanelAppearance();

    DivWidget header = new DivWidget().addId("picker-header");
    header.addChild(new IconWidget(EvilTextures.LOGO).addId("picker-logo"));
    Component title;
    if (this.activeTab == PickerTab.SCHEDULE) {
      title = Component.translatable("evilradio.picker.tab.schedule").color(NamedTextColor.RED);
    } else if (this.displayStreams.isEmpty()) {
      title = Component.translatable("evilradio.picker.noStationsAvailable").color(NamedTextColor.DARK_RED);
    } else {
      title = Component.translatable("evilradio.picker.selectStation").color(NamedTextColor.RED);
    }
    header.addChild(ComponentWidget.component(title).addId("picker-title"));
    header.addChild(ButtonWidget.icon(SpriteCommon.X, this::displayPreviousScreen).addId("picker-close-x"));
    panel.addChild(header);

    HorizontalListWidget tabs = new HorizontalListWidget().addId("picker-tabs");
    this.stationsTabButton = ButtonWidget.text("Sender", () -> this.switchTab(PickerTab.STATIONS))
        .addId("picker-tab")
        .addId("picker-tab-stations");
    this.scheduleTabButton = ButtonWidget.text("Sendeplan", () -> this.switchTab(PickerTab.SCHEDULE))
        .addId("picker-tab")
        .addId("picker-tab-schedule");
    this.stationsTabButton.updateComponent(
        Component.translatable("evilradio.picker.tab.stations"));
    this.scheduleTabButton.updateComponent(
        Component.translatable("evilradio.picker.tab.schedule"));
    if (this.activeTab == PickerTab.STATIONS) {
      this.stationsTabButton.addId("active");
    } else {
      this.scheduleTabButton.addId("active");
    }

    tabs.addEntry(this.stationsTabButton);
    tabs.addEntry(this.scheduleTabButton);
    panel.addChild(tabs);

    // Reset tab-spezifische Referenzen
    this.nowPlayingStrip = null;
    this.stationScroll = null;
    this.volumeSlider = null;
    this.playPauseButton = null;
    this.equalizerStyleButton = null;
    this.coverWidget = null;
    this.coverSongWidget = null;
    this.coverArtistWidget = null;
    this.coverStationWidget = null;
    this.coverTimeWidget = null;
    this.coverProgressTrack = null;
    this.coverProgressFill = null;
    this.equalizerWidget = null;
    this.scheduleDayChips = null;
    this.scheduleScroll = null;

    if (this.activeTab == PickerTab.STATIONS) {
      this.buildStationsContent(panel);
    } else {
      this.buildScheduleContent(panel);
      panel.addId("schedule-tab");
    }

    this.document.addChild(panel);
    this.appliedCoverKey = null;
    this.applyAdaptiveScrollHeights();
    if (this.activeTab == PickerTab.STATIONS) {
      this.refreshNowPlaying();
    } else if (this.addon.scheduleService().days().isEmpty()) {
      // Nur nachladen wenn Cache leer – sonst Reload-Schleife (refresh→reload→refresh…)
      this.addon.scheduleService().refreshAsync(() -> {
        if (this.activeTab == PickerTab.SCHEDULE
            && !this.addon.scheduleService().days().isEmpty()) {
          this.reload();
        }
      });
    }
  }

  /**
   * Kürzt die Listen-Scrollhöhe, wenn das Panel sonst größer als der Screen wäre
   * (z. B. 1080p + GUI Scale 3 ≈ 360 GUI-Einheiten).
   */
  private void applyAdaptiveScrollHeights() {
    float screenHeight = this.bounds().getHeight();
    if (screenHeight <= 1.0F) {
      this.addon.labyAPI().minecraft().executeNextTick(this::applyAdaptiveScrollHeights);
      return;
    }

    float maxPanelHeight = screenHeight * SCREEN_HEIGHT_USAGE;
    if (this.stationScroll != null) {
      float height = clamp(
          maxPanelHeight - STATIONS_CHROME_HEIGHT,
          STATION_SCROLL_MIN_HEIGHT,
          STATION_SCROLL_MAX_HEIGHT
      );
      this.stationScroll.setVariable(STATION_SCROLL_HEIGHT_VAR, height);
    }
    if (this.scheduleScroll != null) {
      float height = clamp(
          maxPanelHeight - SCHEDULE_CHROME_HEIGHT,
          SCHEDULE_SCROLL_MIN_HEIGHT,
          SCHEDULE_SCROLL_MAX_HEIGHT
      );
      this.scheduleScroll.setVariable(SCHEDULE_SCROLL_HEIGHT_VAR, height);
    }
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  private void buildStationsContent(VerticalListWidget<Widget> panel) {
    DivWidget coverStrip = new DivWidget().addId("picker-now-playing");
    this.nowPlayingStrip = coverStrip;

    DivWidget equalizer = new DivWidget().addId("picker-eq");
    this.equalizerWidget = equalizer;
    equalizer.setVisible(false);
    for (int i = 0; i < EQ_BAR_COUNT; i++) {
      DivWidget bar = new DivWidget().addId("picker-eq-bar");
      DivWidget peak = new DivWidget().addId("picker-eq-peak");
      bar.setVariable(EQ_BAR_HEIGHT_VAR, EQ_BAR_MIN_HEIGHT);
      bar.setVariable(EQ_BAR_BOTTOM_VAR, 0.0F);
      bar.setVariable(EQ_BAR_LEFT_VAR, 0.0F);
      peak.setVariable(EQ_PEAK_BOTTOM_VAR, 0.0F);
      peak.setVariable(EQ_BAR_LEFT_VAR, 0.0F);
      equalizer.addChild(bar);
      equalizer.addChild(peak);
      this.equalizerBars.add(bar);
      this.equalizerPeaks.add(peak);
      this.peakHolds[i] = 0.0F;
    }
    this.applyEqualizerStyle(this.addon.configuration().equalizerStyle().get());
    coverStrip.addChild(equalizer);

    this.coverWidget = new IconWidget(EvilTextures.LOGO).addId("picker-cover");
    coverStrip.addChild(this.coverWidget);
    VerticalListWidget<Widget> coverTexts = new VerticalListWidget<>().addId("picker-cover-texts");
    this.coverStationWidget = ComponentWidget.empty().addId("picker-cover-station");
    this.coverSongWidget = ComponentWidget.empty().addId("picker-cover-song");
    this.coverArtistWidget = ComponentWidget.empty().addId("picker-cover-artist");
    coverTexts.addChild(this.coverStationWidget);
    coverTexts.addChild(this.coverSongWidget);
    coverTexts.addChild(this.coverArtistWidget);
    coverStrip.addChild(coverTexts);
    this.coverProgressTrack = new DivWidget().addId("picker-cover-progress-track");
    this.coverProgressFill = new DivWidget().addId("picker-cover-progress-fill");
    this.coverProgressFill.setVariable(COVER_PROGRESS_WIDTH_KEY, 0f);
    this.coverProgressTrack.addChild(this.coverProgressFill);
    this.coverProgressTrack.setVisible(false);
    coverStrip.addChild(this.coverProgressTrack);
    this.coverTimeWidget = ComponentWidget.empty().addId("picker-cover-time");
    this.coverTimeWidget.setVisible(false);
    coverStrip.addChild(this.coverTimeWidget);
    boolean equalizerEnabled = this.isEqualizerFeatureEnabled();
    if (equalizerEnabled) {
      this.equalizerStyleButton = ButtonWidget.icon(SpriteCommon.EQ_ICON, this::cycleEqualizerStyle)
          .addId("picker-eq-toggle");
      coverStrip.addChild(this.equalizerStyleButton);
    } else {
      this.equalizerStyleButton = null;
      coverStrip.addId("no-eq");
    }
    this.playPauseButton = ButtonWidget.icon(
        this.playPauseIcon(),
        this::togglePlayPause
    ).addId("picker-play-pause");
    coverStrip.addChild(this.playPauseButton);

    HorizontalListWidget eqVolumeContainer = new HorizontalListWidget().addId("picker-audio-eq-volume-container");

    VerticalListWidget<Widget> audioEqualizerPresetDropdownContainer = new VerticalListWidget<>().addId("picker-audio-eq-preset-container");

    ComponentWidget audioEqualizerTitle = ComponentWidget.i18n("evilradio.settings.audioEqualizer.name").addId("picker-audio-eq-preset-title");
    audioEqualizerPresetDropdownContainer.addChild(audioEqualizerTitle);

    HorizontalListWidget eqSettingsDropdownContainer = new HorizontalListWidget().addId("picker-audio-eq-settings-container");

    ButtonWidget eqSettingsWidget = ButtonWidget.icon(SpriteCommon.SETTINGS).addId("picker-audio-eq-settings");
    eqSettingsWidget.setPressable(() -> {
      this.addon.labyAPI().coreSettingRegistry().findSetting((CharSequence) (this.addon.labyAPI().getNamespace(this.addon))).ifPresent(setting -> {
        this.addon.labyAPI().showSetting(setting);
      });
    });

    eqSettingsDropdownContainer.addEntry(eqSettingsWidget);

    DropdownWidget<AudioEqualizer.EqualizerPreset> audioEqualizerPresetDropdown = new DropdownWidget<>().addId("picker-audio-eq-preset");
    audioEqualizerPresetDropdown.addAll(AudioEqualizer.EqualizerPreset.values());
    audioEqualizerPresetDropdown.setSelected(this.addon.configuration().audioEqualizer().preset().get());
    audioEqualizerPresetDropdown.setTranslationKeyPrefix("evilradio.settings.audioEqualizer.preset.type");
    audioEqualizerPresetDropdown.setChangeListener(equalizerPreset -> {
      this.addon.configuration().audioEqualizer().preset().set(equalizerPreset);
      this.addon.applyAudioEqualizerConfiguration();
    });
    eqSettingsDropdownContainer.addEntry(audioEqualizerPresetDropdown);

    audioEqualizerPresetDropdownContainer.addChild(eqSettingsDropdownContainer);

    eqVolumeContainer.addEntry(audioEqualizerPresetDropdownContainer);

    this.syncEqualizerStyleButton();

    this.volumeSlider = new SliderWidget(1.0F, this::onVolumeSliderChanged)
        .range(0.0F, 100.0F)
        .withFormatter(value -> Component.text(Math.round(value) + "%"))
        .addId("picker-volume-slider");
    this.volumeSlider.setValue(this.addon.configuration().volume().get(), false);
    eqVolumeContainer.addEntry(this.volumeSlider);

    coverStrip.addChild(eqVolumeContainer);
    panel.addChild(coverStrip);

    VerticalListWidget<RadioStationRowWidget> list =
        new VerticalListWidget<RadioStationRowWidget>().addId("station-list");
    for (int i = 0; i < this.displayStreams.size(); i++) {
      RadioStream stream = this.displayStreams.get(i);
      boolean playing = this.isPlaying(stream);
      RadioStationRowWidget row = new RadioStationRowWidget(stream, playing);
      this.applyRowAppearance(row);
      row.setFocusedRow(false);
      if (StationPickerController.isMashup(stream)) {
        row.updateOnAirAndTwitchStatus(this.mashupOnAir, this.mashupTwitch);
      }
      String shortcode = this.shortcodeOf(stream);
      if (shortcode != null && this.songByShortcode.containsKey(shortcode)) {
        row.setSong(this.songByShortcode.get(shortcode));
      } else if (playing) {
        CurrentSong live = this.addon.currentSongService().getCurrentSong();
        if (live != null && live.isValid()) {
          row.setSong(live);
        }
      }
      final int index = i;
      if (!playing) {
        row.setPressable(() -> this.playAndClose(index));
      } else {
        row.setPressable(null);
      }
      list.addChild(row);
      this.rows.add(row);
    }

    this.stationScroll = new ScrollWidget(list).addId("station-scroll");
    this.stationScroll.setVariable(STATION_SCROLL_HEIGHT_VAR, STATION_SCROLL_MAX_HEIGHT);
    panel.addChild(this.stationScroll);
  }

  private void buildScheduleContent(VerticalListWidget<Widget> panel) {
    // Live-Status für Grußbox (Mashup on air)
    boolean mashupLive = this.mashupOnAir;
    CurrentSong liveSong = this.addon.currentSongService().getCurrentSong();
    if (liveSong != null && liveSong.isOnAir()) {
      mashupLive = true;
    }

    List<ScheduleService.ScheduleDay> days = this.addon.scheduleService().days();
    if (this.selectedScheduleDayIndex >= days.size()) {
      this.selectedScheduleDayIndex = 0;
    }

    this.scheduleDayChipButtons.clear();
    this.scheduleDayChips = new HorizontalListWidget().addId("schedule-day-chips");

    int dayCount = days.size();
    for (int i = 0; i < dayCount; i++) {
      ScheduleService.ScheduleDay day = days.get(i);
      final int dayIndex = i;
      ButtonWidget chip = ButtonWidget.text(
              this.dayChipLabel(day),
              () -> this.selectScheduleDay(dayIndex))
          .addId("schedule-day-chip");
      if (i == this.selectedScheduleDayIndex) {
        chip.addId("active");
      }
      this.scheduleDayChips.addEntry(chip);
      this.scheduleDayChipButtons.add(chip);
    }
    panel.addChild(this.scheduleDayChips);

    VerticalListWidget<Widget> scheduleList = new VerticalListWidget<>().addId("schedule-list");
    if (days.isEmpty()) {
      DivWidget loadingBox = new DivWidget().addId("schedule-empty-box");
      loadingBox.addChild(ComponentWidget.component(
              Component.translatable("evilradio.schedule.loading").color(NamedTextColor.GRAY))
          .addId("schedule-empty"));
      scheduleList.addChild(loadingBox);
    } else {
      ScheduleService.ScheduleDay selected = days.get(this.selectedScheduleDayIndex);
      List<ScheduleService.ScheduleShow> shows = selected.shows();
      scheduleList.addChild(ComponentWidget.component(
              Component.text(this.dayHeaderLabel(selected)).color(NamedTextColor.WHITE))
          .addId("schedule-day-header"));
      if (shows.isEmpty()) {
        DivWidget emptyBox = new DivWidget().addId("schedule-empty-box");
        emptyBox.addChild(ComponentWidget.component(
                Component.translatable("evilradio.schedule.empty").color(NamedTextColor.GRAY))
            .addId("schedule-empty"));
        scheduleList.addChild(emptyBox);
      } else {
        for (ScheduleService.ScheduleShow show : shows) {
          scheduleList.addChild(new ScheduleShowRowWidget(show, mashupLive));
        }
      }
    }

    this.scheduleScroll = new ScrollWidget(scheduleList).addId("schedule-scroll");
    this.scheduleScroll.setVariable(SCHEDULE_SCROLL_HEIGHT_VAR, SCHEDULE_SCROLL_MAX_HEIGHT);
    panel.addChild(this.scheduleScroll);
  }

  @Override
  public void onOpenScreen() {
    super.onOpenScreen();
    this.addon.radioStreamService().addChangeListener(this.streamsChangedListener);
    this.startMashupUpdates();
    this.startNowPlayingSession();
    if (this.panelWidget != null) {
      this.panelWidget.playAnimation(OPEN_ANIMATION_ID);
    }
  }

  @Override
  public void onCloseScreen() {
    this.addon.radioStreamService().removeChangeListener(this.streamsChangedListener);
    this.stopMashupUpdates();
    this.stopNowPlayingSession();
    super.onCloseScreen();
  }

  private void onStreamsChanged() {
    this.songByShortcode.clear();
    this.stopNowPlayingSession();
    this.reload();
    this.startNowPlayingSession();
  }

  private void switchTab(PickerTab tab) {
    if (tab == null || tab == this.activeTab) {
      return;
    }
    this.activeTab = tab;
    // Reload erst im nächsten Tick – sonst verschluckt der Klick-Handler den Rebuild.
    this.addon.labyAPI().minecraft().executeNextTick(this::reload);
  }

  private void setActiveTab(PickerTab tab) {
    this.switchTab(tab);
  }

  private void selectScheduleDay(int dayIndex) {
    List<ScheduleService.ScheduleDay> days = this.addon.scheduleService().days();
    if (dayIndex < 0 || dayIndex >= days.size() || dayIndex == this.selectedScheduleDayIndex) {
      return;
    }
    this.selectedScheduleDayIndex = dayIndex;
    this.reload();
  }

  private String dayChipLabel(ScheduleService.ScheduleDay day) {
    LocalDate date = this.parseScheduleDate(day.date());
    String weekdayApi = day.weekday() == null ? "" : day.weekday().trim();
    if (date == null) {
      String weekday = weekdayApi.isEmpty() ? "?" : weekdayApi;
      return weekday.length() <= 5 ? weekday : weekday.substring(0, 5);
    }
    // Zeile 1: Wochentag, Zeile 2: dd.mm
    String line1;
    if (weekdayApi.equalsIgnoreCase("Heute") || weekdayApi.equalsIgnoreCase("Today")) {
      line1 = "Heute";
    } else {
      line1 = this.chipDayLabel(date);
    }
    return line1 + "\n" + date.format(CHIP_DAY_FORMAT);
  }

  private String chipDayLabel(LocalDate date) {
    return switch (date.getDayOfWeek()) {
      case MONDAY -> I18n.translate("evilradio.picker.schedule.monday.short");
      case TUESDAY -> I18n.translate("evilradio.picker.schedule.tuesday.short");
      case WEDNESDAY -> I18n.translate("evilradio.picker.schedule.wednesday.short");
      case THURSDAY -> I18n.translate("evilradio.picker.schedule.thursday.short");
      case FRIDAY -> I18n.translate("evilradio.picker.schedule.friday.short");
      case SATURDAY -> I18n.translate("evilradio.picker.schedule.saturday.short");
      case SUNDAY -> I18n.translate("evilradio.picker.schedule.sunday.short");
    };
  }

  private String dayHeaderLabel(ScheduleService.ScheduleDay day) {
    LocalDate date = this.parseScheduleDate(day.date());
    String weekday = day.weekday() == null ? "" : day.weekday().trim();
    if (date == null) {
      return weekday.isEmpty() ? day.date() : weekday;
    }
    String formatted = this.headerDayLabel(date) + " - " + date.format(CHIP_DAY_FORMAT);
    if (weekday.equalsIgnoreCase("Heute") || weekday.equalsIgnoreCase("Today")) {
      return I18n.translate("evilradio.picker.schedule.today") + " - " + formatted;
    }
    return formatted;
  }

  private String headerDayLabel(LocalDate date) {
    return switch (date.getDayOfWeek()) {
      case MONDAY -> I18n.translate("evilradio.picker.schedule.monday.full");
      case TUESDAY -> I18n.translate("evilradio.picker.schedule.tuesday.full");
      case WEDNESDAY -> I18n.translate("evilradio.picker.schedule.wednesday.full");
      case THURSDAY -> I18n.translate("evilradio.picker.schedule.thursday.full");
      case FRIDAY -> I18n.translate("evilradio.picker.schedule.friday.full");
      case SATURDAY -> I18n.translate("evilradio.picker.schedule.saturday.full");
      case SUNDAY -> I18n.translate("evilradio.picker.schedule.sunday.full");
    };
  }

  private @Nullable LocalDate parseScheduleDate(String dateStr) {
    if (dateStr == null || dateStr.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(dateStr);
    } catch (Exception ignored) {
      return null;
    }
  }

  public void startNowPlayingSession() {
    this.stopNowPlayingSession();
    Set<String> shortcodes = new LinkedHashSet<>();
    for (RadioStream stream : this.displayStreams) {
      if (!StationPickerController.isPlayable(stream)) {
        continue;
      }
      String shortcode = this.shortcodeOf(stream);
      if (shortcode != null) {
        shortcodes.add(shortcode);
      }
    }

    this.seedLivePlayingSong();
    this.fetchAllSongsSnapshot();
    this.refreshNowPlaying();
    this.startNowPlayingSnapshotTask();

    if (shortcodes.isEmpty()) {
      return;
    }
    this.nowPlayingSession = new PickerNowPlayingSession(shortcodes, this::onPickerSongUpdate);
    this.nowPlayingSession.open();
  }

  private void startNowPlayingSnapshotTask() {
    this.stopNowPlayingSnapshotTask();
    // REST-Fallback: WS liefert Songwechsel manchmal nicht zuverlässig für alle Sender.
    this.nowPlayingSnapshotTask = Task.builder(this::fetchAllSongsSnapshot)
        .repeat(15, TimeUnit.SECONDS)
        .build();
    this.nowPlayingSnapshotTask.execute();
  }

  private void stopNowPlayingSnapshotTask() {
    if (this.nowPlayingSnapshotTask != null) {
      this.nowPlayingSnapshotTask.cancel();
      this.nowPlayingSnapshotTask = null;
    }
  }

  private void fetchAllSongsSnapshot() {
    this.addon.currentSongService().fetchAllNowPlaying(songs -> {
      if (songs == null || songs.isEmpty()) {
        return;
      }
      this.addon.labyAPI().minecraft().executeOnRenderThread(() -> {
        for (Map.Entry<String, CurrentSong> entry : songs.entrySet()) {
          CurrentSong song = entry.getValue();
          if (song == null || !song.isValid()) {
            continue;
          }
          String key = normalizeShortcode(entry.getKey());
          if (key == null) {
            continue;
          }
          this.songByShortcode.put(key, song);
          this.applySongToRows(key, song);
        }
        this.refreshNowPlaying();
      });
    });
  }

  private void stopNowPlayingSession() {
    this.stopNowPlayingSnapshotTask();
    if (this.nowPlayingSession != null) {
      this.nowPlayingSession.close();
      this.nowPlayingSession = null;
    }
  }

  private void seedLivePlayingSong() {
    CurrentSong live = this.addon.currentSongService().getCurrentSong();
    if (live == null || !live.isValid()) {
      return;
    }
    String shortcode = live.getStationShortcode();
    if (shortcode == null || shortcode.isBlank()) {
      RadioStream current = this.controller.radioManager().getCurrentStream();
      shortcode = this.shortcodeOf(current);
    }
    if (shortcode == null) {
      return;
    }
    this.songByShortcode.put(shortcode, live);
    this.applySongToRows(shortcode, live);
  }

  private void onPickerSongUpdate(String shortcode, CurrentSong song) {
    if (shortcode == null || song == null || !song.isValid()) {
      return;
    }
    String key = normalizeShortcode(shortcode);
    this.songByShortcode.put(key, song);
    this.addon.labyAPI().minecraft().executeOnRenderThread(() -> {
      this.applySongToRows(key, song);
      this.refreshNowPlaying();
    });
  }

  private void applySongToRows(String shortcode, CurrentSong song) {
    String key = normalizeShortcode(shortcode);
    CurrentSong toApply = this.withMashupShowTiming(key, song);
    if (key != null && toApply != null) {
      this.songByShortcode.put(key, toApply);
    }
    for (RadioStationRowWidget row : this.rows) {
      if (key != null && key.equals(this.shortcodeOf(row.getStream()))) {
        row.setSong(toApply);
        if (StationPickerController.isMashup(row.getStream()) && toApply != null) {
          // AzuraCast liefert kein Twitch – Flags nur ergänzen, nie löschen.
          if (toApply.isOnAir()) {
            this.mashupOnAir = true;
          }
          if (toApply.isTwitch()) {
            this.mashupTwitch = true;
          }
          row.updateOnAirAndTwitchStatus(this.mashupOnAir, this.mashupTwitch);
        }
      }
    }
  }

  private CurrentSong withMashupShowTiming(@Nullable String shortcode, @Nullable CurrentSong song) {
    if (song == null || shortcode == null || !"mashup".equals(shortcode)) {
      return song;
    }
    if (this.mashupShowStatus == null || !this.mashupShowStatus.onAir()) {
      return song;
    }
    return this.addon.currentSongService().applyShowToSong(song, this.mashupShowStatus);
  }

  private void refreshNowPlaying() {
    if (this.coverWidget == null || this.coverSongWidget == null || this.coverArtistWidget == null
        || this.coverStationWidget == null) {
      return;
    }

    RadioStream current = this.controller.radioManager().getCurrentStream();
    boolean playing = this.controller.radioManager().isPlaying() && current != null;
    CurrentSong song = current != null ? this.resolveSongFor(current) : null;

    Icon icon = EvilTextures.LOGO;
    String coverUrl = null;
    if (song != null) {
      coverUrl = song.getImageUrl();
      if (coverUrl != null && !coverUrl.isBlank()) {
        icon = Icon.url(coverUrl);
      } else if (current.getIcon() != null) {
        icon = current.getIcon();
      }
    } else if (current != null && current.getIcon() != null) {
      icon = current.getIcon();
    }

    String coverKey;
    if (coverUrl != null && !coverUrl.isBlank()) {
      coverKey = coverUrl;
    } else if (current != null) {
      coverKey = "station:" + current.getName();
    } else {
      coverKey = "logo";
    }
    if (!coverKey.equals(this.appliedCoverKey)) {
      this.appliedCoverKey = coverKey;
      this.coverWidget.icon().set(icon);
    }

    if (current != null) {
      Component stationLine = Component.text(current.getDisplayName())
          .color(playing ? NamedTextColor.GREEN : NamedTextColor.GRAY);
      if (StationPickerController.isMashup(current)) {
        if (song != null && song.isOnAir()) {
          this.mashupOnAir = true;
        }
        if (song != null && song.isTwitch()) {
          this.mashupTwitch = true;
        }
        this.syncMashupStatusToRows(this.mashupOnAir, this.mashupTwitch);
        if (this.mashupOnAir) {
          stationLine = stationLine
              .append(Component.text(" | ").color(NamedTextColor.GRAY))
              .append(Component.translatable("evilradio.widget.onAir").color(NamedTextColor.RED));
        }
        if (this.mashupTwitch) {
          stationLine = stationLine
              .append(Component.translatable(" | ").color(NamedTextColor.GRAY))
              .append(Component.translatable("evilradio.widget.twitch")
                  .color(TextColor.color(145, 70, 255)));
        }
      }
      this.coverStationWidget.setComponent(stationLine);
      StationPickerSubSettings picker = this.addon.configuration().stationPicker();
      if (song != null && song.isValid()) {
        this.coverSongWidget.setComponent(
            Component.text(song.getDisplayTitle())
                .color(CurrentSongHudWidget.toTextColor(picker.songColor().get())));
        String artist = song.getArtist();
        if (artist != null && !artist.isBlank()) {
          this.coverArtistWidget.setComponent(
              Component.text(artist)
                  .color(CurrentSongHudWidget.toTextColor(picker.artistColor().get())));
          this.coverArtistWidget.setVisible(true);
        } else {
          this.coverArtistWidget.setComponent(Component.empty());
          this.coverArtistWidget.setVisible(false);
        }
      } else {
        this.coverSongWidget.setComponent(
            Component.translatable("evilradio.picker.loadingSong")
                .color(CurrentSongHudWidget.toTextColor(picker.artistColor().get())));
        this.coverArtistWidget.setComponent(Component.empty());
        this.coverArtistWidget.setVisible(false);
      }
    } else {
      this.coverStationWidget.setComponent(
          Component.translatable("evilradio.picker.previewHint").color(NamedTextColor.GRAY));
      this.coverSongWidget.setComponent(
          Component.translatable("evilradio.picker.noSongInfo").color(NamedTextColor.DARK_GRAY));
      this.coverArtistWidget.setComponent(Component.empty());
      this.coverArtistWidget.setVisible(false);
    }

    this.updateNowPlayingPlaytime(song, true);
    this.updateEqualizer(playing);
  }

  private void updateNowPlayingPlaytime(@Nullable CurrentSong song, boolean force) {
    if (this.coverTimeWidget == null) {
      return;
    }
    if (song == null || !song.isValid() || !song.hasKnownDuration()) {
      if (force || this.lastNowPlayingHadDuration || this.lastNowPlayingElapsed >= 0L) {
        this.coverTimeWidget.setComponent(Component.empty());
        this.coverTimeWidget.setVisible(false);
        this.setCoverProgressVisible(false);
        this.lastNowPlayingElapsed = -1L;
        this.lastNowPlayingHadDuration = false;
      }
      return;
    }

    long elapsed = song.getCurrentElapsedSeconds();
    if (!force && this.lastNowPlayingHadDuration && elapsed == this.lastNowPlayingElapsed) {
      return;
    }
    this.lastNowPlayingElapsed = elapsed;
    this.lastNowPlayingHadDuration = true;
    String label = song.getPlaytimeLabel();
    if (label == null || label.isBlank()) {
      this.coverTimeWidget.setVisible(false);
      this.setCoverProgressVisible(false);
      return;
    }
    this.coverTimeWidget.setComponent(Component.text(label).color(
        CurrentSongHudWidget.toTextColor(
            this.addon.configuration().stationPicker().timeColor().get())));
    this.coverTimeWidget.setVisible(true);
    this.updateCoverProgressBar(song);
  }

  private void updateCoverProgressBar(CurrentSong song) {
    if (this.coverProgressTrack == null || this.coverProgressFill == null || song == null) {
      return;
    }
    double progress = song.getProgress();
    if (progress < 0.0d) {
      this.setCoverProgressVisible(false);
      return;
    }
    float trackWidth = this.equalizerStyleButton == null
        ? COVER_PROGRESS_TRACK_WIDTH_NO_EQ
        : COVER_PROGRESS_TRACK_WIDTH;
    this.coverProgressFill.setVariable(COVER_PROGRESS_WIDTH_KEY, (float) (trackWidth * progress));
    this.setCoverProgressVisible(true);
  }

  private void setCoverProgressVisible(boolean visible) {
    if (this.coverProgressTrack == null) {
      return;
    }
    this.coverProgressTrack.setVisible(visible);
    if (!visible && this.coverProgressFill != null) {
      this.coverProgressFill.setVariable(COVER_PROGRESS_WIDTH_KEY, 0f);
    }
  }

  private void tickPlaytimes() {
    RadioStream current = this.controller.radioManager().getCurrentStream();
    CurrentSong song = current != null ? this.resolveSongFor(current) : null;
    this.updateNowPlayingPlaytime(song, false);
    for (RadioStationRowWidget row : this.rows) {
      row.tickPlaytime();
    }
    this.refreshSnapshotIfAnySongEnded();
  }

  /**
   * Wenn ein Song lokal schon am Ende klebt (elapsed == duration), REST-Snapshot früher nachladen.
   */
  private void refreshSnapshotIfAnySongEnded() {
    long now = System.currentTimeMillis();
    if (now - this.lastEndedSongRefreshAt < 5_000L) {
      return;
    }
    boolean anyEnded = false;
    for (CurrentSong cached : this.songByShortcode.values()) {
      if (cached != null && cached.isValid() && cached.hasKnownDuration()
          && cached.getCurrentElapsedSeconds() >= cached.getDuration()) {
        anyEnded = true;
        break;
      }
    }
    if (!anyEnded) {
      return;
    }
    this.lastEndedSongRefreshAt = now;
    this.fetchAllSongsSnapshot();
  }

  private void applyPanelAppearance() {
    if (this.panelWidget == null) {
      return;
    }
    StationPickerSubSettings picker = this.addon.configuration().stationPicker();
    this.panelWidget.setVariable(PICKER_PANEL_BG_VAR, picker.backgroundColor().get().get());
    this.panelWidget.setVariable(PICKER_PANEL_BORDER_VAR, picker.borderColor().get().get());
    this.panelWidget.setVariable(PICKER_PANEL_BLUR_VAR, (float) picker.backgroundBlur().get());
  }

  private void applyRowAppearance(RadioStationRowWidget row) {
    if (row == null) {
      return;
    }
    int rowBg = this.addon.configuration().stationPicker().rowBackgroundColor().get().get();
    row.setVariable(PICKER_ROW_BG_VAR, rowBg);
  }

  private boolean isEqualizerFeatureEnabled() {
    return Boolean.TRUE.equals(this.addon.configuration().stationPicker().showEqualizer().get());
  }

  private void updateEqualizer(boolean playing) {
    if (this.equalizerWidget == null) {
      return;
    }
    boolean featureEnabled = this.isEqualizerFeatureEnabled();
    EvilRadioConfiguration.EqualizerStyle style = this.addon.configuration().equalizerStyle().get();
    this.applyEqualizerStyle(style);
    this.syncEqualizerStyleButton();
    boolean showEq = featureEnabled && playing && style.isEnabled();
    if (playing) {
      if (this.nowPlayingStrip != null) {
        this.nowPlayingStrip.addId("playing");
      }
    } else if (this.nowPlayingStrip != null) {
      this.nowPlayingStrip.removeId("playing");
    }
    if (showEq) {
      if (!this.equalizerWidget.isVisible()) {
        this.equalizerWidget.setVisible(true);
      }
      this.refreshEqualizerBars();
    } else {
      if (this.equalizerWidget.isVisible()) {
        this.equalizerWidget.setVisible(false);
      }
      this.resetEqualizerVisuals();
    }
  }

  private void cycleEqualizerStyle() {
    if (!this.isEqualizerFeatureEnabled()) {
      return;
    }
    EvilRadioConfiguration.EqualizerStyle next = this.addon.configuration().equalizerStyle().get().next();
    this.addon.configuration().equalizerStyle().set(next);
    this.updateEqualizer(this.controller.radioManager().isPlaying());
  }

  private void syncEqualizerStyleButton() {
    if (this.equalizerStyleButton == null) return;
    boolean featureEnabled = this.isEqualizerFeatureEnabled();
    this.equalizerStyleButton.setVisible(featureEnabled);
    if (!featureEnabled) return;
    EvilRadioConfiguration.EqualizerStyle style = this.addon.configuration().equalizerStyle().get();
    if (style.isEnabled()) {
      this.equalizerStyleButton.removeId("off");
    } else {
      this.equalizerStyleButton.addId("off");
    }
  }

  private void applyEqualizerStyle(EvilRadioConfiguration.EqualizerStyle style) {
    if (this.equalizerWidget == null || style == null) return;
    if (style == this.appliedEqualizerStyle) {
      return;
    }
    for (EvilRadioConfiguration.EqualizerStyle known : EvilRadioConfiguration.EqualizerStyle.values()) {
      this.equalizerWidget.removeId(known.styleId());
    }
    this.equalizerWidget.addId(style.styleId());
    this.appliedEqualizerStyle = style;
  }

  private void resetEqualizerVisuals() {
    for (int i = 0; i < this.equalizerBars.size(); i++) {
      this.peakHolds[i] = 0.0F;
      DivWidget bar = this.equalizerBars.get(i);
      bar.setVariable(EQ_BAR_HEIGHT_VAR, EQ_BAR_MIN_HEIGHT);
      bar.setVariable(EQ_BAR_BOTTOM_VAR, 0.0F);
      if (i < this.equalizerPeaks.size()) {
        this.equalizerPeaks.get(i).setVariable(EQ_PEAK_BOTTOM_VAR, 0.0F);
      }
    }
  }

  private void layoutEqualizerBars() {
    if (this.equalizerWidget == null || this.equalizerBars.isEmpty()) return;
    float width = this.equalizerWidget.bounds().getWidth();
    if (width <= 1.0F) {
      // Bounds oft erst nach erstem Layout verfügbar (nach Cover bis vor EQ/Play)
      width = 198.0F;
    }
    if (Math.abs(width - this.equalizerLayoutWidth) < 0.5F) {
      return;
    }
    this.equalizerLayoutWidth = width;
    int count = this.equalizerBars.size();
    float usable = Math.max(0.0F, width - EQ_BAR_WIDTH);
    float step = count <= 1 ? 0.0F : usable / (count - 1);
    for (int i = 0; i < count; i++) {
      float left = i * step;
      this.equalizerBars.get(i).setVariable(EQ_BAR_LEFT_VAR, left);
      this.equalizerPeaks.get(i).setVariable(EQ_BAR_LEFT_VAR, left);
    }
  }

  private void refreshEqualizerBars() {
    if (this.equalizerBars.isEmpty() || this.equalizerWidget == null) return;

    this.layoutEqualizerBars();

    EvilRadioConfiguration.EqualizerStyle style = this.appliedEqualizerStyle;
    if (style == null) {
      style = this.addon.configuration().equalizerStyle().get();
      this.applyEqualizerStyle(style);
    }

    AudioSpectrumAnalyzer spectrum = this.controller.radioManager().spectrum();
    if (!style.isEnabled()) {
      this.resetEqualizerVisuals();
      return;
    }
    if (style == EvilRadioConfiguration.EqualizerStyle.SCOPE) {
      spectrum.copyWaveform(this.waveformSamples);
      this.renderScope();
      return;
    }

    spectrum.copyBands(this.spectrumBands);
    int count = Math.min(this.equalizerBars.size(), this.spectrumBands.length);
    for (int i = 0; i < count; i++) {
      float level = Math.clamp(this.spectrumBands[i], 0.0F, 1.0F);
      this.updatePeakHold(i, level);
      switch (style) {
        case PEAKS -> this.renderPeaksBar(i, level);
        case MIRROR -> this.renderMirrorBar(i, level);
        case DOTS -> this.renderDotsBar(i, level);
        default -> this.renderClassicBar(i, level);
      }
    }
  }

  private void updatePeakHold(int index, float level) {
    float peak = this.peakHolds[index];
    if (level >= peak) {
      this.peakHolds[index] = level;
    } else {
      this.peakHolds[index] = Math.max(0.0F, peak - EQ_PEAK_DECAY);
    }
  }

  private void renderClassicBar(int index, float level) {
    float height = EQ_BAR_MIN_HEIGHT + level * (EQ_BAR_MAX_HEIGHT - EQ_BAR_MIN_HEIGHT);
    DivWidget bar = this.equalizerBars.get(index);
    bar.setVariable(EQ_BAR_HEIGHT_VAR, height);
    bar.setVariable(EQ_BAR_BOTTOM_VAR, 0.0F);
    this.equalizerPeaks.get(index).setVariable(EQ_PEAK_BOTTOM_VAR, 0.0F);
  }

  private void renderPeaksBar(int index, float level) {
    float height = EQ_BAR_MIN_HEIGHT + level * (EQ_BAR_MAX_HEIGHT - EQ_BAR_MIN_HEIGHT);
    float peakBottom = this.peakHolds[index] * (EQ_BAR_MAX_HEIGHT - EQ_PEAK_MARKER);
    DivWidget bar = this.equalizerBars.get(index);
    bar.setVariable(EQ_BAR_HEIGHT_VAR, height);
    bar.setVariable(EQ_BAR_BOTTOM_VAR, 0.0F);
    this.equalizerPeaks.get(index).setVariable(EQ_PEAK_BOTTOM_VAR, peakBottom);
  }

  private void renderMirrorBar(int index, float level) {
    float height = EQ_BAR_MIN_HEIGHT + level * (EQ_BAR_MAX_HEIGHT - EQ_BAR_MIN_HEIGHT);
    float bottom = (EQ_BAR_MAX_HEIGHT - height) * 0.5F;
    DivWidget bar = this.equalizerBars.get(index);
    bar.setVariable(EQ_BAR_HEIGHT_VAR, height);
    bar.setVariable(EQ_BAR_BOTTOM_VAR, bottom);
    this.equalizerPeaks.get(index).setVariable(EQ_PEAK_BOTTOM_VAR, 0.0F);
  }

  private void renderDotsBar(int index, float level) {
    float bottom = level * (EQ_BAR_MAX_HEIGHT - 3.0F);
    DivWidget bar = this.equalizerBars.get(index);
    bar.setVariable(EQ_BAR_HEIGHT_VAR, 3.0F);
    bar.setVariable(EQ_BAR_BOTTOM_VAR, bottom);
    this.equalizerPeaks.get(index).setVariable(EQ_PEAK_BOTTOM_VAR, 0.0F);
  }

  private void renderScope() {
    int count = Math.min(this.equalizerBars.size(), this.waveformSamples.length);
    float mid = EQ_BAR_MAX_HEIGHT * 0.5F;
    for (int i = 0; i < count; i++) {
      float sample = Math.clamp(this.waveformSamples[i], -1.0F, 1.0F);
      float bottom = mid + sample * (mid - 2.0F) - 1.0F;
      DivWidget bar = this.equalizerBars.get(i);
      bar.setVariable(EQ_BAR_HEIGHT_VAR, 2.0F);
      bar.setVariable(EQ_BAR_BOTTOM_VAR, Math.max(0.0F, bottom));
      this.equalizerPeaks.get(i).setVariable(EQ_PEAK_BOTTOM_VAR, 0.0F);
    }
  }

  private @Nullable CurrentSong resolveSongFor(@Nullable RadioStream stream) {
    String shortcode = this.shortcodeOf(stream);
    CurrentSong live = this.addon.currentSongService().getCurrentSong();
    if (live != null && live.isValid()) {
      String liveShortcode = live.getStationShortcode();
      if (liveShortcode == null || liveShortcode.isBlank()) {
        liveShortcode = this.shortcodeOf(this.controller.radioManager().getCurrentStream());
      } else {
        liveShortcode = normalizeShortcode(liveShortcode);
      }
      if (shortcode != null && shortcode.equals(liveShortcode)) {
        return live;
      }
    }
    if (shortcode == null) return null;
    return this.songByShortcode.get(shortcode);
  }

  private @Nullable String shortcodeOf(@Nullable RadioStream stream) {
    if (stream == null) return null;
    return normalizeShortcode(stream.getAzuraCastShortcode());
  }

  private static @Nullable String normalizeShortcode(@Nullable String shortcode) {
    if (shortcode == null || shortcode.isBlank()) return null;
    return shortcode.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Sender in Service-Reihenfolge (Usage-/ID-Sort), ohne Pin des aktuellen Senders.
   */
  private List<RadioStream> buildDisplayStreams() {
    return new ArrayList<>(this.addon.radioStreamService().streams());
  }

  private int indexOfStream(RadioStream stream) {
    for (int i = 0; i < this.displayStreams.size(); i++) {
      if (this.displayStreams.get(i).equals(stream)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void tick() {
    super.tick();
    this.updateHoverSelection();
    this.syncControls();
    this.tickPlaytimes();
    // Sichtbarkeit + Bars jeden Tick syncen (sonst bleibt EQ manchmal unsichtbar bis Reopen)
    this.updateEqualizer(this.controller.radioManager().isPlaying());
  }

  private void syncControls() {
    if (this.playPauseButton != null) {
      this.playPauseButton.updateIcon(this.playPauseIcon());
    }
    if (this.volumeSlider != null && !this.volumeSlider.isDragging()) {
      float volume = this.addon.configuration().volume().get();
      if (Math.abs(this.volumeSlider.getValue() - volume) >= 0.5F) {
        this.volumeSlider.setValue(volume, false);
      }
    }
  }

  private Icon playPauseIcon() {
    return this.controller.radioManager().isPlaying()
        ? SpriteControls.STOP
        : SpriteControls.PLAY;
  }

  private void togglePlayPause() {
    this.controller.handleMiddleClick();
    this.syncControls();
    this.reload();
    this.startNowPlayingSession();
  }

  private void onVolumeSliderChanged(float value) {
    float rounded = Math.round(value);
    this.addon.configuration().volume().set(rounded);
  }

  @Override
  public boolean keyPressed(Key key, InputType type) {
    if (key == Key.ESCAPE) {
      this.displayPreviousScreen();
      return true;
    }
    if (this.activeTab == PickerTab.SCHEDULE) {
      if (key == Key.ARROW_LEFT || key == Key.A) {
        this.selectScheduleDay(this.selectedScheduleDayIndex - 1);
        return true;
      }
      if (key == Key.ARROW_RIGHT || key == Key.D) {
        this.selectScheduleDay(this.selectedScheduleDayIndex + 1);
        return true;
      }
      return super.keyPressed(key, type);
    }
    if (key == Key.ENTER || key == Key.NUMPAD_ENTER) {
      this.playAndClose(this.selectedIndex);
      return true;
    }
    if (key == Key.ARROW_UP || key == Key.W) {
      this.moveSelection(-1);
      return true;
    }
    if (key == Key.ARROW_DOWN || key == Key.S) {
      this.moveSelection(1);
      return true;
    }
    if (key == Key.PAGE_UP) {
      this.moveSelection(-5);
      return true;
    }
    if (key == Key.PAGE_DOWN) {
      this.moveSelection(5);
      return true;
    }

    int hotkey = this.hotkeyIndex(key);
    if (hotkey >= 0) {
      if (hotkey < this.displayStreams.size()) {
        this.playAndClose(hotkey);
      }
      return true;
    }

    return super.keyPressed(key, type);
  }

  @Override
  public boolean mouseClicked(MutableMouse mouse, MouseButton mouseButton) {
    if (mouseButton == MouseButton.MIDDLE) {
      this.togglePlayPause();
      return true;
    }
    return super.mouseClicked(mouse, mouseButton);
  }

  @Override
  public boolean mouseScrolled(MutableMouse mouse, double scrollDelta) {
    if (this.activeTab == PickerTab.SCHEDULE) {
      // Hover-Check oft false → Scroll immer an Schedule-Liste weiterreichen
      if (this.scheduleScroll != null
          && this.scheduleScroll.mouseScrolled(mouse, scrollDelta)) {
        return true;
      }
      return super.mouseScrolled(mouse, scrollDelta);
    }
    // ScrollWidget prüft die Mausposition nicht – ohne Hit-Test scrollt die Liste
    // auch über dem Volume-Slider oben in der Cover-Leiste.
    if (this.isMouseOver(this.volumeSlider, mouse)) {
      this.controller.adjustVolumeByScroll(scrollDelta);
      this.syncControls();
      return true;
    }
    if (this.stationScroll != null
        && this.isMouseOver(this.stationScroll, mouse)
        && this.stationScroll.mouseScrolled(mouse, scrollDelta)) {
      return true;
    }
    if (super.mouseScrolled(mouse, scrollDelta)) {
      return true;
    }
    this.controller.adjustVolumeByScroll(scrollDelta);
    this.syncControls();
    return true;
  }

  private boolean isMouseOver(@Nullable Widget widget, MutableMouse mouse) {
    if (widget == null || !widget.isVisible()) {
      return false;
    }
    return widget.bounds().isInRectangle(BoundsType.OUTER, mouse.getX(), mouse.getY());
  }

  private void playAndClose(int index) {
    if (index < 0 || index >= this.displayStreams.size()) {
      return;
    }
    this.selectedIndex = index;
    RadioStream stream = this.displayStreams.get(index);
    if (!StationPickerController.isPlayable(stream) || this.isPlaying(stream)) {
      this.refreshFocusStyles();
      return;
    }

    this.displayPreviousScreen();
    this.addon.labyAPI().minecraft().executeNextTick(() ->
        this.controller.playStream(stream, null));
  }

  private void moveSelection(int delta) {
    if (this.displayStreams.isEmpty()) {
      return;
    }
    int next = Math.max(0, Math.min(this.displayStreams.size() - 1, this.selectedIndex + delta));
    if (next == this.selectedIndex && this.keyboardFocus) {
      return;
    }
    this.selectedIndex = next;
    this.keyboardFocus = true;
    this.refreshFocusStyles();
  }

  private void updateHoverSelection() {
    if (this.activeTab != PickerTab.STATIONS) {
      return;
    }
    int hovered = -1;
    for (int i = 0; i < this.rows.size(); i++) {
      if (this.rows.get(i).isHovered()) {
        hovered = i;
        break;
      }
    }

    if (hovered >= 0) {
      this.selectedIndex = hovered;
      this.keyboardFocus = false;
      // Hover-Border kommt aus LSS :hover – focused-ID nur für Tastatur
      for (RadioStationRowWidget row : this.rows) {
        row.setFocusedRow(false);
      }
      return;
    }

    if (this.keyboardFocus) {
      this.refreshFocusStyles();
    } else {
      for (RadioStationRowWidget row : this.rows) {
        row.setFocusedRow(false);
      }
    }
  }

  private void refreshFocusStyles() {
    for (int i = 0; i < this.rows.size(); i++) {
      this.rows.get(i).setFocusedRow(this.keyboardFocus && i == this.selectedIndex);
    }
  }

  private boolean isPlaying(RadioStream stream) {
    RadioStream current = this.controller.radioManager().getCurrentStream();
    return current != null && current.equals(stream) && this.controller.radioManager().isPlaying();
  }

  private int hotkeyIndex(Key key) {
    if (key == Key.NUM1 || key == Key.NUMPAD1) {
      return 0;
    }
    if (key == Key.NUM2 || key == Key.NUMPAD2) {
      return 1;
    }
    if (key == Key.NUM3 || key == Key.NUMPAD3) {
      return 2;
    }
    if (key == Key.NUM4 || key == Key.NUMPAD4) {
      return 3;
    }
    if (key == Key.NUM5 || key == Key.NUMPAD5) {
      return 4;
    }
    if (key == Key.NUM6 || key == Key.NUMPAD6) {
      return 5;
    }
    if (key == Key.NUM7 || key == Key.NUMPAD7) {
      return 6;
    }
    if (key == Key.NUM8 || key == Key.NUMPAD8) {
      return 7;
    }
    if (key == Key.NUM9 || key == Key.NUMPAD9) {
      return 8;
    }
    return -1;
  }

  private void startMashupUpdates() {
    this.stopMashupUpdates();
    this.refreshMashupStatus();
    this.mashupTask = Task.builder(this::refreshMashupStatus).repeat(30, TimeUnit.SECONDS).build();
    this.mashupTask.execute();
  }

  private void stopMashupUpdates() {
    if (this.mashupTask != null) {
      this.mashupTask.cancel();
      this.mashupTask = null;
    }
  }

  private void refreshMashupStatus() {
    if (this.controller.findMashupStream() == null) {
      return;
    }
    this.controller.fetchMashupStatus(show -> {
      this.mashupShowStatus = show;
      this.mashupOnAir = show.onAir();
      this.mashupTwitch = show.twitch();
      // Auch wenn gerade ein anderer Sender läuft: Status + Sendezeit an Mashup-Zeile.
      this.syncMashupStatusToRows(this.mashupOnAir, this.mashupTwitch);
      this.applyMashupShowTimingToRows();
      this.refreshNowPlaying();
    });
  }

  private void applyMashupShowTimingToRows() {
    if (this.mashupShowStatus == null) return;
    for (RadioStationRowWidget row : this.rows) {
      if (!StationPickerController.isMashup(row.getStream())) continue;
      String key = this.shortcodeOf(row.getStream());
      CurrentSong song = key != null ? this.songByShortcode.get(key) : null;
      if (song == null || !song.isValid()) {
        continue;
      }
      CurrentSong updated = this.addon.currentSongService().applyShowToSong(song, this.mashupShowStatus);
      if (key != null) {
        this.songByShortcode.put(key, updated);
      }
      row.setSong(updated);
      row.updateOnAirAndTwitchStatus(this.mashupOnAir, this.mashupTwitch);
    }
  }

  private void syncMashupStatusToRows(boolean onAir, boolean twitch) {
    for (RadioStationRowWidget row : this.rows) {
      if (StationPickerController.isMashup(row.getStream())) {
        row.updateOnAirAndTwitchStatus(onAir, twitch);
      }
    }
  }
}
