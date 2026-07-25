package de.evilradio.core.activity.picker.widget;

import de.evilradio.core.EvilTextures;
import de.evilradio.core.schedule.ScheduleShow;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.TextColor;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.widget.attributes.ObjectFitType;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.models.OperatingSystem;
import org.jetbrains.annotations.Nullable;

@AutoWidget
@Link("widget/schedule-show-row.lss")
public class ScheduleShowRowWidget extends DivWidget {

  private static final TextColor TIME_ORANGE = TextColor.color(230, 126, 34);
  private static final TextColor TWITCH_PURPLE = TextColor.color(145, 70, 255);
  private static final String GRUSSBOX_URL =
      "https://evil-radio.de/sendeplan/music-request.php";
  private static final String TWITCH_URL = "https://www.twitch.tv/evilradiode";

  private final ScheduleShow show;
  private final boolean mashupLive;

  public ScheduleShowRowWidget(ScheduleShow show, boolean mashupLive) {
    this.show = show;
    this.mashupLive = mashupLive;
  }

  public ScheduleShow getShow() {
    return this.show;
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);
    this.children.clear();

    // Links: Zeit → Avatar → Name → Twitch/Grußbox
    DivWidget meta = new DivWidget().addId("schedule-meta");
    DivWidget metaStack = new DivWidget().addId("schedule-meta-stack");
    metaStack.addChild(ComponentWidget.component(
            Component.text(this.show.getTimeLabel()).color(TIME_ORANGE))
        .addId("schedule-time"));
    metaStack.addChild(new IconWidget(this.iconFromUrl(this.show.getProfilePictureUrl()))
        .addId("schedule-avatar"));
    metaStack.addChild(ComponentWidget.component(
            Component.text(this.show.getModerator()).color(NamedTextColor.GRAY))
        .addId("schedule-moderator"));

    boolean showGrussbox = this.mashupLive && this.show.isGrussbox();
    boolean showTwitch = this.show.isTwitch();
    if (showGrussbox || showTwitch) {
      metaStack.addId("with-badges");
      if (showGrussbox) {
        metaStack.addChild(ButtonWidget.component(
                Component.translatable("evilradio.schedule.grussbox").color(NamedTextColor.WHITE),
                () -> openUrl(GRUSSBOX_URL))
            .addId("schedule-grussbox"));
      }
      if (showTwitch) {
        ButtonWidget twitchButton = ButtonWidget.component(
                Component.translatable("evilradio.widget.twitch").color(TWITCH_PURPLE),
                () -> openUrl(TWITCH_URL))
            .addId("schedule-twitch");
        if (!showGrussbox) {
          twitchButton.addId("alone");
        }
        metaStack.addChild(twitchButton);
      }
    }
    meta.addChild(metaStack);
    this.addChild(meta);

    // Rechts: Titel (+ ON AIR), darunter Banner
    DivWidget main = new DivWidget().addId("schedule-main");
    main.addChild(ComponentWidget.component(
            Component.text(this.show.getShowName()).color(NamedTextColor.WHITE))
        .addId("schedule-title"));

    if (this.show.isOnAir()) {
      main.addChild(ComponentWidget.component(
              Component.translatable("evilradio.widget.onAir").color(NamedTextColor.RED))
          .addId("schedule-on-air"));
      this.addId("on-air");
    }

    String bannerUrl = this.show.getShowPictureUrl();
    if (bannerUrl != null && !bannerUrl.isBlank()) {
      IconWidget banner = new IconWidget(Icon.url(bannerUrl)).addId("schedule-banner");
      banner.objectFit().set(ObjectFitType.CONTAIN);
      main.addChild(banner);
    } else {
      this.addId("no-banner");
    }
    this.addChild(main);
  }

  private static void openUrl(String url) {
    OperatingSystem.getPlatform().openUrl(url);
  }

  private Icon iconFromUrl(@Nullable String url) {
    if (url == null || url.isBlank()) {
      return EvilTextures.LOGO;
    }
    return Icon.url(url);
  }
}
