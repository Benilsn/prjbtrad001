package dev.prjbtrad001.paper;

import dev.prjbtrad001.domain.bot.TradeRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wallet is rebuilt from the trade ledger on every start. If this replay is
 * wrong, a restart quietly invents or destroys cash — which is exactly the bug
 * this replaced (balance snapped back to the initial amount while open
 * positions stayed in the database).
 */
class WalletReplayTest {

  private static final BigDecimal INITIAL = new BigDecimal("4000.00");

  private static TradeRecord rec(TradeRecord.Side side, String notional, String fee) {
    TradeRecord t = new TradeRecord();
    t.setSide(side);
    t.setNotionalBrl(new BigDecimal(notional));
    t.setFeeBrl(new BigDecimal(fee));
    return t;
  }

  @Test
  void noHistoryLeavesTheInitialBalance() {
    BigDecimal[] s = PaperWallet.replay(INITIAL, List.of());
    assertEquals(0, s[0].compareTo(INITIAL));
    assertEquals(0, s[1].compareTo(BigDecimal.ZERO));
  }

  /** The live situation that exposed the bug: two open buys, nothing sold. */
  @Test
  void openPositionsStayDebited() {
    BigDecimal[] s = PaperWallet.replay(INITIAL, List.of(
      rec(TradeRecord.Side.BUY, "100.00", "0.10"),
      rec(TradeRecord.Side.BUY, "100.00", "0.10")
    ));

    assertEquals(0, s[0].compareTo(new BigDecimal("3800.00")),
      "cash must stay debited while positions are open, got " + s[0]);
    assertEquals(0, s[1].compareTo(new BigDecimal("0.20")), "fees got " + s[1]);
  }

  @Test
  void profitableRoundTripCreditsTheGain() {
    BigDecimal[] s = PaperWallet.replay(INITIAL, List.of(
      rec(TradeRecord.Side.BUY, "100.00", "0.10"),
      rec(TradeRecord.Side.SELL, "120.00", "0.12")
    ));

    // 4000 - 100 + (120 - 0.12) = 4019.88
    assertEquals(0, s[0].compareTo(new BigDecimal("4019.88")), "got " + s[0]);
    assertEquals(0, s[1].compareTo(new BigDecimal("0.22")));
  }

  @Test
  void losingRoundTripReducesTheBalance() {
    BigDecimal[] s = PaperWallet.replay(INITIAL, List.of(
      rec(TradeRecord.Side.BUY, "100.00", "0.10"),
      rec(TradeRecord.Side.SELL, "80.00", "0.08")
    ));

    assertTrue(s[0].compareTo(INITIAL) < 0, "a loss must leave less cash, got " + s[0]);
    assertEquals(0, s[0].compareTo(new BigDecimal("3979.92")), "got " + s[0]);
  }

  @Test
  void toleratesMissingAmounts() {
    TradeRecord broken = new TradeRecord();
    broken.setSide(TradeRecord.Side.BUY);
    assertDoesNotThrow(() -> PaperWallet.replay(INITIAL, List.of(broken)));
  }
}
