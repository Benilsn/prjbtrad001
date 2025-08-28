package dev.prjbtrad001.app.core;

import lombok.Builder;

public record TradingSignals() {

  @Builder
  public record Bullish(
    boolean rsiCondition,
    boolean trendCondition,
    boolean volumeCondition,
    boolean priceCondition,
    boolean macdCondition,
    boolean stochCondition,
    boolean momentumCondition,
    boolean volatilityCondition
  ) {

    public boolean shouldBuy() {
      return calculateBuyPoints() >= TradingConstants.BUY_THRESHOLD;
    }

    public double calculateBuyPoints() {
      return (rsiCondition ? 1.2 : 0)
        + (trendCondition ? 0.8 : 0)
        + (volumeCondition ? 1.5 : 0)
        + (priceCondition ? 1.3 : 0)
        + (macdCondition ? 1.1 : 0)
        + (stochCondition ? 1.0 : 0)
        + (momentumCondition ? 1.4 : 0)
        + (volatilityCondition ? 0.7 : 0);
    }

  }

  @Builder
  public record Bearish(
    boolean rsiCondition,
    boolean trendCondition,
    boolean volumeCondition,
    boolean priceCondition,
    boolean macdCondition,
    boolean stochCondition,
    boolean momentumCondition,
    boolean volatilityCondition,
    boolean stopLoss,
    boolean takeProfit,
    boolean minimumProfitReached
  ) {

    public boolean shouldSell() {
      if (stopLoss || takeProfit) {
        return true;
      }

      if (!minimumProfitReached) {
        return false;
      }

      return calculateSellPoints() >= TradingConstants.SELL_THRESHOLD;
    }


    public double calculateSellPoints() {
      return (rsiCondition ? 1.0 : 0)
        + (trendCondition ? 0.7 : 0)
        + (volumeCondition ? 0.6 : 0)
        + (priceCondition ? 0.7 : 0)
        + (macdCondition ? 0.5 : 0)
        + (stochCondition ? 0.5 : 0)
        + (momentumCondition ? 1.2 : 0)
        + (volatilityCondition ? 1.0 : 0);
    }

  }


}