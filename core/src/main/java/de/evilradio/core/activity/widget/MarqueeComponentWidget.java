package de.evilradio.core.activity.widget;

import net.labymod.api.Laby;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.TextColor;
import net.labymod.api.client.gfx.pipeline.renderer.text.FontRenderer;
import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.screen.AutoAlignType;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.widget.Widget;
import net.labymod.api.client.gui.screen.widget.attributes.bounds.BoundsType;
import net.labymod.api.client.gui.screen.widget.size.SizeType;
import net.labymod.api.client.gui.screen.widget.size.WidgetSide;
import net.labymod.api.client.gui.screen.widget.size.WidgetSize;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.render.font.TextOverflowStrategy;

/**
 * Textzeile mit optionalem Lauftext. Mehrere Zeilen werden über {@link MarqueeCoordinator}
 * nacheinander gescrollt. Der Text bleibt während des Scrollens stabil; nur {@code translateX}
 * wird pro Frame aktualisiert.
 */
@AutoWidget
public class MarqueeComponentWidget extends DivWidget {

  private static final String GAP = "     ";
  private static final long PAUSE_MS = 1750L;
  private static final float PIXELS_PER_SECOND = 28f;
  private static final float MAX_DELTA_SECONDS = 0.1f;

  private final ComponentWidget label;
  private MarqueeCoordinator coordinator;
  private String plainText = "";
  private TextColor textColor;
  private boolean scrollMode = true;
  private boolean hasContent;
  private boolean loopTextApplied;
  private float scrollOffset;
  private float loopWidth = -1f;
  private long lastFrameTimeMs;
  private long pauseUntilMs;
  private int lastAnimatedFrame = -1;

