package de.evilradio.core.activity.picker;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.EvilTextures;
import de.evilradio.core.activity.picker.widget.RadioStationRowWidget;
import de.evilradio.core.configuration.EqualizerStyle;
import de.evilradio.core.radio.AudioSpectrumAnalyzer;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.song.CurrentSong;
import de.evilradio.core.song.azuracast.PickerNowPlayingSession;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
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
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.ScrollWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.util.concurrent.task.Task;
import org.jetbrains.annotations.Nullable;

/**
 * Eigenständige Sender-Auswahl als echter Minecraft-Screen (Maus frei).
 */
@Link("activity/radio-station-list.lss")
@AutoActivity
public class RadioStationListActivity extends SimpleActivity {

  private static final String OPEN_ANIMATION_ID = "picker-open";
  private static final String PICKER_PANEL_BLUR_VAR = "--picker-panel-blur";
  private static final float PICKER_PANEL_BLUR = 18.0F;
  private static final String EQ_BAR_HEIGHT_VAR = "--eq-bar-height";
  private static final String EQ_BAR_BOTTOM_VAR = "--eq-bar-bottom";
  private static final String EQ_BAR_LEFT_VAR = "--eq-bar-left";
  private static final String EQ_PEAK_BOTTOM_VAR = "--eq-peak-bottom";
  private static final int EQ_BAR_COUNT = AudioSpectrumAnalyzer.BAND_COUNT;
  private static final float EQ_BAR_MIN_HEIGHT = 2.0F;
  private static final float EQ_BAR_MAX_HEIGHT = 38.0F;
  private static final float EQ_BAR_WIDTH = 3.0F;
  private static final float EQ_PEAK_DECAY = 0.012F;
  private static final float EQ_PEAK_MARKER = 2.0F;

  private final EvilRadioAddon addon;
  private final StationPickerController controller;

  private final List<RadioStationRowWidget> rows = new ArrayList<>();
  private final List<RadioStream> displayStreams = new ArrayList<>();
  private final Map<String, CurrentSong> songByShortcode = new ConcurrentHashMap<>();

  private Widget panelWidget;
  private DivWidget nowPlayingStrip;
  private ScrollWidget stationScroll;
  private SliderWidget volumeSlider;
  private ButtonWidget playPauseButton;
  private ButtonWidget equalizerStyleButton;
  private IconWidget coverWidget;
  private ComponentWidget coverSongWidget;
  private ComponentWidget coverStationWidget;
  private ComponentWidget coverTimeWidget;
  private DivWidget equalizerWidget;
  private final List<DivWidget> equalizerBars = new ArrayList<>();
  private final List<DivWidget> equalizerPeaks = new ArrayList<>();
  private final float[] spectrumBands = new float[EQ_BAR_COUNT];
  private final float[] waveformSamples = new float[EQ_BAR_COUNT];
  private final float[] peakHolds = new float[EQ_BAR_COUNT];
  private @Nullable EqualizerStyle appliedEqualizerStyle;
  private @Nullable String appliedCoverKey;
  private long lastNowPlayingElapsed = -1L;
  private boolean lastNowPlayingHadDuration;
  private float equalizerLayoutWidth = -1.0F;

  private int selectedIndex;
  private boolean keyboardFocus;
  private boolean mashupOnAir;
  private boolean mashupTwitch;
  private Task mashupTask;
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
    panel.setVariable(PICKER_PANEL_BLUR_VAR, PICKER_PANEL_BLUR);
    this.panelWidget = panel;

    DivWidget header = new DivWidget().addId("picker-header");
    header.addChild(new IconWidget(EvilTextures.LOGO).addId("picker-logo"));
    Component title = this.displayStreams.isEmpty()
        ? Component.translatable("evilradio.wheel.noStationsAvailable").color(NamedTextColor.DARK_RED)
        : Component.translatable("evilradio.wheel.selectStation").color(NamedTextColor.RED);
    header.addChild(ComponentWidget.component(title).addId("picker-title"));
    header.addChild(ButtonWidget.text("✕", this::displayPreviousScreen).addId("picker-close-x"));
    panel.addChild(header);

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
    this.coverTimeWidget = ComponentWidget.empty().addId("picker-cover-time");
    this.coverTimeWidget.setVisible(false);
    coverTexts.addChild(this.coverStationWidget);
    coverTexts.addChild(this.coverSongWidget);
    coverTexts.addChild(this.coverTimeWidget);
    coverStrip.addChild(coverTexts);
    this.equalizerStyleButton = ButtonWidget.text("EQ", this::cycleEqualizerStyle)
        .addId("picker-eq-toggle");
    coverStrip.addChild(this.equalizerStyleButton);
    this.playPauseButton = ButtonWidget.text(
        this.playPauseIcon(),
        this::togglePlayPause
    ).addId("picker-play-pause");
    coverStrip.addChild(this.playPauseButton);
    this.syncEqualizerStyleButton();
    panel.addChild(coverStrip);

