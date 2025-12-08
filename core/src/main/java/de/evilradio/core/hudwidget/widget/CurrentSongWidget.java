package de.evilradio.core.hudwidget.widget;

import de.evilradio.core.EvilTextures.SpriteControls;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import de.evilradio.core.song.CurrentSong;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.hud.hudwidget.HudWidget.Updatable;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.client.resources.ResourceLocation;

@Link("widget/song-widget.lss")
@AutoWidget
public class CurrentSongWidget extends FlexibleContentWidget implements Updatable {

  private final CurrentSongHudWidget hudWidget;
  private ComponentWidget trackWidget;
  private ComponentWidget artistWidget;
  private IconWidget coverWidget;
  private DivWidget controlsWidget;
  private IconWidget playPauseWidget;

  public CurrentSongWidget(CurrentSongHudWidget hudWidget) {
    this.hudWidget = hudWidget;
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);
    this.children.clear();

    if (!this.hudWidget.getConfig().showCover().get()) {
      this.addId("no-cover");
    }

    boolean leftAligned = this.hudWidget.anchor().isLeft();
    this.addId(leftAligned ? "left" : "right");

    this.coverWidget = new IconWidget(Icon.texture(ResourceLocation.create("evilradio", "textures/logo.png")));
    this.coverWidget.addId("cover");

    // add cover if the hud widget is left-aligned
    if (leftAligned) {
      this.addContent(this.coverWidget);
    }

    FlexibleContentWidget player = new FlexibleContentWidget();
    player.addId("player");

    FlexibleContentWidget textAndControl = new FlexibleContentWidget();
    textAndControl.addId("text-and-control");

    // Text
    VerticalListWidget<ComponentWidget> text = new VerticalListWidget<>();
    text.addId("text");

    this.trackWidget = ComponentWidget.empty();
    text.addChild(this.trackWidget);

    this.artistWidget = ComponentWidget.empty();
    text.addChild(this.artistWidget);

    // Controls
    this.controlsWidget = new DivWidget();
    this.controlsWidget.addId("controls");

    this.playPauseWidget = new IconWidget(
        !this.hudWidget.addon().radioManager().isPlaying() ? SpriteControls.PAUSE : SpriteControls.PLAY
    );
    this.playPauseWidget.addId("play");
    this.playPauseWidget.setPressable(() -> {
      this.playPauseWidget.icon().set(
          this.hudWidget.addon().radioManager().isPlaying() ? SpriteControls.PLAY : SpriteControls.PAUSE
      );

      if(this.hudWidget.addon().radioManager().isPlaying()) {
        this.hudWidget.addon().radioManager().pauseStream();
      } else {
        this.hudWidget.addon().radioManager().playStream(this.hudWidget.addon().radioStreamService().getCurrentSelectedStream());
      }
    });
    this.controlsWidget.addChild(this.playPauseWidget);

    // Add text & controls to player based on the alignment
    if (leftAligned) {
      textAndControl.addFlexibleContent(text);
      textAndControl.addContent(this.controlsWidget);
    } else {
      textAndControl.addContent(this.controlsWidget);
      textAndControl.addFlexibleContent(text);
    }

    player.addFlexibleContent(textAndControl);
    this.addContent(player);

    // add cover if the hud widget is right-aligned
    if (!leftAligned) {
      this.addContent(this.coverWidget);
    }

    this.updateTrack(this.hudWidget.addon().currentSongService().getCurrentSong());
  }

  @Override
  public void tick() {
    super.tick();
  }

  @Override
  public void update(String reason) {
    if (reason == null) {
      this.reInitialize();
      return;
    }

    if (reason.equals(CurrentSongHudWidget.SONG_CHANGE_REASON)) {
      this.updateTrack(this.hudWidget.addon().currentSongService().getCurrentSong());
    }

    if (reason.equals(CurrentSongHudWidget.COVER_VISIBILITY_REASON)) {
      boolean showCover = this.hudWidget.getConfig().showCover().get();
      if (showCover) {
        this.removeId("no-cover");
      } else {
        this.addId("no-cover");
      }
    }
  }

  private void updateTrack(CurrentSong currentSong) {
    if (this.trackWidget == null || this.artistWidget == null) {
      return;
    }

    this.trackWidget.setComponent(Component.text(currentSong == null ? "Not playing" : currentSong.getTitle()));
    this.artistWidget.setComponent(
        Component.text(currentSong == null ? "Click to retry" : currentSong.getArtist())
    );

    this.artistWidget.setVisible(true);

    if (currentSong == null) {
      this.controlsWidget.setVisible(false);
      return;
    }

    this.controlsWidget.setVisible(true);

    Icon icon;
    if(this.hudWidget.addon().currentSongService().getCurrentSong() != null) {
      icon = Icon.url(this.hudWidget.addon().currentSongService().getCurrentSong().getImageUrl());
    } else {
      icon = Icon.texture(ResourceLocation.create("evilradio", "texture/logo.png"));
    }
    this.coverWidget.icon().set(icon);
  }

}
