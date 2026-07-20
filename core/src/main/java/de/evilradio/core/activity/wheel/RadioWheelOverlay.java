package de.evilradio.core.activity.wheel;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.activity.picker.StationPickerController;
import de.evilradio.core.activity.wheel.widget.RadioSegmentWidget;
import de.evilradio.core.activity.wheel.widget.RadioWheelWidget;
import de.evilradio.core.configuration.StationPickerStyle;
import de.evilradio.core.radio.RadioStream;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.activity.AutoActivity;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.activity.types.AbstractWheelInteractionOverlayActivity;
import net.labymod.api.client.gui.screen.activity.util.PageNavigator;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.key.MouseButton;
import net.labymod.api.client.gui.screen.widget.AbstractWidget;
import net.labymod.api.client.gui.screen.widget.Widget;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.WheelWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.KeyEvent;
import net.labymod.api.event.client.input.MouseButtonEvent;
import net.labymod.api.event.client.input.MouseScrollEvent;
import net.labymod.api.util.CharSequences;
import net.labymod.api.util.concurrent.task.Task;
import net.labymod.api.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

@Link("activity/radio-wheel.lss")
@AutoActivity
public class RadioWheelOverlay extends AbstractWheelInteractionOverlayActivity {

  private static final String SUBTITLE_INFO_ID = "subtitle-info";
  private static final String SUBTITLE_INFO_CONTROLS_ID = "subtitle-info-controls";

  private final EvilRadioAddon addon;
  private final StationPickerController controller;
  private boolean isWheelOpen = false;
  private Task mashupOnAirUpdateTask;

  public RadioWheelOverlay(EvilRadioAddon addon) {
    this.addon = addon;
    this.controller = new StationPickerController(addon);
    addon.labyAPI().eventBus().registerListener(this);
  }

  @Override
  protected void closeInteractionOverlay() {
    this.isWheelOpen = false;
    this.stopMashupOnAirUpdateTask();
    this.controller.playStream(this.findSelectedStream(), null);
    super.closeInteractionOverlay();
  }

  @Override
  protected void openInteractionOverlay() {
    this.isWheelOpen = true;
    this.startMashupOnAirUpdateTask();
    super.openInteractionOverlay();
  }

  @Override
  protected Component createTitleComponent() {
    if (!this.hasEntries()) {
      return Component.translatable("evilradio.wheel.noStationsAvailable").color(NamedTextColor.DARK_RED);
    }
    return Component.translatable("evilradio.wheel.selectStation").color(NamedTextColor.RED);
  }

  @Override
  protected VerticalListWidget<Widget> createSubtitle() {
    VerticalListWidget<Widget> list = new VerticalListWidget<>().addId(WHEEL_SUBTITLE_ID);

    if (hasEntries()) {
      list.addChild(this.createPageBar());
    }

    VerticalListWidget<ComponentWidget> infoSubtitle =
        new VerticalListWidget<ComponentWidget>().addId(SUBTITLE_INFO_ID);
    infoSubtitle.addChild(
        ComponentWidget.component(this.controller.controlsLine()).addId(SUBTITLE_INFO_CONTROLS_ID));
    infoSubtitle.addChild(ComponentWidget.component(this.controller.scrollInfoLine()));

    list.addChild(infoSubtitle);
    return list;
  }

  @Override
  protected boolean hasEntries() {
    return !this.addon.radioStreamService().streams().isEmpty();
  }

  @Override
  protected WheelWidget createWheelWidget() {
    RadioWheelWidget wheel = new RadioWheelWidget(
        () -> this.pageNavigator().getCurrentPage(),
        this::getSegmentCount
    );
    wheel.querySupplier(() -> {
      CharSequence searchText = this.getSearchText();
      return CharSequences.isEmpty(searchText) ? null : searchText;
    });

    wheel.setStreams(this.addon.radioStreamService().streams());

    wheel.segmentSupplier((index, wheelIndex, stream) -> {
      if (stream == null) {
        WheelWidget.Segment segment = new WheelWidget.Segment();
        segment.setSelectable(false);
        return segment;
      }

      RadioStream currentStream = this.controller.radioManager().getCurrentStream();
      boolean isActive = currentStream != null && currentStream.equals(stream)
          && this.controller.radioManager().isPlaying();

      RadioSegmentWidget segment = new RadioSegmentWidget(this.addon, stream, isActive);
      segment.addId("radio-wrapper");
      if (!StationPickerController.isPlayable(stream)) {
        segment.addId("coming-soon");
      }

      if (StationPickerController.isMashup(stream)) {
        this.updateMashupSegmentOnAirStatus(segment);
      }

      return segment;
    });

    return wheel;
  }

  @Override
  protected Key getKeyToOpen() {
    return this.addon.configuration().radioMenuKeybind().get();
  }

  @Override
  protected void onInitializeMappedKeys(Object2IntMap<Key> mappedKeys) {
    mappedKeys.put(Key.NUM1, 0);
    mappedKeys.put(Key.NUM2, 1);
    mappedKeys.put(Key.NUM3, 2);
    mappedKeys.put(Key.NUM4, 3);
    mappedKeys.put(Key.NUM5, 4);
    mappedKeys.put(Key.NUM6, 5);
    mappedKeys.put(Key.NUMPAD1, 0);
    mappedKeys.put(Key.NUMPAD2, 1);
    mappedKeys.put(Key.NUMPAD3, 2);
    mappedKeys.put(Key.NUMPAD4, 3);
    mappedKeys.put(Key.NUMPAD5, 4);
    mappedKeys.put(Key.NUMPAD6, 5);
  }

