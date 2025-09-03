package dev.prjbtrad001.app.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum MarketType {
  STRONG_UPTREND,
  WEAK_UPTREND,
  RANGE_BOUND,
  WEAK_DOWNTREND,
  STRONG_DOWNTREND,
  HIGH_VOLATILITY,
  TREND_REVERSAL;

  public static MarketType classifyMarket(MarketConditions conditions) {
    // ANÁLISE DE TENDÊNCIA COM MÚLTIPLOS TIMEFRAMES
    boolean ema8AboveEma21 = conditions.ema8().compareTo(conditions.ema21()) > 0;
    boolean ema21AboveEma50 = conditions.ema21().compareTo(conditions.ema50()) > 0;
    boolean ema50AboveEma100 = conditions.ema50().compareTo(conditions.ema100()) > 0;
    boolean priceAboveEma50 = conditions.currentPrice().compareTo(conditions.ema50()) > 0;

    // Distância do preço às médias (em %)
    BigDecimal priceToEma50Distance = conditions.currentPrice().subtract(conditions.ema50())
      .divide(conditions.ema50(), 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100)).abs();

    boolean priceExtendedFromEma50 = priceToEma50Distance.compareTo(BigDecimal.valueOf(1.2)) > 0;

    // Indicadores de força da tendência
    boolean strongUptrend = ema8AboveEma21 && ema21AboveEma50 && ema50AboveEma100 && priceAboveEma50;
    boolean strongDowntrend = !ema8AboveEma21 && !ema21AboveEma50 && !ema50AboveEma100 && !priceAboveEma50;

    // ANÁLISE DE MOMENTUM
    boolean strongPositiveMomentum = conditions.momentum().compareTo(BigDecimal.valueOf(0.15)) > 0;
    boolean weakPositiveMomentum = conditions.momentum().compareTo(BigDecimal.valueOf(0.05)) > 0;
    boolean negativeMomentum = conditions.momentum().compareTo(BigDecimal.ZERO) < 0;
    boolean strongNegativeMomentum = conditions.momentum().compareTo(BigDecimal.valueOf(-0.15)) < 0;
    boolean momentumDivergenceUp = !weakPositiveMomentum && !strongNegativeMomentum;
    boolean momentumDivergenceDown = !negativeMomentum && !strongPositiveMomentum;

    // ANÁLISE DE RSI
    boolean rsiOverbought = conditions.rsi().compareTo(BigDecimal.valueOf(70)) > 0;
    boolean rsiStronglyOverbought = conditions.rsi().compareTo(BigDecimal.valueOf(80)) > 0;
    boolean rsiOversold = conditions.rsi().compareTo(BigDecimal.valueOf(30)) < 0;
    boolean rsiStronglyOversold = conditions.rsi().compareTo(BigDecimal.valueOf(20)) < 0;

    // RSI em zona média (indicando possível consolidação)
    boolean rsiNeutral = conditions.rsi().compareTo(BigDecimal.valueOf(45)) > 0 &&
      conditions.rsi().compareTo(BigDecimal.valueOf(55)) < 0;

    // ANÁLISE DE BANDAS DE BOLLINGER
    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower())
      .divide(conditions.bollingerMiddle(), 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));

    boolean veryTightBands = bandWidth.compareTo(BigDecimal.valueOf(1.5)) < 0;
    boolean tightBands = bandWidth.compareTo(BigDecimal.valueOf(2.5)) < 0;
    boolean wideBands = bandWidth.compareTo(BigDecimal.valueOf(4.0)) > 0;
    boolean veryWideBands = bandWidth.compareTo(BigDecimal.valueOf(6.0)) > 0;

    // Posição do preço nas bandas
    BigDecimal bandRange = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    BigDecimal relativePosition = conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandRange, 8, RoundingMode.HALF_UP);

    boolean atUpperBand = relativePosition.compareTo(BigDecimal.valueOf(0.95)) > 0;
    boolean nearUpperBand = relativePosition.compareTo(BigDecimal.valueOf(0.80)) > 0;
    boolean atLowerBand = relativePosition.compareTo(BigDecimal.valueOf(0.05)) < 0;
    boolean nearLowerBand = relativePosition.compareTo(BigDecimal.valueOf(0.20)) < 0;

    // Preço em torno da banda média (consolidação)
    boolean nearMiddleBand = relativePosition.compareTo(BigDecimal.valueOf(0.40)) > 0 &&
      relativePosition.compareTo(BigDecimal.valueOf(0.60)) < 0;

    // VOLATILIDADE
    boolean moderateVolatility = conditions.volatility().compareTo(BigDecimal.valueOf(1.5)) > 0;
    boolean highVolatility = conditions.volatility().compareTo(BigDecimal.valueOf(3.0)) > 0;
    boolean extremeVolatility = conditions.volatility().compareTo(BigDecimal.valueOf(5.0)) > 0;

    // DETECÇÃO DE REVERSÃO REFINADA PARA INTRADAY
    // Reversão de baixa para alta (compra)
    boolean potentialReversalUp = (!ema8AboveEma21 || !priceAboveEma50) && // Preço abaixo de médias importantes
      (rsiOversold || atLowerBand || nearLowerBand) &&                   // Indicador em zona de sobrevenda
      conditions.priceSlope().compareTo(BigDecimal.valueOf(-0.0005)) > 0; // Inclinação deixando de ser negativa

    // Reversão de alta para baixa (venda)
    boolean potentialReversalDown = (ema8AboveEma21 || priceAboveEma50) && // Preço acima de médias importantes
      (rsiOverbought || atUpperBand || nearUpperBand) &&                 // Indicador em zona de sobrecompra
      conditions.priceSlope().compareTo(BigDecimal.valueOf(0.0005)) < 0; // Inclinação deixando de ser positiva

    // Confirmação de reversão (temporário até implementar previousMomentum)
    boolean confirmingReversalUp = potentialReversalUp && momentumDivergenceUp;
    boolean confirmingReversalDown = potentialReversalDown && momentumDivergenceDown;

    // DETECÇÃO DE SQUEEZE E BREAKOUT
    boolean priceSqueezing = veryTightBands && rsiNeutral && Math.abs(conditions.momentum().doubleValue()) < 0.03;

    // Breakout potencial após squeeze
    boolean potentialBreakoutUp = tightBands && weakPositiveMomentum &&
      conditions.priceSlope().compareTo(BigDecimal.valueOf(0.001)) > 0;
    boolean potentialBreakoutDown = tightBands && negativeMomentum &&
      conditions.priceSlope().compareTo(BigDecimal.valueOf(-0.001)) < 0;

    // CLASSIFICAÇÃO DO MERCADO (PRIORIZADA PARA SWING TRADING INTRADAY)

    if (extremeVolatility) {
      return MarketType.HIGH_VOLATILITY;
    }

    // 2. Reversões confirmadas com sinais extremos - maior prioridade
    if ((confirmingReversalUp && rsiStronglyOversold) ||
      (confirmingReversalDown && rsiStronglyOverbought)) {
      return MarketType.TREND_REVERSAL;
    }

    // 3. Reversões confirmadas normais
    if (confirmingReversalUp || confirmingReversalDown) {
      return MarketType.TREND_REVERSAL;
    }

    // 4. Volatilidade muito alta com bandas muito largas - risco elevado
    if (highVolatility && veryWideBands) {
      return MarketType.HIGH_VOLATILITY;
    }

    // 5. Breakouts após consolidação
    if (potentialBreakoutUp || potentialBreakoutDown) {
      return priceAboveEma50 ? MarketType.WEAK_UPTREND : MarketType.WEAK_DOWNTREND;
    }

    // 6. Squeeze - período de acumulação/preparação
    if (priceSqueezing) {
      return MarketType.RANGE_BOUND;
    }

    // 7. Tendências fortes com sinais claros
    if (strongUptrend && strongPositiveMomentum && priceExtendedFromEma50) {
      return MarketType.STRONG_UPTREND;
    }

    if (strongDowntrend && strongNegativeMomentum && priceExtendedFromEma50) {
      return MarketType.STRONG_DOWNTREND;
    }

    // 8. Tendências moderadas
    if (ema8AboveEma21 && ema21AboveEma50 && (weakPositiveMomentum || moderateVolatility)) {
      return MarketType.WEAK_UPTREND;
    }

    if (!ema8AboveEma21 && !ema21AboveEma50 && (negativeMomentum || moderateVolatility)) {
      return MarketType.WEAK_DOWNTREND;
    }

    // 9. Mercado com bandas largas mas não em tendência clara - cautela
    if (wideBands && !strongUptrend && !strongDowntrend) {
      return MarketType.HIGH_VOLATILITY;
    }

    // 10. Mercado em consolidação
    if ((tightBands || nearMiddleBand) && Math.abs(conditions.momentum().doubleValue()) < 0.05) {
      return MarketType.RANGE_BOUND;
    }

    // Default: mercado sem padrão claro
    return MarketType.RANGE_BOUND;
  }
}