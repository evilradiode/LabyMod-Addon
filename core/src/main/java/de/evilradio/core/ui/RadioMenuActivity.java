package de.evilradio.core.ui;

import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.EvilRadioConfiguration;
import de.evilradio.core.api.RadioApiService;
import de.evilradio.core.radio.RadioManager;
import de.evilradio.core.radio.RadioStream;
import net.labymod.api.Laby;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.AutoActivity;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.activity.Links;
import net.labymod.api.client.gui.screen.activity.types.SimpleActivity;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.key.InputType;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import java.util.List;

@AutoActivity
@Links({@Link("radio-menu.lss")})
public class RadioMenuActivity extends SimpleActivity {

  private RadioManager radioManager;
  private EvilRadioAddon addon;

  public RadioMenuActivity(EvilRadioAddon addon) {
    this.addon = addon;
    this.radioManager = addon.radioManager();
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);

    // Titel
    ComponentWidget titleWidget = ComponentWidget.component(
        Component.translatable("evilradio.menu.title").color(NamedTextColor.WHITE)
    );
    titleWidget.addId("title");
    this.document.addChild(titleWidget);

    // Beschreibungstext
    ComponentWidget descriptionWidget = ComponentWidget.component(
        Component.translatable("evilradio.menu.description").color(NamedTextColor.GRAY)
            .append(Component.text("\n", NamedTextColor.GRAY))
            .append(Component.translatable("evilradio.menu.description2").color(NamedTextColor.GRAY))
    );
    descriptionWidget.addId("description");
    this.document.addChild(descriptionWidget);

    // Hole aktuellen Stream für Song-Anzeige
    RadioStream currentStream = radioManager.getCurrentStream();

    // Container für Play/Pause und Volume Controls (oberhalb des stations-container)
    DivWidget controlsContainer = new DivWidget();
    controlsContainer.addId("controls-container");
    
    // Aktueller Song-Anzeige
    ComponentWidget currentSongWidget = ComponentWidget.component(
        Component.translatable("evilradio.menu.noSong").color(NamedTextColor.GRAY)
    );
    currentSongWidget.addId("current-song");
    controlsContainer.addChild(currentSongWidget);
    
    // Lade aktuellen Song, falls ein Stream aktiv ist
    if (currentStream != null && radioManager.isPlaying()) {
      RadioApiService.fetchCurrentSong(currentStream.getName(), (response) -> {
        if (response != null && response.getCurrent() != null) {
          String songText = response.getCurrent().getFormatted();
          if (songText.isEmpty()) {
            songText = response.getCurrentSong();
          }
          if (songText.isEmpty()) {
            Laby.labyAPI().minecraft().executeOnRenderThread(() -> {
              currentSongWidget.setComponent(Component.translatable("evilradio.menu.noSong").color(NamedTextColor.WHITE));
            });
            return;
          }
          final String finalSongText = songText;
          Laby.labyAPI().minecraft().executeOnRenderThread(() -> {
            currentSongWidget.setComponent(Component.text(finalSongText, NamedTextColor.WHITE));
          });
        }
      });
    }
    
    // Play/Pause Button
    boolean isPaused = radioManager.isPaused();
    boolean isPlaying = radioManager.isPlaying() && !isPaused;
    String playPauseText = isPlaying ? "⏸" : "▶";
    ButtonWidget playPauseButton = ButtonWidget.component(
        Component.text(playPauseText, NamedTextColor.WHITE)
    );
    playPauseButton.addId("play-pause-button");
    playPauseButton.setPressable(() -> {
      radioManager.togglePlayPause();
      this.reload();
    });
    controlsContainer.addChild(playPauseButton);
    
    // Volume Slider - verwende ConfigProperty aus der Konfiguration
    EvilRadioConfiguration config = addon.configuration();
    ConfigProperty<Float> volumeProperty = config.volume();
    
    // Synchronisiere den aktuellen Volume-Wert mit der ConfigProperty
    float currentVolumePercent = radioManager.getVolume();
    if (volumeProperty.get() != currentVolumePercent) {
      volumeProperty.set(currentVolumePercent);
    }
    
    // Volume Label - muss aktualisiert werden können
    ComponentWidget volumeLabel = ComponentWidget.component(
        Component.text(volumeProperty.get() + "%", NamedTextColor.GRAY)
    );
    volumeLabel.addId("volume-label");
    
    // Listener für Volume-Änderungen - aktualisiere auch das Label
    volumeProperty.addChangeListener((type, oldValue, newValue) -> {
      float volume = newValue / 100.0f;
      radioManager.setVolume(volume);
      // Aktualisiere das Label
      volumeLabel.setComponent(Component.text(newValue + "%", NamedTextColor.GRAY));
    });
    
    // Erstelle SliderWidget - in Activities muss die Property manuell verbunden werden
    // Da SliderWidget normalerweise nur in Config-Screens mit @SliderSetting funktioniert,
    // müssen wir die Property manuell aktualisieren, wenn der Slider geändert wird
    
