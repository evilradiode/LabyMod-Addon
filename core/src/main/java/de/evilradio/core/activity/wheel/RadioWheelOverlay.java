package de.evilradio.core.activity.wheel;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import de.evilradio.core.EvilRadioAddon;
import de.evilradio.core.radio.RadioManager;
import de.evilradio.core.radio.RadioStream;
import de.evilradio.core.activity.wheel.widget.RadioSegmentWidget;
import de.evilradio.core.activity.wheel.widget.RadioWheelWidget;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.activity.AutoActivity;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.activity.types.AbstractWheelInteractionOverlayActivity;
import net.labymod.api.client.gui.screen.activity.util.PageNavigator;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.key.MouseButton;
import net.labymod.api.client.gui.screen.widget.AbstractWidget;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.KeyEvent;
import net.labymod.api.event.client.input.MouseButtonEvent;
import net.labymod.api.event.client.input.MouseScrollEvent;
import net.labymod.api.client.gui.screen.widget.widgets.WheelWidget;
import net.labymod.api.util.CharSequences;
import net.labymod.api.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

@Link("activity/radio-wheel.lss")
@AutoActivity
public class RadioWheelOverlay extends AbstractWheelInteractionOverlayActivity {

  private final EvilRadioAddon addon;
  private final RadioManager radioManager;
  private boolean isWheelOpen = false;
  private long lastMiddleClickTime = 0;
  private static final long MIDDLE_CLICK_DEBOUNCE_MS = 200; // 200ms Debounce für Mittelklick

  public RadioWheelOverlay(EvilRadioAddon addon) {
    this.addon = addon;
    this.radioManager = addon.radioManager();
    // Event-Bus registrieren für Mouse-Events
    addon.labyAPI().eventBus().registerListener(this);
  }

  @Override
  protected void closeInteractionOverlay() {
    this.isWheelOpen = false;
    this.playStream(null, false);
    super.closeInteractionOverlay();
  }

  @Override
  protected void openInteractionOverlay() {
    this.isWheelOpen = true;
    super.openInteractionOverlay();
  }

  @Override
  protected Component createTitleComponent() {
    if (!this.hasEntries()) {
      return Component.translatable("evilradio.wheel.noStationsAvailable").color(NamedTextColor.DARK_RED);
    } else {
      // Immer den aktuellen Lautstärke-Wert aus der Konfiguration lesen
      float volume = this.addon.configuration().volume().get();
      int volumeInt = Math.round(volume);
      
      // Prüfe Play/Pause Status
      boolean isPlaying = this.radioManager.isPlaying() && !this.radioManager.isPaused();
      boolean isPaused = this.radioManager.isPaused();
      
      // Play/Pause Status mit Farbe
      Component playPauseStatus;
      if (isPlaying) {
        playPauseStatus = Component.text("▶ PLAY").color(NamedTextColor.GREEN);
      } else if (isPaused) {
        playPauseStatus = Component.text("⏸ PAUSE").color(NamedTextColor.RED);
      } else {
        playPauseStatus = Component.text("⏹ STOP").color(NamedTextColor.GRAY);
      }
      
      // Erste Zeile: Titel
      Component firstLine = Component.translatable("evilradio.wheel.selectStation").color(NamedTextColor.RED);
      
      // Zweite Zeile: Lautstärke und Play/Pause Status
      Component secondLine = Component.translatable("evilradio.wheel.volume", Component.text(String.valueOf(volumeInt))).color(NamedTextColor.YELLOW)
          .append(Component.text(" | ").color(NamedTextColor.GRAY))
          .append(playPauseStatus);
      
      // Dritte Zeile: Info über Mausrad und Mittelklick
      Component thirdLine = Component.translatable("evilradio.wheel.scrollInfo").color(NamedTextColor.GRAY);
      
      // Kombiniere alle drei Zeilen mit Zeilenumbrüchen
      Component title = firstLine
          .append(Component.text("\n", NamedTextColor.GRAY))
          .append(secondLine)
          .append(Component.text("\n", NamedTextColor.GRAY))
          .append(thirdLine);
      
      // Füge Shadow hinzu (durch Text-Decoration oder ähnliches)
      // In LabyMod kann man möglicherweise Shadow über CSS/LSS hinzufügen
      // Für jetzt verwenden wir einen einfachen Ansatz mit Text-Formatting
      return title;
    }
  }