  public MarqueeComponentWidget() {
    this.label = ComponentWidget.empty();
    this.label.addId("marquee-label");
    this.label.overflowStrategy().set(TextOverflowStrategy.CLIP);
    this.label.useFloatingPointPosition().set(true);
    this.stencilTranslation().set(true);
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);
    this.children.clear();
    this.addChild(this.label);
    this.stencilTranslation().set(true);
    this.paint();
  }

  /**
   * Verhindert, dass der breite Lauftext die Eltern-Breite (und damit {@code needsScroll} der
   * anderen Zeilen) beeinflusst.
   */
  @Override
  public float getContentWidth(BoundsType type) {
    float width = this.bounds().getWidth(type);
    if (width > 1f) {
      return width;
    }
    return this.getDefaultContentWidth(type);
  }

  /** Label darf vollständige Textbreite rendern, ohne auf die Viewport-Breite begrenzt zu werden. */
  @Override
  public boolean hasAutoBounds(Widget child, AutoAlignType type) {
    if (child == this.label && type == AutoAlignType.WIDTH) {
      return false;
    }
    return super.hasAutoBounds(child, type);
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
    this.applyIdleLabel();
    this.label.setComponent(component == null ? Component.empty() : component);
    this.label.updateComponent();
  }

  /** Song-/Interpret-Text; Verhalten hängt von {@link #setScrollMode(boolean)} ab. */
  public void setMarqueeText(String text, TextColor color) {
    this.plainText = text == null ? "" : text;
    this.textColor = color;
    this.hasContent = true;
    this.loopWidth = -1f;
    this.resetScrollState();
    this.paint();
  }

  void resetScrollState() {
    this.scrollOffset = 0f;
    this.lastFrameTimeMs = 0L;
    this.pauseUntilMs = 0L;
    this.lastAnimatedFrame = -1;
    this.loopTextApplied = false;
    this.label.setTranslateX(0f);
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
  public void render(ScreenContext context) {
    this.advanceScroll();
    super.render(context);
  }

  private void advanceScroll() {
    if (!this.hasContent || !this.scrollMode || this.plainText.isEmpty() || this.textColor == null) {
      return;
    }

    float viewport = this.bounds().getWidth(BoundsType.MIDDLE);
    if (viewport <= 1f) {
      return;
    }

    if (!this.needsScroll()) {
      if (this.loopTextApplied || this.scrollOffset != 0f || this.label.getTranslateX() != 0f) {
        this.resetScrollState();
        this.paintFullText();
      }
      return;
    }

    boolean active = this.coordinator == null || this.coordinator.isActive(this);
    if (!active) {
      if (this.loopTextApplied || this.scrollOffset != 0f || this.label.getTranslateX() != 0f) {
        this.scrollOffset = 0f;
        this.lastFrameTimeMs = 0L;
        this.pauseUntilMs = 0L;
        this.lastAnimatedFrame = -1;
        this.loopTextApplied = false;
        this.label.setTranslateX(0f);
        this.paintFullText();
      }
      return;
    }

    this.ensureLoopText();

    int frame = Laby.references().frameTimer().getFrame();
    if (frame == this.lastAnimatedFrame) {
      this.label.setTranslateX(-this.scrollOffset);
      return;
    }
    this.lastAnimatedFrame = frame;

    long now = System.currentTimeMillis();
    if (this.lastFrameTimeMs == 0L) {
      this.lastFrameTimeMs = now;
      if (this.pauseUntilMs == 0L) {
        this.pauseUntilMs = now + PAUSE_MS;
      }
      this.label.setTranslateX(0f);
      return;
    }

    float deltaSeconds = (now - this.lastFrameTimeMs) / 1000f;
    this.lastFrameTimeMs = now;
    if (deltaSeconds < 0f) {
      deltaSeconds = 0f;
    } else if (deltaSeconds > MAX_DELTA_SECONDS) {
      deltaSeconds = MAX_DELTA_SECONDS;
    }

    if (now < this.pauseUntilMs) {
      this.label.setTranslateX(0f);
      return;
    }

    FontRenderer font = Laby.references().minecraftFontRenderer();
    this.ensureLoopWidth(font);
    this.scrollOffset += deltaSeconds * PIXELS_PER_SECOND;

    if (this.scrollOffset >= this.loopWidth) {
      this.scrollOffset = 0f;
      this.pauseUntilMs = now + PAUSE_MS;
      this.label.setTranslateX(0f);
      if (this.coordinator != null) {
        this.coordinator.onCycleComplete(this);
      }
      return;
    }

    this.label.setTranslateX(-this.scrollOffset);
  }

  private void paint() {
    if (!this.hasContent || this.textColor == null) {
      return;
    }
    if (!this.scrollMode) {
      this.paintFullText();
      return;
    }

    float viewport = this.bounds().getWidth(BoundsType.MIDDLE);
    FontRenderer font = Laby.references().minecraftFontRenderer();
    if (viewport <= 1f || font.getWidth(this.plainText) <= viewport + 1f) {
      this.paintFullText();
      return;
    }
    // Noch nicht aktiv: ruhiger Starttext. Loop-Text erst wenn diese Zeile dran ist.
    this.paintFullText();
  }

  private void paintFullText() {
    this.applyIdleLabel();
    this.loopTextApplied = false;
    this.label.setComponent(Component.text(this.plainText).color(this.textColor));
    this.label.updateComponent();
  }

  private void ensureLoopText() {
    if (this.loopTextApplied) {
      return;
    }
    this.applyScrollingLabel();
    String loop = this.plainText + GAP + this.plainText;
    this.label.setComponent(Component.text(loop).color(this.textColor));
    this.label.updateComponent();
    this.loopTextApplied = true;
  }

  private void applyIdleLabel() {
    this.label.overflowStrategy().set(TextOverflowStrategy.CLIP);
    this.label.setSize(SizeType.ACTUAL, WidgetSide.WIDTH, WidgetSize.percentage(100f));
    this.label.setTranslateX(0f);
  }

  private void applyScrollingLabel() {
    this.label.overflowStrategy().set(TextOverflowStrategy.WRAP);
    this.label.maxLines().set(1);
    this.label.setSize(SizeType.ACTUAL, WidgetSide.WIDTH, WidgetSize.fitContent());
  }

  private void ensureLoopWidth(FontRenderer font) {
    if (this.loopWidth >= 0f) {
      return;
    }
    this.loopWidth = font.getWidth(this.plainText + GAP);
  }

}
