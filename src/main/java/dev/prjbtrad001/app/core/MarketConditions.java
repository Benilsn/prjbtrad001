package dev.prjbtrad001.app.core;

import lombok.Builder;

import java.math.BigDecimal;

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
  BigDecimal ema8,
  BigDecimal ema21,
  BigDecimal ema50,
  BigDecimal ema100,
  BigDecimal momentum,
  BigDecimal volatility,
  BigDecimal bollingerUpper,
  BigDecimal bollingerMiddle,
  BigDecimal bollingerLower,
  BigDecimal priceSlope,
  BigDecimal macd,
  BigDecimal stochasticK,
  BigDecimal stochasticD,
  BigDecimal atr,
  BigDecimal obv
) {
}
