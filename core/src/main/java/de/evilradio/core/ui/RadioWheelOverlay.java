package de.evilradio.core.ui;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.radio.RadioManager;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.ui.widget.RadioSegmentWidget;
import de.evilradio.core.ui.widget.RadioWheelWidget;
import net.labymod.api.Laby;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.activity.AutoActivity;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.activity.types.AbstractWheelInteractionOverlayActivity;
import net.labymod.api.client.gui.screen.activity.util.PageNavigator;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.widget.AbstractWidget;
import net.labymod.api.event.client.input.KeyEvent;
import net.labymod.api.client.gui.screen.widget.widgets.WheelWidget;
import net.labymod.api.util.CharSequences;
import net.labymod.api.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

@Link("activity/radio-wheel.lss")
@AutoActivity
public class RadioWheelOverlay extends AbstractWheelInteractionOverlayActivity {

  private final EvilRadioAddon addon;
  private final RadioManager radioManager;

  public RadioWheelOverlay(EvilRadioAddon addon) {
    this.addon = addon;
    this.radioManager = addon.radioManager();
  }

  @Override
  protected void closeInteractionOverlay() {
    this.playStream(null, false);
    super.closeInteractionOverlay();
  }

  @Override
  protected Component createTitleComponent() {
    if (!this.hasEntries()) {
      return Component.text("Keine Sender verfügbar", NamedTextColor.DARK_RED);
    } else {
      return Component.text("Wähle einen Evil-Radio Sender", NamedTextColor.RED);
    }
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

    // Setze die Streams
    wheel.setStreams(this.addon.radioStreamService().streams());

    wheel.segmentSupplier((index, wheelIndex, stream) -> {
      if (stream == null) {
        WheelWidget.Segment segment = new WheelWidget.Segment();
        segment.setSelectable(false);
        return segment;
      }

      RadioStream currentStream = this.radioManager.getCurrentStream();
      boolean isActive = currentStream != null && currentStream.equals(stream) && this.radioManager.isPlaying();

      RadioSegmentWidget segment = new RadioSegmentWidget(stream, isActive);
      segment.addId("radio-wrapper");
      // Die Selektierbarkeit wird bereits im RadioSegmentWidget-Konstruktor gesetzt
      // basierend auf der konsistenten "Coming Soon"-Logik
      boolean isComingSoon = stream.getUrl() == null || stream.getUrl().isEmpty();
      
      if (isComingSoon) {
        segment.addId("coming-soon");
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
    int mappedPosition = this.getMappedPosition(key);
    if (mappedPosition != Integer.MIN_VALUE) {
      RadioStream stream = this.findStreamByPosition(mappedPosition);
      if (stream != null) {
        this.playStream(stream, true);
      }
    }
  }

  @Override
  protected void renderInteractionOverlay(ScreenContext context) {
    // Optional: Hier können wir zusätzliche Overlay-Rendering-Logik hinzufügen
    // z.B. um den aktiven Stream-Status zu aktualisieren
  }

  @Override
  protected boolean shouldOpenInteractionMenu() {
    return this.addon.configuration().enabled().get();
  }

  @Override
  public void initialize(Parent parent) {
    this.refreshStreams();
    super.initialize(parent);
  }


  private void refreshStreams() {
    int maxPages = MathHelper.ceil((float) this.addon.radioStreamService().streams().size() / (float) this.getSegmentCount()) - 1;
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

  private void playStream(RadioStream forcedStream, boolean closeMenu) {
    RadioStream stream = forcedStream;
    if(forcedStream == null) {
      stream = this.findSelectedStream();
    }
    if (stream != null && stream.getUrl() != null && !stream.getUrl().isEmpty()) {
      this.radioManager.playStream(stream);
      Laby.labyAPI().minecraft().chatExecutor().displayClientMessage(
          Component.text("Stream gestartet: " + stream.getDisplayName(), NamedTextColor.GREEN)
      );
    }

    if (closeMenu) {
      this.closeInteraction();
    }
  }

  private @Nullable RadioStream findSelectedStream() {
    for(AbstractWidget<?> child : this.wheelWidget().getChildren()) {
      if (child instanceof RadioSegmentWidget radioSegmentWidget) {
        if (radioSegmentWidget.isSelectable() && radioSegmentWidget.isSegmentSelected()) {
          return radioSegmentWidget.getStream();
        }
      }
    }

    return null;
  }

}

