package dev.prjbtrad001.app.core;

import lombok.Builder;

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
  boolean positionTimeout,
  boolean extremeRsi,
  boolean extremeLowVolume,
  boolean strongDowntrend,
  boolean emergencyExit,
  boolean minimumProfitReached,
  boolean priceRejection,
  boolean bollBandPressure,
  boolean consecutiveCandles
) {

  public boolean shouldBuy() {
    if (extremeRsi || extremeLowVolume || strongDowntrend) {
      return false;
    }

    double points = calculateBuyPoints();

    // Requer no mínimo 3 sinais positivos
    int positiveSignals = countPositiveSignals();

    return points >= TradingConstants.BUY_THRESHOLD && positiveSignals >= 3;
  }

  public boolean shouldSell() {
    // Condições de emergência são prioritárias
    if (stopLoss || emergencyExit || takeProfit || positionTimeout) {
      return true;
    }

    // Se não alcançou lucro mínimo, não vender por sinais técnicos
    if (!minimumProfitReached) {
      return false;
    }

    double points = calculateSellPoints();
    return points >= TradingConstants.SELL_THRESHOLD;
  }

  private int countPositiveSignals() {
    return (rsiCondition ? 1 : 0) +
      (trendCondition ? 1 : 0) +
      (volumeCondition ? 1 : 0) +
      (priceCondition ? 1 : 0) +
      (momentumCondition ? 1 : 0) +
      (priceRejection ? 1 : 0) +
      (bollBandPressure ? 1 : 0) +
      (consecutiveCandles ? 1 : 0);
  }

  private double calculateBuyPoints() {
    double basePoints = (rsiCondition ? 1.5 : 0)                // Aumentado peso do RSI
      + (trendCondition ? 1.2 : 0)                            // Aumentado peso da tendência
      + (volumeCondition ? 0.8 : 0)                           // Aumentado peso do volume
      + (priceCondition ? 0.9 : 0)                            // Aumentado peso do preço
      + (momentumCondition ? 0.7 : 0)                         // Aumentado peso do momentum
      + (volatilityCondition ? 0.4 : 0)                       // Aumentado peso da volatilidade
      + (priceRejection ? 0.8 : 0)                            // Novo indicador
      + (bollBandPressure ? 0.6 : 0)                          // Novo indicador
      + (consecutiveCandles ? 0.5 : 0);                       // Novo indicador

    // Penalidades mais severas para condições ausentes
    double penalties = 0;
    if (rsiCondition && !trendCondition) penalties += 0.6;
    if (trendCondition && !volumeCondition) penalties += 0.4;
    if (!momentumCondition) penalties += 0.3;
    if (!priceCondition && !priceRejection) penalties += 0.5;

    return Math.max(0, basePoints - penalties);
  }

  private double calculateSellPoints() {
    return (rsiCondition ? 1.3 : 0)                             // Aumentado
      + (trendCondition ? 1.4 : 0)                            // Aumentado
      + (volumeCondition ? 0.5 : 0)                           // Aumentado
      + (priceCondition ? 0.8 : 0)                            // Aumentado
      + (momentumCondition ? 0.7 : 0)                         // Aumentado
      + (volatilityCondition ? 0.5 : 0)                       // Aumentado
      + (priceRejection ? 0.6 : 0)                            // Novo
      + (bollBandPressure ? 0.5 : 0)                          // Novo
      + (consecutiveCandles ? 0.7 : 0);                       // Novo
  }
}