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
  private boolean isOnAir = false;
  private boolean isTwitch = false;

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
    this.updateNameWidget(isActive, this.isOnAir, this.isTwitch);
  }

  public void updateOnAirStatus(boolean isOnAir) {
    this.isOnAir = isOnAir;
    RadioStream currentStream = this.addon.radioManager().getCurrentStream();
    boolean isActive = currentStream != null && currentStream.equals(this.stream) && this.addon.radioManager().isPlaying();
    this.updateNameWidget(isActive, isOnAir, this.isTwitch);
  }

  public void updateTwitchStatus(boolean isTwitch) {
    this.isTwitch = isTwitch;
    RadioStream currentStream = this.addon.radioManager().getCurrentStream();
    boolean isActive = currentStream != null && currentStream.equals(this.stream) && this.addon.radioManager().isPlaying();
    this.updateNameWidget(isActive, this.isOnAir, isTwitch);
  }

  public void updateOnAirAndTwitchStatus(boolean isOnAir, boolean isTwitch) {
    this.isOnAir = isOnAir;
    this.isTwitch = isTwitch;
    RadioStream currentStream = this.addon.radioManager().getCurrentStream();
    boolean isActive = currentStream != null && currentStream.equals(this.stream) && this.addon.radioManager().isPlaying();
    this.updateNameWidget(isActive, isOnAir, isTwitch);
  }

  private void updateNameWidget(boolean isActive, boolean isOnAir, boolean isTwitch) {
    if (this.nameWidget != null && this.stream != null) {
      TextColor color = isActive
          ? NamedTextColor.GREEN 
          : NamedTextColor.WHITE;
      
      Component nameComponent = Component.text(this.stream.getDisplayName(), color);
      
      // Zeige "On Air" und "Twitch" nur für Mashup-Streams
      if (this.isMashupStream()) {
        boolean hasStatus = false;
        if (isOnAir) {
          nameComponent = nameComponent.append(Component.text(" | ").color(NamedTextColor.GRAY))
              .append(Component.text("● ON AIR").color(NamedTextColor.RED));
          hasStatus = true;
        }
        if (isTwitch) {
          if (hasStatus) {
            nameComponent = nameComponent.append(Component.text(" | ").color(NamedTextColor.GRAY));
          } else {
            nameComponent = nameComponent.append(Component.text(" | ").color(NamedTextColor.GRAY));
          }
          TextColor twitchColor = TextColor.color(145, 70, 255); // #9146ff
          nameComponent = nameComponent.append(Component.text("● TWITCH").color(twitchColor));
        }
      }
      
      this.nameWidget.setComponent(nameComponent);
    } else {
      this.addon.logger().warn("[RadioSegmentWidget] updateNameWidget - nameWidget or stream is null");
    }
  }

  private boolean isMashupStream() {
    if (this.stream == null) {
      return false;
    }
    String streamName = this.stream.getName();
    return streamName != null && streamName.equalsIgnoreCase("mashup");
  }
}