    VerticalListWidget<RadioStationRowWidget> list =
        new VerticalListWidget<RadioStationRowWidget>().addId("station-list");
    for (int i = 0; i < this.displayStreams.size(); i++) {
      RadioStream stream = this.displayStreams.get(i);
      boolean playing = this.isPlaying(stream);
      RadioStationRowWidget row = new RadioStationRowWidget(stream, playing);
      // Kein sticky Focus beim Öffnen – nur Hover/Tastatur
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
    panel.addChild(this.stationScroll);

    VerticalListWidget<Widget> footer = new VerticalListWidget<>().addId("picker-footer");
    float volume = this.addon.configuration().volume().get();
    this.volumeSlider = new SliderWidget(1.0F, this::onVolumeSliderChanged)
        .range(0.0F, 100.0F)
        .withFormatter(value -> Component.translatable(
            "evilradio.wheel.volume",
            Component.text(String.valueOf(Math.round(value)))))
        .addId("picker-volume-slider");
    this.volumeSlider.setValue(volume, false);
    footer.addChild(this.volumeSlider);

    panel.addChild(footer);
    this.document.addChild(panel);
    this.appliedCoverKey = null;
    this.refreshNowPlaying();
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

  private void startNowPlayingSession() {
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

    if (shortcodes.isEmpty()) {
      return;
    }
    this.nowPlayingSession = new PickerNowPlayingSession(shortcodes, this::onPickerSongUpdate);
    this.nowPlayingSession.open();
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
          this.songByShortcode.put(entry.getKey(), song);
          this.applySongToRows(entry.getKey(), song);
        }
        this.refreshNowPlaying();
      });
    });
  }

  private void stopNowPlayingSession() {
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
    this.songByShortcode.put(shortcode.trim(), live);
    this.applySongToRows(shortcode.trim(), live);
  }

  private void onPickerSongUpdate(String shortcode, CurrentSong song) {
    if (shortcode == null || song == null || !song.isValid()) {
      return;
    }
    this.songByShortcode.put(shortcode, song);
    this.addon.labyAPI().minecraft().executeOnRenderThread(() -> {
      this.applySongToRows(shortcode, song);
      this.refreshNowPlaying();
    });
  }

  private void applySongToRows(String shortcode, CurrentSong song) {
    for (RadioStationRowWidget row : this.rows) {
      if (shortcode.equals(this.shortcodeOf(row.getStream()))) {
        row.setSong(song);
      }
    }
  }

  private void refreshNowPlaying() {
    if (this.coverWidget == null || this.coverSongWidget == null || this.coverStationWidget == null) {
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
      this.coverStationWidget.setComponent(
          Component.text(current.getDisplayName())
              .color(playing ? NamedTextColor.GREEN : NamedTextColor.GRAY));
      if (song != null && song.isValid()) {
        this.coverSongWidget.setComponent(
            Component.text(song.getFormatted()).color(NamedTextColor.GRAY));
      } else {
        this.coverSongWidget.setComponent(
            Component.translatable("evilradio.picker.loadingSong").color(NamedTextColor.DARK_GRAY));
      }
    } else {
      this.coverStationWidget.setComponent(
          Component.translatable("evilradio.picker.previewHint").color(NamedTextColor.GRAY));
      this.coverSongWidget.setComponent(
          Component.translatable("evilradio.picker.noSongInfo").color(NamedTextColor.DARK_GRAY));
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
    String label = CurrentSong.formatTime(elapsed) + " / " + CurrentSong.formatTime(song.getDuration());
    this.coverTimeWidget.setComponent(Component.text(label).color(NamedTextColor.DARK_GRAY));
    this.coverTimeWidget.setVisible(true);
  }

  private void tickPlaytimes() {
    RadioStream current = this.controller.radioManager().getCurrentStream();
    CurrentSong song = current != null ? this.resolveSongFor(current) : null;
    this.updateNowPlayingPlaytime(song, false);
    for (RadioStationRowWidget row : this.rows) {
      row.tickPlaytime();
    }
  }

  private void updateEqualizer(boolean playing) {
    if (this.equalizerWidget == null) {
      return;
    }
    EqualizerStyle style = this.addon.configuration().equalizerStyle().get();
    this.applyEqualizerStyle(style);
    this.syncEqualizerStyleButton();
    boolean showEq = playing && style.isEnabled();
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
    EqualizerStyle next = this.addon.configuration().equalizerStyle().get().next();
    this.addon.configuration().equalizerStyle().set(next);
    this.updateEqualizer(this.controller.radioManager().isPlaying());
  }

  private void syncEqualizerStyleButton() {
    if (this.equalizerStyleButton == null) {
      return;
    }
    EqualizerStyle style = this.addon.configuration().equalizerStyle().get();
    if (style.isEnabled()) {
      this.equalizerStyleButton.removeId("off");
    } else {
      this.equalizerStyleButton.addId("off");
    }
  }

  private void applyEqualizerStyle(EqualizerStyle style) {
    if (this.equalizerWidget == null || style == null) {
      return;
    }
    if (style == this.appliedEqualizerStyle) {
      return;
    }
    for (EqualizerStyle known : EqualizerStyle.values()) {
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
    if (this.equalizerWidget == null || this.equalizerBars.isEmpty()) {
      return;
    }
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
    if (this.equalizerBars.isEmpty() || this.equalizerWidget == null) {
      return;
    }

    this.layoutEqualizerBars();

    EqualizerStyle style = this.appliedEqualizerStyle;
    if (style == null) {
      style = this.addon.configuration().equalizerStyle().get();
      this.applyEqualizerStyle(style);
    }

    AudioSpectrumAnalyzer spectrum = this.controller.radioManager().spectrum();
    if (!style.isEnabled()) {
      this.resetEqualizerVisuals();
      return;
    }
    if (style == EqualizerStyle.SCOPE) {
      spectrum.copyWaveform(this.waveformSamples);
      this.renderScope();
      return;
    }

    spectrum.copyBands(this.spectrumBands);
    int count = Math.min(this.equalizerBars.size(), this.spectrumBands.length);
    for (int i = 0; i < count; i++) {
      float level = Math.max(0.0F, Math.min(1.0F, this.spectrumBands[i]));
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
      float sample = Math.max(-1.0F, Math.min(1.0F, this.waveformSamples[i]));
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
      }
      if (shortcode != null && shortcode.equals(liveShortcode == null ? null : liveShortcode.trim())) {
        return live;
      }
    }
    if (shortcode == null) {
      return null;
    }
    return this.songByShortcode.get(shortcode);
  }

  private @Nullable String shortcodeOf(@Nullable RadioStream stream) {
    if (stream == null) {
      return null;
    }
    String shortcode = stream.getAzuraCastShortcode();
    if (shortcode == null || shortcode.isBlank()) {
      return null;
    }
    return shortcode.trim();
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
  public boolean shouldRenderBackground() {
    return true;
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
      this.playPauseButton.updateComponent(Component.text(this.playPauseIcon()));
    }
    if (this.volumeSlider != null && !this.volumeSlider.isDragging()) {
      float volume = this.addon.configuration().volume().get();
      if (Math.abs(this.volumeSlider.getValue() - volume) >= 0.5F) {
        this.volumeSlider.setValue(volume, false);
      }
    }
  }

  private String playPauseIcon() {
    return this.controller.radioManager().isPlaying() ? "⏹" : "▶";
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
    if (this.stationScroll != null && this.stationScroll.isHovered()
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
    this.controller.fetchMashupStatus((onAir, twitch) -> {
      this.mashupOnAir = onAir;
      this.mashupTwitch = twitch;
      for (RadioStationRowWidget row : this.rows) {
        if (StationPickerController.isMashup(row.getStream())) {
          row.updateOnAirAndTwitchStatus(onAir, twitch);
        }
      }
    });
  }
}
