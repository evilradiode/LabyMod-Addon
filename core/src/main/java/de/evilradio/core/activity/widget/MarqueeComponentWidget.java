package de.evilradio.core.activity.widget;

import net.labymod.api.Laby;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.TextColor;
import net.labymod.api.client.gfx.pipeline.renderer.text.FontRenderer;
import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.widget.attributes.bounds.BoundsType;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.render.font.TextOverflowStrategy;

/**
 * Textzeile mit optionalem Lauftext. Mehrere Zeilen werden über {@link MarqueeCoordinator}
 * nacheinander gescrollt.
 */
@AutoWidget
public class MarqueeComponentWidget extends DivWidget {

  private static final String GAP = "     ";
  private static final int PAUSE_TICKS = 35;
  private static final int TICKS_PER_STEP = 2;

  private final ComponentWidget label;
  private MarqueeCoordinator coordinator;
  private String plainText = "";
  private TextColor textColor;
  private boolean scrollMode = true;
  private boolean hasContent;
  private int scrollChars;
  private int tickCounter;

  public MarqueeComponentWidget() {
    this.label = ComponentWidget.empty();
    this.label.addId("marquee-label");
    this.label.overflowStrategy().set(TextOverflowStrategy.CLIP);
    this.setTicking(true);
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);
    this.children.clear();
    this.addChild(this.label);
    this.setTicking(true);
    this.paint();
  }

  void bindCoordinator(MarqueeCoordinator coordinator) {
    this.coordinator = coordinator;
  }

  public void setScrollMode(boolean scrollMode) {
    if (this.scrollMode == scrollMode) {
      return;
    }
    this.scrollMode = scrollMode;
    this.resetScrollState();
    this.paint();
  }

  /** Statischer Text ohne Laufschrift (z. B. Ladehinweise). */
  public void setComponent(Component component) {
    this.hasContent = false;
    this.plainText = "";
    this.resetScrollState();
    this.label.overflowStrategy().set(TextOverflowStrategy.CLIP);
    this.label.setComponent(component == null ? Component.empty() : component);
    this.label.updateComponent();
  }

  /** Song-/Interpret-Text; Verhalten hängt von {@link #setScrollMode(boolean)} ab. */
  public void setMarqueeText(String text, TextColor color) {
    this.plainText = text == null ? "" : text;
    this.textColor = color;
    this.hasContent = true;
    this.resetScrollState();
    this.paint();
  }

  void resetScrollState() {
    this.scrollChars = 0;
    this.tickCounter = 0;
  }

  boolean needsScroll() {
    if (!this.scrollMode || !this.hasContent || this.plainText.isEmpty() || this.textColor == null) {
      return false;
    }
    float viewport = this.bounds().getWidth(BoundsType.MIDDLE);
    if (viewport <= 1f) {
      return false;
    }
    FontRenderer font = Laby.references().minecraftFontRenderer();
    return font.getWidth(this.plainText) > viewport + 1f;
  }

  @Override
  public void tick() {
    super.tick();
    if (!this.hasContent || !this.scrollMode || this.plainText.isEmpty() || this.textColor == null) {
      return;
    }

    float viewport = this.bounds().getWidth(BoundsType.MIDDLE);
    if (viewport <= 1f) {
      return;
    }

    FontRenderer font = Laby.references().minecraftFontRenderer();
    if (!this.needsScroll()) {
      if (this.scrollChars != 0 || this.tickCounter != 0) {
        this.resetScrollState();
        this.paintFullText();
      }
      return;
    }

    boolean active = this.coordinator == null || this.coordinator.isActive(this);
    if (!active) {
      // Andere Zeile ist dran: ruhig am Anfang stehen bleiben
      if (this.scrollChars != 0 || this.tickCounter != 0) {
        this.resetScrollState();
      }
      this.paintScrollWindow(viewport, font);
      return;
    }

    this.tickCounter++;

    if (this.scrollChars == 0 && this.tickCounter <= PAUSE_TICKS) {
      this.paintScrollWindow(viewport, font);
      return;
    }

    if (this.tickCounter % TICKS_PER_STEP != 0) {
      return;
    }

    this.scrollChars++;
    int loopLength = this.plainText.length() + GAP.length();
    if (this.scrollChars >= loopLength) {
      this.scrollChars = 0;
      this.tickCounter = 0;
      this.paintScrollWindow(viewport, font);
      if (this.coordinator != null) {
        this.coordinator.onCycleComplete(this);
      }
      return;
    }
    this.paintScrollWindow(viewport, font);
  }

  private void paint() {
    if (!this.hasContent || this.textColor == null) {
      return;
    }
    if (!this.scrollMode) {
      this.label.overflowStrategy().set(TextOverflowStrategy.CLIP);
      this.paintFullText();
      return;
    }

    float viewport = this.bounds().getWidth(BoundsType.MIDDLE);
    FontRenderer font = Laby.references().minecraftFontRenderer();
    if (viewport <= 1f || font.getWidth(this.plainText) <= viewport + 1f) {
      this.paintFullText();
      return;
    }
    this.paintScrollWindow(viewport, font);
  }

  private void paintFullText() {
    this.label.setComponent(Component.text(this.plainText).color(this.textColor));
    this.label.updateComponent();
  }

  private void paintScrollWindow(float viewport, FontRenderer font) {
    String loop = this.plainText + GAP + this.plainText;
    String view = visibleWindow(loop, this.scrollChars, viewport, font);
    this.label.setComponent(Component.text(view).color(this.textColor));
    this.label.updateComponent();
  }

  private static String visibleWindow(String loop, int start, float maxWidth, FontRenderer font) {
    if (start < 0 || start >= loop.length()) {
      return "";
    }
    StringBuilder visible = new StringBuilder();
    for (int i = start; i < loop.length(); i++) {
      char next = loop.charAt(i);
      if (font.getWidth(visible.toString() + next) > maxWidth) {
        break;
      }
      visible.append(next);
    }
    return visible.toString();
  }

}