  @Override
  protected boolean hasEntries() {
    return !this.addon.radioStreamService().streams().isEmpty();
  }

  @Override
  protected WheelWidget createWheelWidget() {
    RadioWheelWidget wheel = new RadioWheelWidget(
        () -> this.pageNavigator().getCurrentPage(),
        this::getSegmentCount
    );
    wheel.querySupplier(() -> {
      CharSequence searchText = this.getSearchText();
      return CharSequences.isEmpty(searchText) ? null : searchText;
    });

    wheel.setStreams(this.addon.radioStreamService().streams());

    wheel.segmentSupplier((index, wheelIndex, stream) -> {
      if (stream == null) {
        WheelWidget.Segment segment = new WheelWidget.Segment();
        segment.setSelectable(false);
        return segment;
      }

      RadioStream currentStream = this.radioManager.getCurrentStream();
      boolean isActive = currentStream != null && currentStream.equals(stream) && this.radioManager.isPlaying();

      RadioSegmentWidget segment = new RadioSegmentWidget(this.addon, stream, isActive);
      segment.addId("radio-wrapper");
      boolean isComingSoon = stream.getUrl() == null || stream.getUrl().isEmpty();
      
      if (isComingSoon) {
        segment.addId("coming-soon");
      }

      return segment;
    });

    return wheel;
  }

  @Override
  protected Key getKeyToOpen() {
    return this.addon.configuration().radioMenuKeybind().get();
  }

  @Override
  protected void onInitializeMappedKeys(Object2IntMap<Key> mappedKeys) {
    mappedKeys.put(Key.NUM1, 0);
    mappedKeys.put(Key.NUM2, 1);
    mappedKeys.put(Key.NUM3, 2);
    mappedKeys.put(Key.NUM4, 3);
    mappedKeys.put(Key.NUM5, 4);
    mappedKeys.put(Key.NUM6, 5);
    mappedKeys.put(Key.NUMPAD1, 0);
    mappedKeys.put(Key.NUMPAD2, 1);
    mappedKeys.put(Key.NUMPAD3, 2);
    mappedKeys.put(Key.NUMPAD4, 3);
    mappedKeys.put(Key.NUMPAD5, 4);
    mappedKeys.put(Key.NUMPAD6, 5);
  }

  @Override
  protected void onKey(Key key, KeyEvent.State state) {
    int mappedPosition = this.getMappedPosition(key);
    if (mappedPosition != Integer.MIN_VALUE) {
      RadioStream stream = this.findStreamByPosition(mappedPosition);
      if (stream != null) {
        this.playStream(stream, true);
      }
    }
  }

  @Override
  protected void renderInteractionOverlay(ScreenContext context) {
    // Versuche, den Titel-Widget direkt zu aktualisieren, falls vorhanden
    if (this.isWheelOpen && this.hasEntries()) {
      this.updateTitleWidgetIfPossible();
    }
  }

