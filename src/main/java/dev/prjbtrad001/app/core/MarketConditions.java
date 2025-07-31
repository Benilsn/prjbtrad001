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
  BigDecimal averageVolume
) {
}
