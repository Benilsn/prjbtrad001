package dev.prjbtrad001.app.core;

import java.math.BigDecimal;

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
  BigDecimal momentum,
  BigDecimal volatility,
  BigDecimal bollingerUpper,
  BigDecimal bollingerMiddle,
  BigDecimal bollingerLower
) {
}
