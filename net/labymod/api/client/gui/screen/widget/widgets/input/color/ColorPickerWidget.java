package net.labymod.api.client.gui.screen.widget.widgets.input.color;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Consumer;
import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.widget.overlay.WidgetReference.ClickRemoveStrategy;
import net.labymod.api.client.gui.screen.widget.overlay.WidgetReference.KeyPressRemoveStrategy;
import net.labymod.api.client.gui.screen.widget.cursor.CursorTypes;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.color.overlay.ColorPickerOverlayWidget;
import net.labymod.api.client.gui.screen.widget.widgets.overlay.OverlayWidget;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.configuration.settings.Setting;
import net.labymod.api.configuration.settings.accessor.SettingAccessor;
import net.labymod.api.configuration.settings.annotation.SettingElement;
import net.labymod.api.configuration.settings.annotation.SettingFactory;
import net.labymod.api.configuration.settings.annotation.SettingWidget;
import net.labymod.api.configuration.settings.widget.WidgetFactory;
import net.labymod.api.util.Color;
import org.jetbrains.annotations.NotNull;

@AutoWidget
@Link("color-picker.lss")
@SettingWidget
public class ColorPickerWidget extends OverlayWidget {

  private final ColorData colorData;
  private DivWidget colorPreview;

  private ColorPickerWidget(ColorData colorData) {
    super(ClickRemoveStrategy.OUTSIDE, KeyPressRemoveStrategy.ESCAPE, true);
    this.colorData = colorData;
    this.setHoverCursor(CursorTypes.POINTING_HAND, true);
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);

    this.colorPreview = new DivWidget();
    this.addChild(this.colorPreview);
  }

  @Override
  public void renderWidget(ScreenContext context) {
    if (this.colorPreview != null) {
      this.colorPreview.backgroundColor().set(this.colorData.getActualColor().get());
    }

    super.renderWidget(context);
  }

  @Override
  protected @NotNull Parent content() {
    return new ColorPickerOverlayWidget(this.colorData);
  }

  @Override
  public boolean isHoverComponentRendered() {
    return this.hasHoverComponent() ? super.isHoverComponentRendered() : this.isHovered();
  }

  public boolean enabledAlpha() {
    return this.colorData.enabledAlpha();
  }

  public boolean enabledChroma() {
    return this.colorData.enabledChroma();
  }

  public boolean enabledChromaSpeed() {
    return this.colorData.enabledChromaSpeed();
  }

  public ColorPickerWidget alpha(boolean alpha) {
    this.colorData.alphaEnabled(alpha);
    return this;
  }

  public ColorPickerWidget chroma(boolean chroma) {
    this.colorData.chromaEnabled(chroma);
    return this;
  }

  public ColorPickerWidget chromaSpeed(boolean chromaSpeed) {
    this.colorData.chromaSpeedEnabled(chromaSpeed);
    return this;
  }

  public ColorPickerWidget set(Color color) {
    this.colorData.set(color);
    return this;
  }

  public ColorPickerWidget addUpdateListener(Object instance, Consumer<Color> updateListener) {
    this.colorData.addUpdateListener(
        instance, () -> updateListener.accept(this.colorData.getActualColor()));
    return this;
  }

  public Color value() {
    return this.colorData.getActualColor();
  }

  public static ColorPickerWidget of(Color color) {
    return new ColorPickerWidget(new ColorData(color));
  }

  public static ColorPickerWidget of(ConfigProperty<Color> property) {
    ColorPickerWidget widget = of(property.get());
    ColorData colorData = widget.colorData;
    colorData.addUpdateListener(widget, () -> {
      property.set(colorData.getActualColor());
    });
    property.addChangeListener((t, oldValue, newValue) -> {
      colorData.set(newValue);
    });
    return widget;
  }

  /**
   * The color picker setting annotation. Will result in a {@link ColorPickerWidget} when added to a
   * field in a config.
   */
  @SettingElement
  @Target({ElementType.FIELD})
  @Retention(RetentionPolicy.RUNTIME)
  public @interface ColorPickerSetting {

    /**
     * If the user should be able to change the alpha/opacity value of the color. The default is
     * false.
     *
     * @return if the user should be able to change the alpha value of the color
     */
    boolean alpha() default false;

    /**
     * Whether the alpha value should be removed from the color. Only takes affect if
     * {@link ColorPickerSetting#alpha()} is false. The default is true.
     *
     * @return if the alpha value should be removed
     */
    boolean removeAlpha() default true;

    /**
     * If the user should be able to add the rgb/chroma effect to the color. The default is false.
     *
     * @return if the user should be able to add the rgb/chroma effect to the color
     */
    boolean chroma() default false;

    /**
     * If the user should be able to change the chroma speed value of the color. Only takes affect
     * if {@link ColorPickerSetting#chroma()} is true. The default is true.
     *
     * @return if the user should be able to change the chroma speed value of the color
     */
    boolean chromaSpeed() default true;

    /**
     * Whether the chroma speed value should be removed from the color. Only takes affect if
     * {@link ColorPickerSetting#chromaSpeed()} is false. The default is false.
     *
     * @return if the chroma speed value should be removed
     */
    boolean removeChromaSpeed() default false;
  }

  @SettingFactory
  public static class Factory implements
      WidgetFactory<ColorPickerSetting, ColorPickerWidget> {

    public Factory() {
    }

    @Override
    public ColorPickerWidget[] create(
        Setting setting, ColorPickerSetting annotation,
        SettingAccessor accessor
    ) {
      Object accessorObject = accessor.get();

      ColorPickerWidget widget;
      if (accessorObject instanceof Color) {
        widget = of(accessor.property());
      } else {
        if (annotation.chroma()) {
          throw new IllegalArgumentException("Chroma is only supported for Color objects");
        }

        widget = of(Color.of((int) accessorObject));
        ColorData colorData = widget.colorData;
        colorData.addUpdateListener(this, () -> {
          accessor.set(colorData.getColor().get());
        });
      }

      ColorData colorData = widget.colorData;
      colorData.chromaEnabled(annotation.chroma());
      colorData.alphaEnabled(annotation.alpha());
      colorData.removeAlpha(annotation.removeAlpha());
      colorData.chromaSpeedEnabled(annotation.chromaSpeed());
      colorData.removeChromaSpeed(annotation.removeChromaSpeed());

      return new ColorPickerWidget[]{widget};
    }

    @Override
    public Class<?>[] types() {
      return new Class[]{Integer.class, int.class, Color.class};
    }
  }
}
