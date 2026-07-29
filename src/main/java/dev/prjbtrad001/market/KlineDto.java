package dev.prjbtrad001.market;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * A single candlestick. Binance returns klines as JSON arrays:
 * [ openTime, open, high, low, close, volume, closeTime, ... ]
 *
 * We only keep what the strategy and backtest need.
 */
public record KlineDto(
  long openTime,
  BigDecimal open,
  BigDecimal high,
  BigDecimal low,
  BigDecimal close,
  BigDecimal volume,
  long closeTime
) {

  /** Candle close instant — used as the ta4j bar end time. */
  public Instant closeInstant() {
    return Instant.ofEpochMilli(closeTime);
  }

  /** Bar duration derived from open/close timestamps. */
  public Duration period() {
    long millis = closeTime - openTime + 1;
    return Duration.ofMillis(millis > 0 ? millis : 1);
  }
}
