package de.evilradio.core.activity.widget;

import de.evilradio.core.schedule.ScheduleShow;
import de.evilradio.core.song.CurrentSongService.ShowStatus;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.TextColor;
import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.HorizontalListWidget;

@AutoWidget
public class MashupLiveBannerWidget extends FlexibleContentWidget {

  public static final int MODE_HIDDEN = 0;
  public static final int MODE_SWITCH = 1;
  public static final int MODE_ACTIONS = 2;
  public static final int MODE_UPCOMING = 3;

  private static final TextColor TWITCH_PURPLE = TextColor.color(145, 70, 255);

  private int mode;
  private boolean showGrussbox;
  private boolean showTwitch;
  private ShowStatus showStatus;
  private ScheduleShow upcomingShow;
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

  public boolean apply(
      int mode,
      boolean showGrussbox,
      boolean showTwitch,
      ShowStatus showStatus,
      ScheduleShow upcomingShow) {
    boolean structureChanged = mode != this.mode
        || showGrussbox != this.showGrussbox
        || showTwitch != this.showTwitch
        || !sameUpcoming(this.upcomingShow, upcomingShow);
    this.mode = mode;
    this.showGrussbox = showGrussbox;
    this.showTwitch = showTwitch;
    this.showStatus = showStatus;
    this.upcomingShow = upcomingShow;
    if (!structureChanged) {
      if (mode == MODE_SWITCH || mode == MODE_ACTIONS) {
        this.refreshLiveTitle();
      } else if (mode == MODE_UPCOMING) {
        this.refreshUpcomingText();
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
    this.removeId("upcoming");
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
      this.refreshLiveTitle();
      if (this.detailWidget != null) {
        this.detailWidget.setComponent(
            Component.translatable("evilradio.widget.mashupLiveSwitch").color(NamedTextColor.GREEN));
      }
      return;
    }

    if (this.mode == MODE_UPCOMING) {
      this.addId("upcoming");
      this.titleWidget = ComponentWidget.empty().addId("mashup-live-title");
      this.detailWidget = ComponentWidget.empty().addId("mashup-live-detail");
      this.addContent(this.titleWidget);
      this.addContent(this.detailWidget);
      this.refreshUpcomingText();
      return;
    }

    if (this.mode == MODE_ACTIONS) {
      this.addId("actions");
      this.titleWidget = ComponentWidget.empty().addId("mashup-live-title");
      this.addContent(this.titleWidget);
      this.refreshLiveTitle();

      HorizontalListWidget actions = new HorizontalListWidget().addId("mashup-live-actions");
      if (this.showGrussbox) {
        ButtonWidget grussbox = ButtonWidget.component(
            Component.translatable("evilradio.schedule.grussbox").color(NamedTextColor.WHITE),
            () -> {
              if (this.openGrussbox != null) {
                this.openGrussbox.run();
              }
            })
            .addId("mashup-live-grussbox");
        if (!this.showTwitch) {
          grussbox.addId("alone");
        }
        grussbox.setHoverComponent(
            Component.translatable("evilradio.schedule.grussboxHover").color(NamedTextColor.GRAY));
        actions.addEntry(grussbox);
      }
      if (this.showTwitch) {
        ButtonWidget twitch = ButtonWidget.component(
            Component.translatable("evilradio.schedule.twitch").color(TWITCH_PURPLE),
            () -> {
              if (this.openTwitch != null) {
                this.openTwitch.run();
              }
            })
            .addId("mashup-live-twitch");
        if (!this.showGrussbox) {
          twitch.addId("alone");
        }
        twitch.setHoverComponent(
            Component.translatable("evilradio.schedule.twitchButtonHover")
                .color(NamedTextColor.GRAY));
        actions.addEntry(twitch);
      }
      this.addContent(actions);
    }
  }

  private void refreshLiveTitle() {
    if (this.titleWidget == null || this.showStatus == null) {
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
  }

  private void refreshUpcomingText() {
    if (this.titleWidget == null || this.detailWidget == null || this.upcomingShow == null) {
      return;
    }
    String time = this.upcomingShow.getStartTime() == null ? "" : this.upcomingShow.getStartTime();
    this.titleWidget.setComponent(
        Component.translatable("evilradio.widget.mashupUpcomingTitle", Component.text(time))
            .color(NamedTextColor.WHITE));
    String showName = this.upcomingShow.getShowName() == null || this.upcomingShow.getShowName().isBlank()
        ? "Mashup"
        : this.upcomingShow.getShowName().trim();
    String moderator = this.upcomingShow.getModerator();
    if (moderator != null && !moderator.isBlank()) {
      this.detailWidget.setComponent(
          Component.text(showName).color(NamedTextColor.WHITE)
              .append(Component.newline())
              .append(Component.translatable(
                      "evilradio.widget.mashupUpcomingWith",
                      Component.text(moderator.trim()).color(NamedTextColor.GOLD))
                  .color(NamedTextColor.GRAY)));
    } else {
      this.detailWidget.setComponent(Component.text(showName).color(NamedTextColor.WHITE));
    }
  }

  private static boolean sameUpcoming(ScheduleShow left, ScheduleShow right) {
    if (left == right) {
      return true;
    }
    if (left == null || right == null) {
      return false;
    }
    return java.util.Objects.equals(left.getDate(), right.getDate())
        && java.util.Objects.equals(left.getStartTime(), right.getStartTime())
        && java.util.Objects.equals(left.getShowName(), right.getShowName())
        && java.util.Objects.equals(left.getModerator(), right.getModerator());
  }
}
