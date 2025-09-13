package dev.prjbtrad001.app.core;

import java.math.BigDecimal;

public class TradingConstants {
  // Thresholds de compra por tipo de mercado
  public static final double BUY_THRESHOLD_STRONG_UPTREND = 4.5;
  public static final double BUY_THRESHOLD_WEAK_UPTREND = 3.8;
  public static final double BUY_THRESHOLD_RANGE_BOUND = 4.0;
  public static final double BUY_THRESHOLD_WEAK_DOWNTREND = 4.8;
  public static final double BUY_THRESHOLD_STRONG_DOWNTREND = 5.5;
  public static final double BUY_THRESHOLD_HIGH_VOLATILITY = 5.0;
  public static final double BUY_THRESHOLD_TREND_REVERSAL = 3.8;

  // Thresholds de venda por tipo de mercado
  public static final double SELL_THRESHOLD_STRONG_UPTREND = 3.5;
  public static final double SELL_THRESHOLD_WEAK_UPTREND = 3.0;
  public static final double SELL_THRESHOLD_RANGE_BOUND = 2.8;
  public static final double SELL_THRESHOLD_WEAK_DOWNTREND = 2.5;
  public static final double SELL_THRESHOLD_STRONG_DOWNTREND = 2.0;
  public static final double SELL_THRESHOLD_HIGH_VOLATILITY = 2.5;
  public static final double SELL_THRESHOLD_TREND_REVERSAL = 2.8;

  public static final BigDecimal MIN_PROFIT_THRESHOLD = new BigDecimal("0.4");

}