  /**
   * Versucht, den Titel-Widget direkt zu aktualisieren
   * Dies ist notwendig, da createTitleComponent() möglicherweise nur einmal aufgerufen wird
   */
  private void updateTitleWidgetIfPossible() {
    try {
      // Versuche, den Titel-Widget über Reflection zu finden
      // Dies ist ein Workaround, da die Basisklasse möglicherweise den Titel cached
      java.lang.reflect.Field[] fields = this.getClass().getSuperclass().getDeclaredFields();
      for (java.lang.reflect.Field field : fields) {
        field.setAccessible(true);
        Object value = field.get(this);
        if (value instanceof ComponentWidget componentWidget) {
          // Füge ID hinzu, falls noch nicht vorhanden, für besseres Styling
          if (!componentWidget.hasId("radio-wheel-title")) {
            componentWidget.addId("radio-wheel-title");
          }
          
          // Aktualisiere den Component mit dem aktuellen Wert
          float volume = this.addon.configuration().volume().get();
          int volumeInt = Math.round(volume);
          
          // Prüfe Play/Pause Status
          boolean isPlaying = this.radioManager.isPlaying() && !this.radioManager.isPaused();
          boolean isPaused = this.radioManager.isPaused();
          
          // Play/Pause Status mit Farbe
          Component playPauseStatus;
          if (isPlaying) {
            playPauseStatus = Component.text("▶ PLAY").color(NamedTextColor.GREEN);
          } else if (isPaused) {
            playPauseStatus = Component.text("⏸ PAUSE").color(NamedTextColor.RED);
          } else {
            playPauseStatus = Component.text("⏹ STOP").color(NamedTextColor.GRAY);
          }
          
          Component firstLine = Component.translatable("evilradio.wheel.selectStation").color(NamedTextColor.RED);
          
          Component secondLine = Component.translatable("evilradio.wheel.volume", Component.text(String.valueOf(volumeInt))).color(NamedTextColor.YELLOW)
              .append(Component.text(" | ").color(NamedTextColor.GRAY))
              .append(playPauseStatus);
          
          Component thirdLine = Component.translatable("evilradio.wheel.scrollInfo").color(NamedTextColor.GRAY);
          
          Component newTitle = firstLine
              .append(Component.text("\n", NamedTextColor.GRAY))
              .append(secondLine)
              .append(Component.text("\n", NamedTextColor.GRAY))
              .append(thirdLine);
          
          componentWidget.setComponent(newTitle);
          break;
        }
      }
    } catch (Exception e) {
      // Fehler beim Aktualisieren - ignoriere, da createTitleComponent() als Fallback dient
    }
  }

  /**
   * Event-Handler für Mausrad-Scroll
   * Ändert die Lautstärke in 5er-Schritten pro erkanntem Dreh, wenn das Wheel offen ist
   */
  @Subscribe
  public void onMouseScroll(MouseScrollEvent event) {
    // Nur verarbeiten, wenn das Wheel offen ist
    if (!this.isWheelOpen) {
      return;
    }

    // Prüfe, ob die Taste zum Öffnen des Wheels gedrückt gehalten wird
    Key openKey = this.getKeyToOpen();
    if (openKey == null) {
      return;
    }

    // Verhindere, dass das Event weiterverarbeitet wird
    event.setCancelled(true);

    // Ändere die Lautstärke basierend auf der Scroll-Richtung
    float currentVolume = this.addon.configuration().volume().get();
    double scrollDelta = event.delta();
    
    // Bestimme die Scroll-Richtung: positiv = nach oben, negativ = nach unten
    // Pro erkanntem Scroll-Event ändern wir die Lautstärke um genau 5%
    int direction = scrollDelta > 0 ? 1 : -1;
    float volumeChange = direction * 5.0f;
    
    // Berechne die neue Lautstärke
    float newVolume = currentVolume + volumeChange;
    newVolume = Math.max(0.0f, Math.min(100.0f, newVolume));
    
    // Runde auf den nächsten 5er-Schritt (0, 5, 10, 15, 20, ...)
    newVolume = Math.round(newVolume / 5.0f) * 5.0f;
    
    // Setze die neue Lautstärke
    this.addon.configuration().volume().set(newVolume);
  }

