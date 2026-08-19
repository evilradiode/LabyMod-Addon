package de.evilradio.core.activity.widget;

import de.evilradio.core.song.CurrentSongService.ShowStatus;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.TextColor;
import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;

@AutoWidget
public class MashupLiveBannerWidget extends FlexibleContentWidget {

  public static final int MODE_HIDDEN = 0;
  public static final int MODE_SWITCH = 1;
  public static final int MODE_ACTIONS = 2;

  private static final TextColor TWITCH_PURPLE = TextColor.color(145, 70, 255);

  private int mode;
  private boolean showGrussbox;
  private boolean showTwitch;
  private ShowStatus showStatus;
  private Runnable playMashup;
  private Runnable openGrussbox;
  private Runnable openTwitch;
  private ComponentWidget titleWidget;
  private ComponentWidget detailWidget;
  private boolean treeInitialized;

  public void bind(Runnable playMashup, Runnable openGrussbox, Runnable openTwitch) {
    this.playMashup = playMashup;
    this.openGrussbox = openGrussbox;
    this.openTwitch = openTwitch;
  }

  public int mode() {
    return this.mode;
  }

  public boolean apply(int mode, boolean showGrussbox, boolean showTwitch, ShowStatus showStatus) {
    boolean structureChanged = mode != this.mode
        || showGrussbox != this.showGrussbox
        || showTwitch != this.showTwitch;
    this.mode = mode;
    this.showGrussbox = showGrussbox;
    this.showTwitch = showTwitch;
    this.showStatus = showStatus;
    if (!structureChanged) {
      if (mode == MODE_SWITCH) {
        this.refreshSwitchText();
      }
      return false;
    }
    if (this.treeInitialized) {
      this.reInitialize();
    }
    return true;
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);
    this.children.clear();
    this.treeInitialized = true;
    this.removeId("switch");
    this.removeId("actions");
    this.setPressable(null);
    this.titleWidget = null;
    this.detailWidget = null;

    if (this.mode == MODE_SWITCH) {
      this.addId("switch");
      this.setPressable(() -> {
        if (this.playMashup != null) {
          this.playMashup.run();
        }
      });
      this.titleWidget = ComponentWidget.empty().addId("mashup-live-title");
      this.detailWidget = ComponentWidget.empty().addId("mashup-live-detail");
      this.addContent(this.titleWidget);
      this.addContent(this.detailWidget);
      this.refreshSwitchText();
      return;
    }

    if (this.mode == MODE_ACTIONS) {
      this.addId("actions");
      if (this.showGrussbox) {
        this.addContent(ButtonWidget.component(
            Component.translatable("evilradio.schedule.grussbox").color(NamedTextColor.WHITE),
            () -> {
              if (this.openGrussbox != null) {
                this.openGrussbox.run();
              }
            })
            .addId("mashup-live-grussbox"));
      }
      if (this.showTwitch) {
        this.addContent(ButtonWidget.component(
            Component.text("Twitch").color(TWITCH_PURPLE),
            () -> {
              if (this.openTwitch != null) {
                this.openTwitch.run();
              }
            })
            .addId("mashup-live-twitch"));
      }
    }
  }

  private void refreshSwitchText() {
    if (this.titleWidget == null || this.detailWidget == null || this.showStatus == null) {
      return;
    }
    String moderator = this.showStatus.moderatorName();
    if (moderator != null && !moderator.isBlank()) {
      this.titleWidget.setComponent(
          Component.translatable(
                  "evilradio.widget.mashupLiveTitle",
                  Component.text(moderator).color(NamedTextColor.GOLD))
              .color(NamedTextColor.WHITE));
    } else {
      this.titleWidget.setComponent(
          Component.translatable("evilradio.widget.mashupLive").color(NamedTextColor.WHITE));
    }
    this.detailWidget.setComponent(
        Component.translatable("evilradio.widget.mashupLiveSwitch").color(NamedTextColor.GREEN));
  }
}
