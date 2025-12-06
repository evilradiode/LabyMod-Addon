package de.evilradio.core.ui.widget;

import de.evilradio.core.radio.RadioStream;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
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

  private final RadioStream stream;
  private IconWidget iconWidget;
  private ComponentWidget nameWidget;

  public RadioSegmentWidget(RadioStream stream, boolean isActive) {
    this.stream = stream;
    // Ein Stream ist nur selektierbar, wenn er eine gültige (nicht-null und nicht-leere) URL hat
    // Dies ist konsistent mit der "Coming Soon"-Logik in RadioMenuActivity und RadioWheelOverlay
    boolean isComingSoon = stream == null || stream.getUrl() == null || stream.getUrl().isEmpty();
    this.setSelectable(!isComingSoon);

    if (stream != null) {
      Icon icon = stream.getIcon();
      if (icon == null) {
        icon = Icon.texture(ResourceLocation.create("evilradio", "textures/stations/comingsoon.png"));
      }

      this.iconWidget = new IconWidget(icon);
      this.iconWidget.addId("radio-segment-icon");
      // Setze die Größe direkt, um Verzerrung zu vermeiden
      // Die Größe wird hauptsächlich über CSS gesteuert, aber wir setzen hier eine Basis
      this.addChild(this.iconWidget);

      String displayName = stream.getDisplayName();
      if (displayName == null || displayName.isEmpty()) {
        displayName = stream.getName();
      }

      net.labymod.api.client.component.format.TextColor color = isActive 
          ? NamedTextColor.GREEN 
          : NamedTextColor.WHITE;
      this.nameWidget = ComponentWidget.component(Component.text(displayName, color));
      this.nameWidget.addId("radio-segment-name");
      this.addChild(this.nameWidget);
    }
  }

  public RadioStream getStream() {
    return this.stream;
  }

  public void updateActive(boolean isActive) {
    if (this.nameWidget != null && this.stream != null) {
      net.labymod.api.client.component.format.TextColor color = isActive 
          ? NamedTextColor.GREEN 
          : NamedTextColor.WHITE;
      String displayName = this.stream.getDisplayName();
      if (displayName == null || displayName.isEmpty()) {
        displayName = this.stream.getName();
      }
      this.nameWidget.setComponent(Component.text(displayName, color));
    }
  }
}

