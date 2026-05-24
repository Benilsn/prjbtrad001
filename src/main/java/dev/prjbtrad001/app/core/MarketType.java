package dev.prjbtrad001.app.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Market classification for swing trading.
 *
 * Simplified from the original 7-state model.
 * The primary filter is EMA200 (macro trend), then momentum overlays.
 */
public enum MarketType {

  /** All EMAs aligned up, price above EMA200 — ride the trend */
  STRONG_UPTREND,

  /** Price above EMA200 but momentum slowing or correcting */
  WEAK_UPTREND,

  /** Price roughly flat, bounded between clear support and resistance */
  RANGE_BOUND,

  /** Price below EMA200, selling pressure — avoid long entries */
  WEAK_DOWNTREND,

  /** All EMAs aligned down, price below EMA200 — do not buy */
  STRONG_DOWNTREND,

  /** ATR spike / wide Bollinger bands — wait for calm */
  HIGH_VOLATILITY;

  public static MarketType classifyMarket(MarketConditions c) {
    // ── 1. Volatility gate (always first) ────────────────────────
    BigDecimal pricePercent = c.volatility()
      .divide(c.currentPrice(), 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));
    if (pricePercent.compareTo(BigDecimal.valueOf(3.0)) > 0) {
      return HIGH_VOLATILITY;
    }

    BigDecimal bandWidth = c.bollingerUpper().subtract(c.bollingerLower())
      .divide(c.bollingerMiddle(), 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));
    if (bandWidth.compareTo(BigDecimal.valueOf(8.0)) > 0) {
      return HIGH_VOLATILITY;
    }

    // ── 2. Primary trend: EMA200 (the swing trader's north star) ─
    boolean aboveEma200 = c.currentPrice().compareTo(c.ema200()) > 0;
    boolean ema50AboveEma200 = c.ema50().compareTo(c.ema200()) > 0;

    // ── 3. Medium-term structure ──────────────────────────────────
    boolean ema21AboveEma50 = c.ema21().compareTo(c.ema50()) > 0;
    boolean ema8AboveEma21  = c.ema8().compareTo(c.ema21()) > 0;

    // ── 4. Momentum ───────────────────────────────────────────────
    boolean positiveMacdHistogram = c.macdHistogram().compareTo(BigDecimal.ZERO) > 0;
    boolean strongPositiveMomentum = c.momentum().compareTo(BigDecimal.valueOf(1.5)) > 0;
    boolean strongNegativeMomentum = c.momentum().compareTo(BigDecimal.valueOf(-1.5)) < 0;

    // ── 5. Classification ─────────────────────────────────────────

    // Strong uptrend: everything aligned bullish
    if (aboveEma200 && ema50AboveEma200 && ema21AboveEma50 && ema8AboveEma21
      && (positiveMacdHistogram || strongPositiveMomentum)) {
      return STRONG_UPTREND;
    }

    // Strong downtrend: everything aligned bearish
    if (!aboveEma200 && !ema50AboveEma200 && !ema21AboveEma50 && !ema8AboveEma21
      && (!positiveMacdHistogram || strongNegativeMomentum)) {
      return STRONG_DOWNTREND;
    }

    // Weak uptrend: above EMA200 but mixed short-term signals
    if (aboveEma200 && ema50AboveEma200) {
      return WEAK_UPTREND;
    }

    // Weak downtrend: below EMA200 but not in free-fall
    if (!aboveEma200 && !ema50AboveEma200) {
      return WEAK_DOWNTREND;
    }

    // Mixed — EMAs not aligned, price chopping
    return RANGE_BOUND;
  }

  /** Returns true if the market state permits new long entries */
  public boolean allowsLongEntry() {
    return this == STRONG_UPTREND || this == WEAK_UPTREND || this == RANGE_BOUND;
  }

  /** Returns true if we should not open positions (risk too high) */
  public boolean isNoTrade() {
    return this == HIGH_VOLATILITY || this == STRONG_DOWNTREND;
  }
}
