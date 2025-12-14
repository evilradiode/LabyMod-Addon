package de.evilradio.core.hudwidget.widget;

import de.evilradio.core.EvilTextures;
import de.evilradio.core.EvilTextures.SpriteControls;
import de.evilradio.core.hudwidget.CurrentSongHudWidget;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.song.CurrentSong;
import net.labymod.api.Laby;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.TextColor;
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
  private ComponentWidget liveStatusWidget;
  private ComponentWidget trackWidget;
  private ComponentWidget artistWidget;

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
    boolean showCover = this.hudWidget.getConfig().showCover().get();
    this.coverWidget.setVisible(showCover);

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

    // Zeile 1: Stream-Name
    this.streamWidget = ComponentWidget.empty();
    text.addChild(this.streamWidget);

    // Zeile 2: Live Status
    this.liveStatusWidget = ComponentWidget.empty();
    text.addChild(this.liveStatusWidget);

    // Zeile 2: Track-Titel
    this.trackWidget = ComponentWidget.empty();
    text.addChild(this.trackWidget);

    // Zeile 3: Artist
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
        // Benutzer stoppt das Radio manuell
        this.hudWidget.addon().radioManager().stopStream(true);
      } else {
        // Benutzer startet das Radio manuell - setze Flag zurück
        this.hudWidget.addon().setUserManuallyStopped(false);
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
        if (this.coverWidget != null) {
          this.coverWidget.setVisible(true);
        }
      } else {
        this.addId("no-cover");
        if (this.coverWidget != null) {
          this.coverWidget.setVisible(false);
        }
      }
    }
  }

  private void updateTrack(CurrentSong currentSong) {
    if (this.trackWidget == null || this.artistWidget == null || this.streamWidget == null) {
      return;
    }

    // Prüfe, ob der Stream läuft, auch wenn currentSong noch null ist
    boolean isPlaying = this.hudWidget.addon().radioManager().isPlaying();
    RadioStream currentStream = this.hudWidget.addon().radioManager().getCurrentStream();

    if(currentSong == null) {
      if (isPlaying && currentStream != null) {
        if (currentStream.getName() != null) {
          this.streamWidget.setComponent(Component.text("EvilRadio - " + currentStream.getName()));
        } else {
          this.streamWidget.setComponent(Component.translatable("evilradio.widget.loading"));
        }
        this.liveStatusWidget.setComponent(Component.text(""));
        this.liveStatusWidget.setVisible(false);
        this.trackWidget.setComponent(Component.translatable("evilradio.widget.loading"));
        this.artistWidget.setComponent(Component.translatable("evilradio.widget.fetchingSongInfo"));
      } else {
        // Kein Stream ausgewählt - Widgets leer lassen (Widget wird durch isVisibleInGame() versteckt)
        this.streamWidget.setComponent(Component.text(""));
        this.liveStatusWidget.setComponent(Component.text(""));
        this.liveStatusWidget.setVisible(false);
        this.trackWidget.setComponent(Component.text(""));
        this.artistWidget.setComponent(Component.text(""));
      }
      this.coverWidget.icon().set(EvilTextures.LOGO);
      this.controlsWidget.setVisible(false);
      return;
    }

    boolean onAir = currentSong.isOnAir();
    boolean twitch = currentSong.isTwitch();

    // Zeile 1: Stream-Name (z.B. "EvilRadio - Mashup") - Grau für dezente Anzeige
    if (currentStream != null && currentStream.getName() != null) {
      this.streamWidget.setComponent(Component.text("EvilRadio - " + currentStream.getName()).color(NamedTextColor.GRAY));
    } else {
      this.streamWidget.setComponent(Component.text(""));
    }

    // Prüfe Twitch-Status (nur für Mashup)
    String streamName = currentStream != null ? currentStream.getName() : null;
    boolean isMashup = streamName != null && streamName.equalsIgnoreCase("Mashup");

    // Zeile 2: On Air Badge (rot) mit optionalem Moderator-Name (weiß) und Twitch-Status (nur für Mashup)
    Component onAirComponent = Component.text("");
    if(isMashup && onAir) {
      onAirComponent = Component.text("● ON AIR").color(NamedTextColor.RED);
    }

    // Füge Twitch-Status hinzu, wenn aktiv und Stream ist Mashup
    if (isMashup && twitch) {
      TextColor twitchColor = TextColor.color(145, 70, 255); // #9146ff
      onAirComponent = onAirComponent.append(Component.text(" | ").color(NamedTextColor.GRAY))
          .append(Component.text("● TWITCH").color(twitchColor));
    }

    if (onAir && isMashup && currentSong.getModeratorName() != null && !currentSong.getModeratorName().isEmpty()) {
      onAirComponent = onAirComponent.append(Component.text(" | " + currentSong.getModeratorName()).color(NamedTextColor.WHITE));
    }
    this.liveStatusWidget.setComponent(onAirComponent);

    // Zeile 3: Track-Titel (bereinigt) - Weiß für prominente Anzeige
    this.trackWidget.setComponent(Component.text(currentSong.getTitle()).color(NamedTextColor.WHITE));

    // Zeile 4: Artist (bereinigt) - Grau für sekundäre Info
    this.artistWidget.setComponent(Component.text(currentSong.getArtist()).color(NamedTextColor.GRAY));

    this.streamWidget.setVisible(true);
    this.liveStatusWidget.setVisible(true);
    this.trackWidget.setVisible(true);
    this.artistWidget.setVisible(true);
    this.coverWidget.icon().set(Icon.url(currentSong.getImageUrl()));
    this.controlsWidget.setVisible(true);
  }

}