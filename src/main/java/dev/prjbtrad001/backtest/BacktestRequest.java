package dev.prjbtrad001.backtest;

import java.math.BigDecimal;

/**
 * Parameters for a single backtest run.
 *
 * @param feePercent per-side fee as a percentage (e.g. 0.1 for 0.1%)
 */
public record BacktestRequest(
  String symbol,
  String timeframe,
  int emaFast,
  int emaSlow,
  BigDecimal stopLossPercent,
  int candles,
  BigDecimal feePercent
) {
}
