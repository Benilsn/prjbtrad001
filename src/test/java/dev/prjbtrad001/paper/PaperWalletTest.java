package dev.prjbtrad001.paper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Slippage has to be adverse on BOTH sides. If it ever flips sign the paper
 * account starts getting fills nobody could obtain, and every result built on
 * top of it becomes optimistic — the exact failure we already had once with the
 * stale candle price.
 */
class PaperWalletTest {

  private PaperWallet wallet;

  @BeforeEach
  void setUp() {
    wallet = new PaperWallet();
    wallet.initialBalance = new BigDecimal("4000.00");
    wallet.feeRate = new BigDecimal("0.001");
    wallet.slippageRate = new BigDecimal("0.0005");
    wallet.init();
  }

  @Test
  void buyFillsAboveTheQuote() {
    BigDecimal quote = new BigDecimal("100.00");
    BigDecimal fill = wallet.fillPrice(quote, true);

    assertTrue(fill.compareTo(quote) > 0, "a market buy must lift the ask");
    assertEquals(0, fill.compareTo(new BigDecimal("100.05000000")), "expected +0.05%, got " + fill);
  }

  @Test
  void sellFillsBelowTheQuote() {
    BigDecimal quote = new BigDecimal("100.00");
    BigDecimal fill = wallet.fillPrice(quote, false);

    assertTrue(fill.compareTo(quote) < 0, "a market sell must hit the bid");
    assertEquals(0, fill.compareTo(new BigDecimal("99.95000000")), "expected -0.05%, got " + fill);
  }

  @Test
  void roundTripAtAFlatPriceLosesFeesAndSlippage() {
    BigDecimal quote = new BigDecimal("100.00");
    BigDecimal notional = new BigDecimal("100.00");

    BigDecimal buyFill = wallet.fillPrice(quote, true);
    BigDecimal buyFee = wallet.feeOn(notional);
    BigDecimal qty = notional.subtract(buyFee)
      .divide(buyFill, 8, java.math.RoundingMode.HALF_UP);

    BigDecimal sellFill = wallet.fillPrice(quote, false);
    BigDecimal proceeds = qty.multiply(sellFill);
    BigDecimal net = proceeds.subtract(wallet.feeOn(proceeds));

    // Buying and selling at the same quote must LOSE money: ~0.2% fees + ~0.1%
    // slippage. A round trip that breaks even would mean the costs vanished.
    assertTrue(net.compareTo(notional) < 0,
      "round trip should cost money, got net " + net);
    BigDecimal costPct = notional.subtract(net)
      .divide(notional, 6, java.math.RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));
    assertTrue(costPct.doubleValue() > 0.25 && costPct.doubleValue() < 0.35,
      "expected ~0.3% total cost, got " + costPct + "%");
  }

  @Test
  void debitRefusesWhenShort() {
    assertFalse(wallet.debit(new BigDecimal("5000.00")), "must not spend money it does not have");
    assertTrue(wallet.debit(new BigDecimal("100.00")));
    assertEquals(0, wallet.getBalance().compareTo(new BigDecimal("3900.00")));
  }
}