  /**
   * Event-Handler für Maus-Button-Klicks
   * Togglet Play/Pause bei Mittelklick, wenn das Wheel offen ist
   */
  @Subscribe
  public void onMouseButton(MouseButtonEvent event) {
    // Nur verarbeiten, wenn das Wheel offen ist
    if (!this.isWheelOpen) {
      return;
    }

    // Prüfe, ob es ein Mittelklick ist
    MouseButton button = event.button();
    if (button == MouseButton.MIDDLE) {
      long currentTime = System.currentTimeMillis();
      
      // Debounce: Verhindere mehrfache Auslösung bei einem Klick (Press + Release)
      if (currentTime - this.lastMiddleClickTime < MIDDLE_CLICK_DEBOUNCE_MS) {
        event.setCancelled(true);
        return;
      }
      
      this.lastMiddleClickTime = currentTime;
      
      // Verhindere, dass das Event weiterverarbeitet wird
      event.setCancelled(true);
      
      // Toggle Play/Pause
      if (this.radioManager.isPlaying() || this.radioManager.isPaused()) {
        this.radioManager.togglePlayPause();
      }
    }
  }

  @Override
  protected boolean shouldOpenInteractionMenu() {
    return this.addon.configuration().enabled().get();
  }

  @Override
  public void initialize(Parent parent) {
    this.refreshStreams();
    super.initialize(parent);
  }

  private void refreshStreams() {
    int maxPages = MathHelper.ceil((float) this.addon.radioStreamService().streams().size() / (float) this.getSegmentCount()) - 1;
    PageNavigator pageNavigator = this.pageNavigator();
    pageNavigator.setMaximumPage(maxPages);
    pageNavigator.setMinimumPage(0);
  }

  private RadioStream findStreamByPosition(int position) {
    int currentPage = this.pageNavigator().getCurrentPage();
    int pagePosition = currentPage * this.getSegmentCount() + position;
    List<RadioStream> streams = this.addon.radioStreamService().streams();
    return pagePosition >= streams.size() ? null : streams.get(pagePosition);
  }

  private void playStream(RadioStream forcedStream, boolean closeMenu) {
    RadioStream stream = forcedStream;
    if(forcedStream == null) {
      stream = this.findSelectedStream();
    }
    final RadioStream finalStream = stream;
    if (finalStream != null && finalStream.getUrl() != null && !finalStream.getUrl().isEmpty()) {
      this.radioManager.playStream(finalStream);
      this.addon.currentSongService().fetchCurrentSong();
      
      // Hole Song-Informationen für die Notification
      this.addon.currentSongService().fetchCurrentSong(finalStream.getName(), (currentSong) -> {
        Component notificationTitle;
        Component notificationText;
        Icon icon = null;
        
        if (currentSong != null) {
          String songText = currentSong.getFormatted();
          if (!songText.isEmpty()) {
            // Zeige Sender im Titel und Song im Text an
            notificationTitle = Component.translatable("evilradio.notification.streamSelected.titleWithStation", 
                Component.text(finalStream.getDisplayName())
            );
            notificationText = Component.translatable("evilradio.notification.streamSelected.textWithSong", 
                Component.text(songText)
            );
            icon = Icon.url(currentSong.getImageUrl());
          } else {
            // Nur Sender anzeigen, wenn kein Song verfügbar ist
            notificationTitle = Component.translatable("evilradio.notification.streamSelected.titleWithStation", 
                Component.text(finalStream.getDisplayName())
            );
            notificationText = Component.translatable("evilradio.notification.streamSelected.text");
          }
        } else {
          // Nur Sender anzeigen, wenn keine Song-Informationen verfügbar sind
          notificationTitle = Component.translatable("evilradio.notification.streamSelected.titleWithStation", 
              Component.text(finalStream.getDisplayName())
          );
          notificationText = Component.translatable("evilradio.notification.streamSelected.text");
        }
        
        this.addon.notification(notificationTitle, notificationText, icon, finalStream.getIcon());
      });
    }

    if (closeMenu) {
      this.closeInteraction();
    }
  }

  private @Nullable RadioStream findSelectedStream() {
    for(AbstractWidget<?> child : this.wheelWidget().getChildren()) {
      if (child instanceof RadioSegmentWidget radioSegmentWidget) {
        if (radioSegmentWidget.isSelectable() && radioSegmentWidget.isSegmentSelected()) {
          return radioSegmentWidget.getStream();
        }
      }
    }

    return null;
  }

}

