package dev.prjbtrad001.app.core;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Snapshot of market conditions calculated from kline data.
 * Designed for swing trading on 4h/1d timeframes.
 */
@Builder
public record MarketConditions(
  BigDecimal rsi,
  BigDecimal sma9,
  BigDecimal sma21,
  BigDecimal support,
  BigDecimal resistance,
  BigDecimal currentPrice,
  BigDecimal currentVolume,
  BigDecimal averageVolume,
  // Short-term EMAs (entry timing)
  BigDecimal ema8,
  BigDecimal ema21,
  // Swing-trade trend EMAs
  BigDecimal ema50,
  BigDecimal ema200,
  BigDecimal momentum,
  BigDecimal volatility,
  BigDecimal bollingerUpper,
  BigDecimal bollingerMiddle,
  BigDecimal bollingerLower,
  BigDecimal priceSlope,
  // MACD: line = EMA12 - EMA26; signal = 9-EMA of macd; histogram = macd - signal
  BigDecimal macd,
  BigDecimal macdSignal,
  BigDecimal macdHistogram,
  BigDecimal stochasticK,
  BigDecimal stochasticD,
  // ATR: average true range, used for volatility-adjusted stops
  BigDecimal atr,
  BigDecimal obv
) {}
