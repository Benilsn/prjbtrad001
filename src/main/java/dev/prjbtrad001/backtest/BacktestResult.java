package dev.prjbtrad001.backtest;

import java.util.List;

/**
 * Outcome of a backtest: headline metrics, the equity curve, and the trade log.
 * When {@code ok} is false, {@code message} explains why and the rest is empty.
 */
public record BacktestResult(
  boolean ok,
  String message,

  String symbol,
  String timeframe,
  int emaFast,
  int emaSlow,
  int candleCount,
  String fromTime,
  String toTime,

  double initialEquity,
  double finalEquity,
  double netReturnPct,
  double buyHoldReturnPct,
  double maxDrawdownPct,
  int numTrades,
  double winRatePct,
  double profitFactor,

  List<Double> equityCurve,
  List<Double> buyHoldCurve,
  List<Long> times,
  List<TradeRow> trades
) {

  /** True when the strategy beat simply buying and holding over the window. */
  public boolean beatBuyHold() {
    return netReturnPct > buyHoldReturnPct;
  }

  public static BacktestResult error(String message) {
    return new BacktestResult(false, message,
      null, null, 0, 0, 0, null, null,
      0, 0, 0, 0, 0, 0, 0, 0,
      List.of(), List.of(), List.of(), List.of());
  }

  /** A single closed round-trip trade. */
  public record TradeRow(
    int number,
    String entryTime,
    String exitTime,
    double entryPrice,
    double exitPrice,
    double profitPct,
    boolean win
  ) {
  }
}
