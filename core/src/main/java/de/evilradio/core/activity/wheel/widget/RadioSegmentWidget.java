package de.evilradio.core.activity.wheel.widget;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.radio.RadioStream;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.TextColor;
import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.WheelWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.resources.ResourceLocation;

@AutoWidget
@Link("widget/radio-segment.lss")
public class RadioSegmentWidget extends WheelWidget.Segment {

  private final EvilRadioAddon addon;
  private final RadioStream stream;
  private ComponentWidget nameWidget;

  public RadioSegmentWidget(EvilRadioAddon addon, RadioStream stream, boolean isActive) {
    this.addon = addon;
    this.stream = stream;

    if (stream != null) {
      Icon icon = stream.getIcon();
      if (icon == null) {
        icon = Icon.texture(ResourceLocation.create("evilradio", "textures/stations/comingsoon.png"));
      }

      IconWidget iconWidget = new IconWidget(icon);
      iconWidget.addId("radio-segment-icon");
      this.addChild(iconWidget);

      TextColor color = isActive
          ? NamedTextColor.GREEN 
          : NamedTextColor.WHITE;
      this.nameWidget = ComponentWidget.component(Component.text(stream.getDisplayName(), color));
      this.nameWidget.addId("radio-segment-name");
      this.addChild(this.nameWidget);
    }
  }

  @Override
  public boolean isSelectable() {
    if(stream == null || stream.getUrl() == null || stream.getUrl().isEmpty()) return false;
    if(this.addon.radioManager().getCurrentStream() != null && this.addon.radioManager().getCurrentStream() == stream) return false;
    return true;
  }

  public RadioStream getStream() {
    return this.stream;
  }

  public void updateActive(boolean isActive) {
    if (this.nameWidget != null && this.stream != null) {
      TextColor color = isActive
          ? NamedTextColor.GREEN 
          : NamedTextColor.WHITE;
      this.nameWidget.setComponent(Component.text(stream.getDisplayName(), color));
    }
  }
}

