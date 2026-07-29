package dev.prjbtrad001.market;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only market data. Public Binance endpoints only — no API key needed,
 * because v1 never places real orders.
 */
public interface MarketDataClient {

  /** Most recent {@code limit} candles (max 1000 per Binance request). */
  List<KlineDto> getCandles(String symbol, String interval, int limit);

  /**
   * Up to {@code total} most recent candles, paginating past the 1000/request
   * cap so backtests can span years. Returned oldest → newest.
   */
  List<KlineDto> getCandlesRange(String symbol, String interval, int total);

  /** Latest traded price for the symbol, or null on failure. */
  BigDecimal getPrice(String symbol);
}
