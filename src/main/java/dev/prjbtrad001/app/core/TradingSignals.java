package dev.prjbtrad001.app.core;

import lombok.Builder;

/**
 * Represents trading signals based on technical analysis conditions.
 * Uses a point-based system to determine buy and sell decisions.
 * <p>
 * Points System:
 * - RSI condition: 1.0 point
 * - Trend condition: 1.0 point
 * - Volume condition: 0.4 point
 * - Price condition: 0.4 point
 * <p>
 * Buy Signal:
 * - Triggered when total points >= BUY_THRESHOLD
 * <p>
 * Sell Signal:
 * - Triggered when total points >= SELL_THRESHOLD
 * - OR when stopLoss is true
 * - OR when takeProfit is true
 *
 * @param rsiCondition    Relative Strength Index condition (oversold/overbought)
 * @param trendCondition  Market trend condition (bullish/bearish)
 * @param volumeCondition Trading volume condition
 * @param priceCondition  Price level condition (support/resistance)
 * @param stopLoss        Stop loss trigger
 * @param takeProfit      Take profit trigger
 */
@Builder
public record TradingSignals(
  boolean rsiCondition,
  boolean trendCondition,
  boolean volumeCondition,
  boolean priceCondition,
  boolean momentumCondition,
  boolean volatilityCondition,
  boolean stopLoss,
  boolean takeProfit,
  boolean bollingerBandCondition
) {

  public boolean shouldBuy() {
    double points = calculateBuyPoints();
    return points >= TradingConstants.BUY_THRESHOLD && hasMinimumRequiredConditions();
  }

  public boolean shouldSell() {
    double points = calculateSellPoints();
    return points >= TradingConstants.SELL_THRESHOLD || stopLoss || takeProfit;
  }

  private boolean hasMinimumRequiredConditions() {
    return rsiCondition || priceCondition || (volumeCondition && momentumCondition);
  }

  private double calculateBuyPoints() {
    return (rsiCondition ? 1.0 : 0)
      + (trendCondition ? 0.6 : 0)
      + (volumeCondition ? 1.0 : 0)
      + (priceCondition ? 1.2 : 0)
      + (momentumCondition ? 1.0 : 0)
      + (volatilityCondition ? 0.7 : 0)
      + (bollingerBandCondition ? 1.2 : 0);
  }

  private double calculateSellPoints() {
    return (rsiCondition ? 1.0 : 0)
      + (trendCondition ? 1.2 : 0)
      + (volumeCondition ? 0.3 : 0)
      + (priceCondition ? 0.6 : 0)
      + (momentumCondition ? 0.5 : 0)
      + (volatilityCondition ? 0.4 : 0);
  }
}