  @Override
  protected void onKey(Key key, KeyEvent.State state) {
    if (key == Key.ESCAPE) {
      this.isWheelOpen = false;
      this.closeInteraction();
      return;
    }
    int mappedPosition = this.getMappedPosition(key);
    if (mappedPosition != Integer.MIN_VALUE) {
      RadioStream stream = this.findStreamByPosition(mappedPosition);
      if (stream != null) {
        this.controller.playStream(stream, this::closeInteraction);
      }
    }
  }

  @Override
  protected void renderInteractionOverlay(ScreenContext context) {
    if (this.isWheelOpen && this.hasEntries()) {
      this.updateTitleWidgetIfPossible();
    }
  }

  private void updateTitleWidgetIfPossible() {
    Widget container = this.document.findFirstChildIf(widget -> widget.hasId(CONTAINER_ID));
    if (container == null) {
      return;
    }
    if (!(container instanceof DivWidget containerDiv)) {
      return;
    }

    Widget subtitle = containerDiv.findFirstChildIf(widget -> widget.hasId(WHEEL_SUBTITLE_ID));
    if (!(subtitle instanceof VerticalListWidget<?> subtitleWidget)) {
      return;
    }

    Widget subtitleInfo = subtitleWidget.findFirstChildIf(widget -> widget.hasId(SUBTITLE_INFO_ID));
    if (!(subtitleInfo instanceof VerticalListWidget<?> subtitleInfoWidget)) {
      return;
    }

    Widget subtitleControls =
        subtitleInfoWidget.findFirstChildIf(widget -> widget.hasId(SUBTITLE_INFO_CONTROLS_ID));
    if (!(subtitleControls instanceof ComponentWidget subtitleControlsWidget)) {
      return;
    }
    subtitleControlsWidget.setComponent(this.controller.controlsLine());
  }

  @Subscribe
  public void onMouseScroll(MouseScrollEvent event) {
    if (!this.isWheelOpen) {
      return;
    }
    if (this.getKeyToOpen() == null) {
      return;
    }
    event.setCancelled(true);
    this.controller.adjustVolumeByScroll(event.delta());
  }

  @Subscribe
  public void onMouseButton(MouseButtonEvent event) {
    if (!this.isWheelOpen) {
      return;
    }
    if (event.button() != MouseButton.MIDDLE) {
      return;
    }
    event.setCancelled(true);
    this.controller.handleMiddleClick();
  }

  @Override
  protected boolean shouldOpenInteractionMenu() {
    if (!this.addon.configuration().enabled().get()) {
      return false;
    }
    StationPickerStyle style = this.addon.configuration().stationPickerStyle().get();
    return style == null || style.isWheel();
  }

  @Override
  public void initialize(Parent parent) {
    this.refreshStreams();
    super.initialize(parent);
  }

  private void refreshStreams() {
    int maxPages = MathHelper.ceil(
        (float) this.addon.radioStreamService().streams().size() / (float) this.getSegmentCount())
        - 1;
    PageNavigator pageNavigator = this.pageNavigator();
    pageNavigator.setMaximumPage(maxPages);
    pageNavigator.setMinimumPage(0);
  }

  private RadioStream findStreamByPosition(int position) {
    int currentPage = this.pageNavigator().getCurrentPage();
    int pagePosition = currentPage * this.getSegmentCount() + position;
    List<RadioStream> streams = this.addon.radioStreamService().streams();
    return pagePosition >= streams.size() ? null : streams.get(pagePosition);
  }

  private @Nullable RadioStream findSelectedStream() {
    for (AbstractWidget<?> child : this.wheelWidget().getChildren()) {
      if (child instanceof RadioSegmentWidget radioSegmentWidget) {
        if (radioSegmentWidget.isSelectable() && radioSegmentWidget.isSegmentSelected()) {
          return radioSegmentWidget.getStream();
        }
      }
    }
    return null;
  }

  private void startMashupOnAirUpdateTask() {
    this.stopMashupOnAirUpdateTask();
    this.updateMashupOnAirStatus();
    this.mashupOnAirUpdateTask = Task.builder(() -> {
      if (this.isWheelOpen) {
        this.updateMashupOnAirStatus();
      }
    }).repeat(30, TimeUnit.SECONDS).build();
    this.mashupOnAirUpdateTask.execute();
  }

  private void stopMashupOnAirUpdateTask() {
    if (this.mashupOnAirUpdateTask != null) {
      this.mashupOnAirUpdateTask.cancel();
      this.mashupOnAirUpdateTask = null;
    }
  }

  private void updateMashupSegmentOnAirStatus(RadioSegmentWidget segment) {
    this.controller.fetchMashupStatus(segment::updateOnAirAndTwitchStatus);
  }

  private void updateMashupOnAirStatus() {
    if (!this.isWheelOpen || this.controller.findMashupStream() == null) {
      return;
    }

    this.controller.fetchMashupStatus((isOnAir, isTwitch) -> {
      for (AbstractWidget<?> child : this.wheelWidget().getChildren()) {
        if (child instanceof RadioSegmentWidget radioSegmentWidget) {
          if (StationPickerController.isMashup(radioSegmentWidget.getStream())) {
            radioSegmentWidget.updateOnAirAndTwitchStatus(isOnAir, isTwitch);
          }
        }
      }
    });
  }
}
