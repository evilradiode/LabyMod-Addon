package de.evilradio.core.song.azuracast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SubscriptionGenerationGuardTest {

  @Test
  void stationSwitchIncrementsGenerationAndIgnoresStaleMessages() {
    SubscriptionGenerationGuard guard = new SubscriptionGenerationGuard();
    long mashupGen = guard.switchTo("mashup");
    assertTrue(guard.accepts(mashupGen, "station:mashup"));

    long technoGen = guard.switchTo("techno");
    assertFalse(guard.accepts(mashupGen, "station:mashup"));
    assertTrue(guard.accepts(technoGen, "station:techno"));

    long sommerGen = guard.switchTo("sommer");
    assertFalse(guard.accepts(mashupGen, "mashup"));
    assertFalse(guard.accepts(technoGen, "techno"));
    assertTrue(guard.accepts(sommerGen, "sommer"));
    assertEquals("sommer", guard.activeShortcode());
  }

  @Test
  void clearInvalidatesPreviousGeneration() {
    SubscriptionGenerationGuard guard = new SubscriptionGenerationGuard();
    long gen = guard.switchTo("mashup");
    long cleared = guard.clear();
    assertFalse(guard.accepts(gen, "station:mashup"));
    assertFalse(guard.accepts(cleared, "station:mashup"));
  }

  @Test
  void unknownOrBlankStationIsRejected() {
    SubscriptionGenerationGuard guard = new SubscriptionGenerationGuard();
    long gen = guard.switchTo("mashup");
    assertFalse(guard.accepts(gen, null));
    assertFalse(guard.accepts(gen, ""));
    assertFalse(guard.accepts(gen, "station:oldi"));
  }
}
