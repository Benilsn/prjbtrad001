package dev.prjbtrad001.app.core;

import lombok.Builder;

import java.math.BigDecimal;

import static dev.prjbtrad001.app.utils.LogUtils.log;

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
  boolean extremeRsi,
  boolean extremeLowVolume,
  boolean strongDowntrend,
  boolean emergencyExit,
  boolean minimumProfitReached
) {

  @Builder
  public TradingSignals(
    boolean rsiCondition,
    boolean trendCondition,
    boolean volumeCondition,
    boolean priceCondition,
    boolean momentumCondition,
    boolean volatilityCondition,
    boolean stopLoss,
    boolean takeProfit,
    boolean emergencyExit,
    boolean minimumProfitReached
  ) {
    this(rsiCondition, trendCondition, volumeCondition, priceCondition,
      momentumCondition, volatilityCondition, stopLoss, takeProfit,
      false, false, false, emergencyExit, minimumProfitReached);
  }

  public boolean shouldBuy() {
    if (extremeRsi || extremeLowVolume || strongDowntrend) {
      return false;
    }

    double points = calculateBuyPoints();
    return points >= TradingConstants.BUY_THRESHOLD && hasMinimumRequiredConditions();
  }

  public boolean shouldSell() {
    boolean emergencyCondition = stopLoss || emergencyExit || takeProfit;

    if (!emergencyCondition && !minimumProfitReached) {
      return false;
    }

    if (emergencyCondition) {
      return true;
    }

    double points = calculateSellPoints();
    return points >= TradingConstants.SELL_THRESHOLD;
  }

  private boolean hasMinimumRequiredConditions() {
    int positiveSignals = (rsiCondition ? 1 : 0) +
      (trendCondition ? 1 : 0) +
      (volumeCondition ? 1 : 0) +
      (priceCondition ? 1 : 0) +
      (momentumCondition ? 1 : 0);

    return priceCondition && positiveSignals >= 3;
  }

  private double calculateBuyPoints() {
    double basePoints = (rsiCondition ? 1.3 : 0)
      + (trendCondition ? 1.0 : 0)
      + (volumeCondition ? 0.6 : 0)
      + (priceCondition ? 0.8 : 0)
      + (momentumCondition ? 0.5 : 0)
      + (volatilityCondition ? 0.3 : 0);

    double penalties = 0;

    if (rsiCondition && !trendCondition) penalties += 0.4;
    if (trendCondition && !volumeCondition) penalties += 0.3;
    if (!momentumCondition) penalties += 0.3;

    return Math.max(0, basePoints - penalties);
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