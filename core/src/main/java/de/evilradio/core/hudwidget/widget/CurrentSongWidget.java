package de.evilradio.core.hudwidget.widget;

import de.evilradio.core.EvilTextures;
import de.evilradio.core.EvilTextures.SpriteControls;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.song.CurrentSong;
import net.labymod.api.Laby;
import net.labymod.api.client.component.Component;
// import net.labymod.api.client.component.format.NamedTextColor; // TODO: Wird für OnAir Badge benötigt
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

@Link("widget/song-widget.lss")
@AutoWidget
public class CurrentSongWidget extends FlexibleContentWidget implements Updatable {

  private final CurrentSongHudWidget hudWidget;
  private ComponentWidget streamWidget;
  private ComponentWidget trackWidget;
  private ComponentWidget artistWidget;
  private ComponentWidget fourthLineWidget;
  private IconWidget coverWidget;
  private DivWidget controlsWidget;
  private IconWidget playPauseWidget;

  private final boolean editorContext;

  public CurrentSongWidget(CurrentSongHudWidget hudWidget, boolean editorContext) {
    this.hudWidget = hudWidget;
    this.editorContext = editorContext;
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);
    this.children.clear();

    if (this.editorContext) {
      this.addId("maximized");
    }

    if (!this.hudWidget.getConfig().showCover().get()) {
      this.addId("no-cover");
    }

    boolean leftAligned = this.hudWidget.anchor().isLeft();
    this.addId(leftAligned ? "left" : "right");

    this.coverWidget = new IconWidget(EvilTextures.LOGO);
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

    // Zeile 1: On Air Info (wenn aktiv) oder Stream-Name
    this.streamWidget = ComponentWidget.empty();
    text.addChild(this.streamWidget);

    // Zeile 2: Track-Titel
    this.trackWidget = ComponentWidget.empty();
    text.addChild(this.trackWidget);

    // Zeile 3: Artist
    this.artistWidget = ComponentWidget.empty();
    text.addChild(this.artistWidget);

    // Zeile 4: Wird nur angezeigt wenn On Air aktiv ist
    this.fourthLineWidget = ComponentWidget.empty();
    text.addChild(this.fourthLineWidget);

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
        this.hudWidget.addon().radioManager().stopStream();
      } else {
        this.hudWidget.addon().radioManager().playStream(this.hudWidget.addon().radioStreamService().getLastSelectedStream());
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

    // everything with the variable LARGE_PROGRESS_VISIBLE_KEY is an ugly hotfix for IDEA-16722. Revert the changes and you'll see
    if (!this.editorContext) {
      boolean isChatOpen = Laby.references().chatAccessor().isChatOpen();

      if (isChatOpen) {
        this.addId("maximized");
      } else {
        this.removeId("maximized");
      }
    }

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

    if (reason.equals(CurrentSongHudWidget.FOUR_LINES_REASON)) {
      this.reInitialize();
    }
  }

  private void updateTrack(CurrentSong currentSong) {
    if (this.trackWidget == null || this.artistWidget == null || this.streamWidget == null) {
      return;
    }

    // Prüfe, ob der Stream läuft, auch wenn currentSong noch null ist
    boolean isPlaying = this.hudWidget.addon().radioManager().isPlaying();
    
    if (currentSong == null) {
      // Wenn kein Song gefunden wurde, aber der Stream läuft, zeige "Loading..." an
      if (isPlaying) {
        RadioStream currentStream = this.hudWidget.addon().radioManager().getCurrentStream();
        if (currentStream != null && currentStream.getName() != null) {
          this.streamWidget.setComponent(Component.text("EvilRadio - " + currentStream.getName()));
        } else {
          this.streamWidget.setComponent(Component.translatable("evilradio.widget.loading"));
        }
        this.trackWidget.setComponent(Component.translatable("evilradio.widget.loading"));
        this.artistWidget.setComponent(Component.translatable("evilradio.widget.fetchingSongInfo"));
        this.fourthLineWidget.setComponent(Component.text(""));
        this.fourthLineWidget.setVisible(false);
        this.removeId("four-lines");
      } else {
        this.streamWidget.setComponent(Component.translatable("evilradio.widget.noStreamSelected"));
        this.trackWidget.setComponent(Component.translatable("evilradio.widget.notPlaying"));
        this.artistWidget.setComponent(Component.translatable("evilradio.widget.notPlaying"));
        this.fourthLineWidget.setComponent(Component.text(""));
        this.fourthLineWidget.setVisible(false);
        this.removeId("four-lines");
      }
    } else {
      boolean isOnAir = currentSong.isOnAir();
      
      // Zeige 4 Zeilen nur wenn On Air aktiv ist
      if (isOnAir) {
        this.addId("four-lines");
        
        // Zeile 1: On Air Badge mit Moderator-Name
        String onAirText = "● ON AIR";
        if (currentSong.getModeratorName() != null && !currentSong.getModeratorName().isEmpty()) {
          onAirText += " | " + currentSong.getModeratorName();
        }
        this.streamWidget.setComponent(Component.text(onAirText));
        
        // Zeile 2: Track-Titel (bereinigt)
        this.trackWidget.setComponent(Component.text(currentSong.getTitle()));
        
        // Zeile 3: Artist (bereinigt)
        this.artistWidget.setComponent(Component.text(currentSong.getArtist()));
        
        // Zeile 4: Leer (kann später für andere Infos verwendet werden)
        this.fourthLineWidget.setComponent(Component.text(""));
        this.fourthLineWidget.setVisible(true);
      } else {
        this.removeId("four-lines");
        
        // Normale 3-Zeilen-Ansicht
        RadioStream currentStream = this.hudWidget.addon().radioManager().getCurrentStream();
        if (currentStream != null && currentStream.getName() != null) {
          this.streamWidget.setComponent(Component.text("EvilRadio - " + currentStream.getName()));
        } else {
          this.streamWidget.setComponent(Component.translatable("evilradio.widget.noStreamSelected"));
        }
        this.trackWidget.setComponent(Component.text(currentSong.getTitle()));
        this.artistWidget.setComponent(Component.text(currentSong.getArtist()));
        this.fourthLineWidget.setComponent(Component.text(""));
        this.fourthLineWidget.setVisible(false);
      }
    }

    this.streamWidget.setVisible(true);
    this.trackWidget.setVisible(true);
    this.artistWidget.setVisible(true);

    Icon icon;
    if(currentSong != null) {
      icon = Icon.url(currentSong.getImageUrl());
    } else {
      icon = EvilTextures.LOGO;
    }
    this.coverWidget.icon().set(icon);

    if (currentSong == null) {
      this.controlsWidget.setVisible(false);
      return;
    }

    this.controlsWidget.setVisible(true);
  }

}
