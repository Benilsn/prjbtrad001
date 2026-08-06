package dev.prjbtrad001.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BotOrchestratorTest {

  /** The old code collapsed hours and days into minutes; guard against it. */
  @Test
  void mapsTimeframesToSeconds() {
    assertEquals(900, BotOrchestrator.timeframeSeconds("15m"));
    assertEquals(3600, BotOrchestrator.timeframeSeconds("1h"));
    assertEquals(14400, BotOrchestrator.timeframeSeconds("4h"));
    assertEquals(86400, BotOrchestrator.timeframeSeconds("1d"));
    assertEquals(604800, BotOrchestrator.timeframeSeconds("1w"));
  }

  @Test
  void heartbeatStaysWithinSaneBand() {
    assertEquals(900, BotOrchestrator.pollSeconds("15m"));
    assertEquals(3600, BotOrchestrator.pollSeconds("1d"), "daily is capped at 1h");
    assertEquals(3600, BotOrchestrator.pollSeconds("1w"));
    assertTrue(BotOrchestrator.pollSeconds("15m") >= 60);
  }

  @Test
  void malformedTimeframeFallsBackInsteadOfThrowing() {
    assertEquals(3600, BotOrchestrator.timeframeSeconds("bogus"));
    assertEquals(3600, BotOrchestrator.timeframeSeconds(null));
    assertEquals(3600, BotOrchestrator.timeframeSeconds("d"));
  }

  /**
   * The close-aligned tick is what removes the entry delay, so the offset must
   * always land inside the next candle — never zero, never beyond one candle.
   */
  @Test
  void nextCloseIsAlwaysWithinOneCandle() {
    for (long tf : new long[]{900, 3600, 14400, 86400}) {
      long s = BotOrchestrator.secondsUntilNextClose(tf);
      assertTrue(s > 0, "must be in the future for tf=" + tf + ", got " + s);
      assertTrue(s <= tf, "must not exceed one candle for tf=" + tf + ", got " + s);
    }
  }
}
