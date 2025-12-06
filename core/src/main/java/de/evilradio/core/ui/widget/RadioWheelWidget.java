package de.evilradio.core.ui.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import de.evilradio.core.radio.RadioStream;
import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.widget.widgets.WheelWidget;

@AutoWidget
@Link("widget/radio-wheel.lss")
public class RadioWheelWidget extends WheelWidget {

  private final IntSupplier pageSupplier;
  private final IntSupplier segmentCountSupplier;
  private final List<RadioStream> streams;
  private Supplier<CharSequence> querySupplier;
  private SegmentSupplier segmentSupplier;

  public RadioWheelWidget(IntSupplier pageSupplier, IntSupplier segmentCountSupplier) {
    this.pageSupplier = pageSupplier;
    this.segmentCountSupplier = segmentCountSupplier;
    this.streams = new ArrayList<>();
  }

  @Override
  public void initialize(Parent parent) {
    this.refresh();
    super.initialize(parent);
  }

  public void refresh() {
    this.removeChildIf((widget) -> widget instanceof WheelWidget.Segment);

    if (this.segmentSupplier != null) {
      int page = this.pageSupplier.getAsInt();
      CharSequence searchQuery = this.querySupplier == null ? null : this.querySupplier.get();

      // Filtere Streams basierend auf Suchanfrage
      List<RadioStream> filteredStreams = new ArrayList<>();
      for (RadioStream stream : this.streams) {
        if (searchQuery == null || stream.getDisplayName().toLowerCase().contains(searchQuery.toString().toLowerCase())) {
          filteredStreams.add(stream);
        }
      }

      int size = filteredStreams.size();
      int segmentCount = this.segmentCountSupplier.getAsInt();

      for (int i = 0; i < segmentCount; ++i) {
        int index = page * segmentCount + i;
        RadioStream stream = index >= 0 && index < size ? filteredStreams.get(index) : null;

        WheelWidget.Segment segment = this.segmentSupplier.get(index, i, stream);

        if (this.initialized) {
          this.addSegmentInitialized(segment);
        } else {
          this.addSegment(segment);
        }
      }
    }
  }

  public RadioWheelWidget querySupplier(Supplier<CharSequence> querySupplier) {
    this.querySupplier = querySupplier;
    return this;
  }

  public RadioWheelWidget segmentSupplier(Function<RadioStream, WheelWidget.Segment> segmentSupplier) {
    return this.segmentSupplier((index, wheelIndex, stream) -> segmentSupplier.apply(stream));
  }

  public RadioWheelWidget segmentSupplier(SegmentSupplier segmentSupplier) {
    this.segmentSupplier = segmentSupplier;
    return this;
  }

  public void setStreams(List<RadioStream> streams) {
    this.streams.clear();
    this.streams.addAll(streams);
  }

  public List<RadioStream> getStreams() {
    return new ArrayList<>(this.streams);
  }

  public interface SegmentSupplier {
    WheelWidget.Segment get(int index, int wheelIndex, RadioStream stream);
  }
}

