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
    boolean volatilityCondition,
    boolean patternsCondition,
    boolean bullishRejection,
    MarketType marketType
  ) {

    public boolean shouldBuy() {
      return calculateBuyPoints() >= getBuyThreshold();
    }

    private double getBuyThreshold() {
      return switch (marketType) {
        case STRONG_UPTREND -> TradingConstants.BUY_THRESHOLD_STRONG_UPTREND;
        case WEAK_UPTREND -> TradingConstants.BUY_THRESHOLD_WEAK_UPTREND;
        case RANGE_BOUND -> TradingConstants.BUY_THRESHOLD_RANGE_BOUND;
        case WEAK_DOWNTREND -> TradingConstants.BUY_THRESHOLD_WEAK_DOWNTREND;
        case STRONG_DOWNTREND -> TradingConstants.BUY_THRESHOLD_STRONG_DOWNTREND;
        case HIGH_VOLATILITY -> TradingConstants.BUY_THRESHOLD_HIGH_VOLATILITY;
        case TREND_REVERSAL -> TradingConstants.BUY_THRESHOLD_TREND_REVERSAL;
      };
    }

    public double calculateBuyPoints() {
      double points = 0;

      switch (marketType) {
        case STRONG_DOWNTREND:
          // Em tendência forte de baixa, priorize sobrevendido e rejeição
          points += rsiCondition ? 1.8 : -1.0;
          points += bullishRejection ? 1.8 : -0.6;
          points += volumeCondition ? 1.6 : -0.5;
          points += priceCondition ? 1.5 : -0.4;
          points += patternsCondition ? 1.4 : -0.3;
          // Menos relevante em downtrend
          points += trendCondition ? 0.4 : 0;
          points += macdCondition ? 0.5 : 0;
          points += stochCondition ? 0.6 : 0;
          points += momentumCondition ? 0.7 : 0;
          points += volatilityCondition ? 0.3 : 0;
          break;

        case RANGE_BOUND:
          // Em mercado lateral, priorize suporte/resistência e volume
          points += priceCondition ? 2.0 : -1.0;
          points += rsiCondition ? 1.5 : -0.5;
          points += volumeCondition ? 1.3 : -0.3;
          points += stochCondition ? 1.2 : 0;
          points += bullishRejection ? 1.0 : -0.2;
          // Menos relevante em mercado lateral
          points += trendCondition ? 0.3 : 0;
          points += macdCondition ? 0.4 : 0;
          points += momentumCondition ? 0.5 : 0;
          points += volatilityCondition ? 0.6 : 0;
          points += patternsCondition ? 0.8 : 0;
          break;

        case TREND_REVERSAL:
          // Em reversão, priorize divergências e rejeição
          points += bullishRejection ? 2.0 : -0.8;
          points += rsiCondition ? 1.7 : -0.6;
          points += momentumCondition ? 1.6 : -0.5;
          points += patternsCondition ? 1.5 : -0.4;
          points += volumeCondition ? 1.4 : -0.3;
          points += volatilityCondition ? 0.9 : 0;
          points += stochCondition ? 0.8 : 0;
          points += priceCondition ? 0.7 : 0;
          points += macdCondition ? 0.6 : 0;
          points += trendCondition ? 0.5 : 0;
          break;

        default: // Uptrend ou padrão
          // Manter pesos originais
          points += rsiCondition ? 1.2 : -0.5;
          points += trendCondition ? 0.8 : -0.5;
          points += volumeCondition ? 1.5 : 0;
          points += priceCondition ? 1.3 : 0;
          points += macdCondition ? 1.1 : 0;
          points += stochCondition ? 1.0 : 0;
          points += momentumCondition ? 1.4 : 0;
          points += volatilityCondition ? 0.7 : 0;
          points += patternsCondition ? 1.3 : 0;
          points += bullishRejection ? 1.2 : -0.4;
      }


      return points;
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
    boolean patternsCondition,
    boolean bearishRejection,
    MarketType marketType
  ) {

    public boolean shouldSell() {
      if (stopLoss || takeProfit) return true;

      return calculateSellPoints() >= getSellThreshold();
    }

    private double getSellThreshold() {
      return switch (marketType) {
        case STRONG_UPTREND -> TradingConstants.SELL_THRESHOLD_STRONG_UPTREND;
        case WEAK_UPTREND -> TradingConstants.SELL_THRESHOLD_WEAK_UPTREND;
        case RANGE_BOUND -> TradingConstants.SELL_THRESHOLD_RANGE_BOUND;
        case WEAK_DOWNTREND -> TradingConstants.SELL_THRESHOLD_WEAK_DOWNTREND;
        case STRONG_DOWNTREND -> TradingConstants.SELL_THRESHOLD_STRONG_DOWNTREND;
        case HIGH_VOLATILITY -> TradingConstants.SELL_THRESHOLD_HIGH_VOLATILITY;
        case TREND_REVERSAL -> TradingConstants.SELL_THRESHOLD_TREND_REVERSAL;
      };
    }

    public double calculateSellPoints() {
      double points = 0;

      switch (marketType) {
        case STRONG_UPTREND:
          // Em uptrend forte, priorize resistência e rejeição de preço
          points += priceCondition ? 1.8 : -0.8;
          points += bearishRejection ? 1.6 : -0.6;
          points += rsiCondition ? 1.5 : -0.5;
          points += stochCondition ? 1.4 : -0.4;
          points += momentumCondition ? 1.2 : -0.3;
          points += volatilityCondition ? 0.8 : 0;
          points += trendCondition ? 0.6 : 0;
          points += macdCondition ? 0.5 : 0;
          points += volumeCondition ? 0.7 : 0;
          points += patternsCondition ? 1.0 : 0;
          break;

        case WEAK_UPTREND:
          // Em uptrend fraco, equilibre os indicadores
          points += momentumCondition ? 1.5 : -0.6;
          points += priceCondition ? 1.4 : -0.5;
          points += rsiCondition ? 1.3 : -0.4;
          points += trendCondition ? 1.2 : -0.3;
          points += stochCondition ? 1.1 : -0.2;
          points += bearishRejection ? 1.0 : -0.2;
          points += patternsCondition ? 0.9 : 0;
          points += macdCondition ? 0.8 : 0;
          points += volumeCondition ? 0.7 : 0;
          points += volatilityCondition ? 0.6 : 0;
          break;

        case RANGE_BOUND:
          // Em mercado lateral, priorize suporte/resistência
          points += priceCondition ? 1.8 : -0.8;
          points += stochCondition ? 1.6 : -0.6;
          points += rsiCondition ? 1.4 : -0.4;
          points += patternsCondition ? 1.2 : -0.2;
          points += bearishRejection ? 1.0 : 0;
          points += volumeCondition ? 0.8 : 0;
          points += macdCondition ? 0.6 : 0;
          points += momentumCondition ? 0.5 : 0;
          points += volatilityCondition ? 0.4 : 0;
          points += trendCondition ? 0.3 : 0;
          break;

        case STRONG_DOWNTREND:
        case WEAK_DOWNTREND:
          // Em downtrend, seja mais ágil para vender
          points += momentumCondition ? 1.6 : -0.4;
          points += trendCondition ? 1.5 : -0.3;
          points += macdCondition ? 1.4 : -0.2;
          points += volatilityCondition ? 1.3 : -0.1;
          points += rsiCondition ? 1.2 : 0;
          points += volumeCondition ? 1.1 : 0;
          points += priceCondition ? 1.0 : 0;
          points += stochCondition ? 0.9 : 0;
          points += patternsCondition ? 0.8 : 0;
          points += bearishRejection ? 0.7 : 0;
          break;

        case HIGH_VOLATILITY:
          // Em alta volatilidade, priorize proteção do capital
          points += volatilityCondition ? 1.8 : -0.6;
          points += momentumCondition ? 1.6 : -0.5;
          points += rsiCondition ? 1.4 : -0.4;
          points += bearishRejection ? 1.2 : -0.3;
          points += priceCondition ? 1.0 : -0.2;
          points += macdCondition ? 0.8 : 0;
          points += trendCondition ? 0.7 : 0;
          points += stochCondition ? 0.6 : 0;
          points += patternsCondition ? 0.5 : 0;
          points += volumeCondition ? 0.4 : 0;
          break;

        case TREND_REVERSAL:
          // Em reversão, priorize indicadores de momentum e padrões
          points += bearishRejection ? 1.8 : -0.7;
          points += patternsCondition ? 1.6 : -0.6;
          points += momentumCondition ? 1.4 : -0.5;
          points += rsiCondition ? 1.2 : -0.4;
          points += stochCondition ? 1.0 : -0.3;
          points += macdCondition ? 0.9 : -0.2;
          points += priceCondition ? 0.8 : -0.1;
          points += trendCondition ? 0.7 : 0;
          points += volatilityCondition ? 0.6 : 0;
          points += volumeCondition ? 0.5 : 0;
          break;

        default:
          // Configuração padrão
          points += rsiCondition ? 1.0 : -0.5;
          points += trendCondition ? 0.7 : -0.5;
          points += volumeCondition ? 0.6 : 0;
          points += priceCondition ? 0.7 : 0;
          points += macdCondition ? 0.5 : 0;
          points += stochCondition ? 0.5 : 0;
          points += momentumCondition ? 1.2 : 0;
          points += volatilityCondition ? 1.0 : 0;
          points += patternsCondition ? 1.1 : 0;
          points += bearishRejection ? 1.0 : -0.4;
      }

      return points;
    }
  }

}