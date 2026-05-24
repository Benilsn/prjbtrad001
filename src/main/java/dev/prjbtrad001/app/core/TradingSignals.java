package dev.prjbtrad001.app.core;

/**
 * Swing-trade signal evaluation.
 *
 * Design principles:
 *  - Fewer, higher-quality conditions rather than a floating-point scoring soup
 *  - Mandatory conditions (must all pass) + bonus conditions for confidence
 *  - Market type acts as a primary gate, not just a weight modifier
 */
public record TradingSignals() {

  // ────────────────────────────────────────────────────────────────
  //  BUY signal
  // ────────────────────────────────────────────────────────────────
  public record Bullish(
    /** EMA200 above — macro uptrend confirmed */
    boolean trendAboveEma200,
    /** RSI in the buy window (30–55): pulled back, not exhausted */
    boolean rsiInBuyWindow,
    /** MACD histogram flipped positive (momentum turning up) */
    boolean macdTurningUp,
    /** Price near a support level (EMA50, Bollinger lower, or structural low) */
    boolean priceAtSupport,
    /** Volume at least average — confirms the move */
    boolean volumeConfirmed,
    /** Candlestick pattern aligned bullish (hammer, engulf, morning star) */
    boolean bullishPattern,
    /** Risk/reward ratio looks favourable based on ATR and distance to resistance */
    boolean favourableRiskReward,
    MarketType marketType
  ) {

    public boolean shouldBuy() {
      // ── Hard gates: never buy into these ──────────────────────────
      if (marketType.isNoTrade()) return false;

      // ── Mandatory conditions (all required for a swing entry) ─────
      boolean mandatory = trendAboveEma200 && rsiInBuyWindow && favourableRiskReward;
      if (!mandatory) return false;

      // ── Need at least 2 of the 4 confirming signals ───────────────
      int confirmations = 0;
      if (macdTurningUp)   confirmations++;
      if (priceAtSupport)  confirmations++;
      if (volumeConfirmed) confirmations++;
      if (bullishPattern)  confirmations++;

      return confirmations >= 2;
    }

    public int confidenceScore() {
      if (!shouldBuy()) return 0;
      int score = 40; // base for passing mandatory gate
      if (macdTurningUp)   score += 15;
      if (priceAtSupport)  score += 20;
      if (volumeConfirmed) score += 15;
      if (bullishPattern)  score += 10;
      if (marketType == MarketType.STRONG_UPTREND) score += 10;
      return Math.min(score, 100);
    }
  }

  // ────────────────────────────────────────────────────────────────
  //  SELL signal
  // ────────────────────────────────────────────────────────────────
  public record Bearish(
    /** Hard stop loss — always sell regardless of other signals */
    boolean stopLoss,
    /** Hard take profit target reached */
    boolean takeProfit,
    /** RSI entered overbought territory (≥ 68) */
    boolean rsiOverbought,
    /** MACD histogram flipped negative (momentum turning down) */
    boolean macdTurningDown,
    /** Price touched or exceeded the identified resistance level */
    boolean priceAtResistance,
    /** Price at Bollinger upper band */
    boolean priceAtUpperBand,
    /** Bearish candlestick pattern (shooting star, engulf, evening star) */
    boolean bearishPattern,
    /** Trailing stop level was breached */
    boolean trailingStopHit,
    MarketType marketType
  ) {

    public boolean shouldSell() {
      // ── Hard exits: always execute ─────────────────────────────────
      if (stopLoss || takeProfit || trailingStopHit) return true;

      // ── Soft exits: need a combination ────────────────────────────
      // In downtrend, be more aggressive about exiting
      if (marketType == MarketType.STRONG_DOWNTREND || marketType == MarketType.WEAK_DOWNTREND) {
        return macdTurningDown || rsiOverbought || priceAtResistance;
      }

      // In uptrend, need at least 2 signals (let winners run)
      int signals = 0;
      if (rsiOverbought)      signals++;
      if (macdTurningDown)    signals++;
      if (priceAtResistance)  signals++;
      if (priceAtUpperBand)   signals++;
      if (bearishPattern)     signals++;

      return signals >= 2;
    }
  }
}
