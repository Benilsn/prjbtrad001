package dev.prjbtrad001.app.core;

import lombok.Builder;

/**
 * Represents trading signals based on technical analysis conditions.
 * Uses a point-based system to determine buy and sell decisions.
 *
 * Points System:
 * - RSI condition: 1.0 point
 * - Trend condition: 1.0 point
 * - Volume condition: 0.4 point
 * - Price condition: 0.4 point
 *
 * Buy Signal:
 * - Triggered when total points >= BUY_THRESHOLD
 *
 * Sell Signal:
 * - Triggered when total points >= SELL_THRESHOLD
 * - OR when stopLoss is true
 * - OR when takeProfit is true
 *
 * @param rsiCondition    Relative Strength Index condition (oversold/overbought)
 * @param trendCondition  Market trend condition (bullish/bearish)
 * @param volumeCondition Trading volume condition
 * @param priceCondition  Price level condition (support/resistance)
 * @param stopLoss       Stop loss trigger
 * @param takeProfit     Take profit trigger
 */
@Builder
public record TradingSignals(
  boolean rsiCondition,
  boolean trendCondition,
  boolean volumeCondition,
  boolean priceCondition,
  boolean stopLoss,
  boolean takeProfit
) {

  public boolean shouldBuy() {
    return calculatePoints() >= TradingConstants.BUY_THRESHOLD;
  }

  public boolean shouldSell() {
    return calculatePoints() >= TradingConstants.SELL_THRESHOLD
      || stopLoss
      || takeProfit;
  }

  private double calculatePoints() {
    return
      (rsiCondition ? 1.0 : 0)
        + (trendCondition ? 1.0 : 0)
        + (volumeCondition ? 0.4 : 0)
        + (priceCondition ? 0.4 : 0);
  }
}