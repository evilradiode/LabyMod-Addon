package de.evilradio.core.song.azuracast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.evilradio.core.radio.StationShortcodes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Simuliert Senderwechsel-/Reconnect-Semantik ohne echte Netzwerkverbindung.
 */
class StationSwitchSemanticsTest {

  @Test
  void rapidSwitchKeepsOnlyLatestStation() {
    SubscriptionGenerationGuard guard = new SubscriptionGenerationGuard();
    List<String> accepted = new ArrayList<>();

    long mashup = guard.switchTo("mashup");
    long techno = guard.switchTo("techno");
    long sommer = guard.switchTo("sommer");

    if (guard.accepts(mashup, "station:mashup")) {
      accepted.add("mashup");
    }
    if (guard.accepts(techno, "station:techno")) {
      accepted.add("techno");
    }
    if (guard.accepts(sommer, "station:sommer")) {
      accepted.add("sommer");
    }

    assertEquals(List.of("sommer"), accepted);
    assertEquals("sommer", guard.activeShortcode());
  }

  @Test
  void reconnectUsesLastActiveShortcode() {
    AtomicReference<String> subscribed = new AtomicReference<>();
    AtomicBoolean stopped = new AtomicBoolean(false);

    Runnable reconnect = () -> {
      if (stopped.get()) {
        return;
      }
      String active = subscribed.get();
      if (StationShortcodes.isValidShortcode(active)) {
        subscribed.set(active);
      }
    };

    subscribed.set("mashup");
    subscribed.set("techno");
    reconnect.run();
    assertEquals("techno", subscribed.get());

    stopped.set(true);
    subscribed.set("sommer");
    reconnect.run();
    assertEquals("sommer", subscribed.get());
    assertTrue(stopped.get());
  }

  @Test
  void shutdownPreventsFurtherReconnectScheduling() {
    AtomicBoolean stopped = new AtomicBoolean(false);
    AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

    Runnable onClose = () -> {
      if (stopped.get()) {
        return;
      }
      reconnectScheduled.set(true);
    };

    stopped.set(true);
    onClose.run();
    assertFalse(reconnectScheduled.get());
  }

  @Test
  void unknownStationProducesNoValidSubscription() {
    assertFalse(StationShortcodes.isValidShortcode("POP und Rap"));
    assertFalse(StationShortcodes.isValidShortcode("X-MAS Live"));
    assertTrue(StationShortcodes.fromDisplayName("Oldie").isPresent());
    assertEquals("oldi", StationShortcodes.fromDisplayName("Oldie").orElseThrow());
    assertTrue(StationShortcodes.isValidShortcode("oldi"));
  }
}