    // Erstelle den SliderWidget - er sollte die Property automatisch verwenden
    // wenn sie mit @SliderSetting annotiert ist, aber das funktioniert nur in Config-Screens
    // Für Activities: Wir müssen die Property manuell verbinden über einen Workaround
    
    SliderWidget volumeSlider = new SliderWidget();
    volumeSlider.addId("volume-slider");
    
    // Versuche, die Property über Reflection zu setzen
    try {
      java.lang.reflect.Field propertyField = SliderWidget.class.getDeclaredField("property");
      propertyField.setAccessible(true);
      propertyField.set(volumeSlider, volumeProperty);
    } catch (Exception e) {
      // Fallback: Wenn Reflection nicht funktioniert, müssen wir einen anderen Ansatz verwenden
      // Der SliderWidget wird die Property automatisch verwenden, wenn sie mit @SliderSetting annotiert ist
      // Aber das funktioniert nur in Config-Screens, nicht in Activities
    }
    
    controlsContainer.addChild(volumeSlider);
    controlsContainer.addChild(volumeLabel);
    
    this.document.addChild(controlsContainer);

    // Container für die Station-Buttons
    DivWidget stationsContainer = new DivWidget();
    stationsContainer.addId("stations-container");
    
    // Erstelle Buttons für jeden Stream in einem 2x4 Grid
    List<RadioStream> streams = this.addon.radioStreamService().streams();
    
    for (int i = 0; i < streams.size() && i < 8; i++) {
      RadioStream stream = streams.get(i);
      boolean isActive = currentStream != null && currentStream.equals(stream) && radioManager.isPlaying();
      boolean isComingSoon = stream.getUrl() == null || stream.getUrl().isEmpty();
      
      // Button als DivWidget - direkt zum Container hinzufügen
      DivWidget stationButton = new DivWidget();
      stationButton.addId("station-button-" + i);
      
      if (isActive) {
        stationButton.addId("active");
      }
      
      if (isComingSoon) {
        stationButton.addId("coming-soon");
      }
      
      // Icon für den Button
      Icon buttonIcon = stream.getIcon();
      if (buttonIcon == null) {
        buttonIcon = Icon.texture(ResourceLocation.create("evilradio", "textures/stations/comingsoon.png"));
      }
      
      // IconWidget als Hintergrund
      IconWidget iconWidget = new IconWidget(buttonIcon);
      iconWidget.addId("station-icon-" + i);
      stationButton.addChild(iconWidget);
      
      // Click-Handler
      if (!isComingSoon) {
        final RadioStream selectedStream = stream;
        stationButton.setPressable(() -> {
          radioManager.playStream(selectedStream);
          addon.currentSongService().fetchCurrentSong();
          
          // Hole Song-Informationen für den Toast
          RadioApiService.fetchCurrentSong(selectedStream.getName(), (response) -> {
            Component toastMessage;
            if (response != null && response.getCurrent() != null) {
              String songText = response.getCurrent().getFormatted();
              if (songText.isEmpty()) {
                songText = response.getCurrentSong();
              }
              if (!songText.isEmpty()) {
                // Zeige Sender und Song an
                toastMessage = Component.translatable("evilradio.menu.streamStartedWithSong", 
                    Component.text(selectedStream.getDisplayName()),
                    Component.text(songText)
                ).color(NamedTextColor.GREEN);
              } else {
                // Nur Sender anzeigen, wenn kein Song verfügbar ist
                toastMessage = Component.translatable("evilradio.menu.streamStarted", 
                    Component.text(selectedStream.getDisplayName())
                ).color(NamedTextColor.GREEN);
              }
            } else {
              // Nur Sender anzeigen, wenn keine Song-Informationen verfügbar sind
              toastMessage = Component.translatable("evilradio.menu.streamStarted", 
                  Component.text(selectedStream.getDisplayName())
              ).color(NamedTextColor.GREEN);
            }
            
            Laby.labyAPI().minecraft().executeOnRenderThread(() -> {
              Laby.labyAPI().minecraft().chatExecutor().displayClientMessage(toastMessage);
            });
          });
          
          // Menü neu laden, um aktiven Status zu aktualisieren
          this.reload();
        });
      }
      
      // Direkt zum Container hinzufügen
      stationsContainer.addChild(stationButton);
    }

    this.document.addChild(stationsContainer);

    // Schließen-Button
    ButtonWidget closeButton = ButtonWidget.component(
        Component.translatable("evilradio.menu.close").color(NamedTextColor.RED)
    );
    closeButton.addId("close-button");
    closeButton.setPressable(() -> {
      Laby.labyAPI().minecraft().executeOnRenderThread(() -> {
        Laby.labyAPI().minecraft().minecraftWindow().closeScreen();
      });
    });
    this.document.addChild(closeButton);
  }

  @Override
  public boolean keyPressed(Key key, InputType type) {
    if (key == Key.ESCAPE) {
      Laby.labyAPI().minecraft().executeOnRenderThread(() -> {
        Laby.labyAPI().minecraft().minecraftWindow().closeScreen();
      });
      return true;
    }
    return super.keyPressed(key, type);
  }
}
