package de.evilradio.core.activity.widget;

import java.util.ArrayList;
import java.util.List;

/**
 * Stellt sicher, dass von mehreren Lauftext-Zeilen immer nur eine gleichzeitig scrollt.
 */
public final class MarqueeCoordinator {

  private final List<MarqueeComponentWidget> lines = new ArrayList<>();
  private int activeIndex;

  public void clear() {
    this.lines.clear();
    this.activeIndex = 0;
  }

  public void register(MarqueeComponentWidget line) {
    if (line == null || this.lines.contains(line)) {
      return;
    }
    this.lines.add(line);
    line.bindCoordinator(this);
  }

  public boolean isActive(MarqueeComponentWidget line) {
    if (this.lines.isEmpty() || line == null) {
      return false;
    }
    ensureActiveValid();
    return this.lines.get(this.activeIndex) == line;
  }

  /** Nach Textwechsel: wieder bei der ersten zu langen Zeile starten. */
  public void onContentChanged() {
    this.activeIndex = 0;
    for (MarqueeComponentWidget line : this.lines) {
      line.resetScrollState();
    }
    this.ensureActiveValid();
  }

  /** Aktuelle Zeile hat einen vollen Scroll-Durchlauf beendet. */
  public void onCycleComplete(MarqueeComponentWidget line) {
    if (!this.isActive(line)) {
      return;
    }
    int size = this.lines.size();
    if (size == 0) {
      return;
    }
    for (int step = 1; step <= size; step++) {
      int next = (this.activeIndex + step) % size;
      MarqueeComponentWidget candidate = this.lines.get(next);
      if (candidate.needsScroll()) {
        this.activeIndex = next;
        candidate.resetScrollState();
        return;
      }
    }
  }

  private void ensureActiveValid() {
    int size = this.lines.size();
    if (size == 0) {
      return;
    }
    if (this.activeIndex >= size) {
      this.activeIndex = 0;
    }
    if (this.lines.get(this.activeIndex).needsScroll()) {
      return;
    }
    for (int i = 0; i < size; i++) {
      int idx = (this.activeIndex + i) % size;
      if (this.lines.get(idx).needsScroll()) {
        this.activeIndex = idx;
        this.lines.get(idx).resetScrollState();
        return;
      }
    }
  }

}
