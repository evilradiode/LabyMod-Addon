package de.evilradio.core.activity.widget;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.EvilTextures.SpriteControls;
import de.evilradio.core.radio.RadioManager;
import de.evilradio.core.song.CurrentSong;
import net.labymod.api.client.gui.HorizontalAlignment;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.HorizontalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;

@Link("widget/auto-complete.lss")
@AutoWidget
public class AutoCompleteWidget extends FlexibleContentWidget {

  private String iconUrl;
  private String artist;
  private String title;
  private String album;
  private String releaseDate;
  private long durationMs;
  private String previewUrl;

  private ButtonWidget previewButton;
  private boolean previewPlaying;

  public AutoCompleteWidget(String iconUrl, String artist, String title, String album,
      String releaseDate, long durationMs, String previewUrl) {
    this.iconUrl = iconUrl;
    this.artist = artist;
    this.title = title;
    this.album = album;
    this.releaseDate = releaseDate;
    this.durationMs = durationMs;
    this.previewUrl = previewUrl;
    // Wie ButtonWidget erst beim Loslassen auslösen – sonst greift die Auswahl beim Drücken,
    // bevor der Preview-Button (Kind) sein Release bekommt
    this.setPressOnRelease(true);
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);

    HorizontalListWidget content = new HorizontalListWidget().addId("autocomplete-content");

    IconWidget iconWidget = new IconWidget(Icon.url(this.iconUrl)).addId("autocomplete-icon");
    content.addEntry(iconWidget);

    FlexibleContentWidget text = new FlexibleContentWidget().addId("autocomplete-text");

    ComponentWidget interpretAndTitle = ComponentWidget.text(this.title + " - " + this.artist).addId("autocomplete-interpret-and-title");
    text.addContent(interpretAndTitle);

    ComponentWidget titleDetails = ComponentWidget.text(CurrentSong.formatTime(this.durationMs / 1000) + (album != null && !album.isEmpty() ? " × " + this.album : "") + ((releaseDate != null && !releaseDate.isEmpty()) ? " × " + this.releaseDate : "")).addId("autocomplete-title-details");
    text.addContent(titleDetails);

    content.addEntry(text);

    if (this.previewUrl != null && !this.previewUrl.isEmpty()) {
      this.previewPlaying = this.radioManager().isPreviewPlaying(this.previewUrl);
      this.previewButton = ButtonWidget.icon(this.previewPlaying ? SpriteControls.STOP : SpriteControls.PLAY)
          .addId("autocomplete-preview-button");
      this.previewButton.setPressable(() -> this.radioManager().togglePreview(this.previewUrl));
      content.addEntry(this.previewButton).alignment().set(HorizontalAlignment.RIGHT);
    }

    this.addContent(content);

  }

  @Override
  public boolean onPress() {
    // Klick auf den Preview-Button soll den Song nicht übernehmen (mehrere vorhören möglich)
    if (this.previewButton != null && this.previewButton.isHovered()) {
      return true;
    }
    return super.onPress();
  }

  @Override
  public void tick() {
    super.tick();
    if (this.previewButton == null) return;

    // Icon nachziehen, wenn die Preview endet oder eine andere gestartet wird
    boolean playing = this.radioManager().isPreviewPlaying(this.previewUrl);
    if (playing != this.previewPlaying) {
      this.previewPlaying = playing;
      this.previewButton.updateIcon(playing ? SpriteControls.STOP : SpriteControls.PLAY);
    }
  }

  private RadioManager radioManager() {
    return EvilRadioAddon.instance().radioManager();
  }

  public String getIconUrl() {
    return iconUrl;
  }

  public String getArtist() {
    return artist;
  }

  public String getTitle() {
    return title;
  }

  public String getAlbum() {
    return album;
  }

  public String getReleaseDate() {
    return releaseDate;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public String getPreviewUrl() {
    return previewUrl;
  }
}
