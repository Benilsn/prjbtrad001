package dev.prjbtrad001.app.core;

import java.math.BigDecimal;

public class TradingConstants {
  public static final double BUY_THRESHOLD = 2.5;
  public static final double SELL_THRESHOLD = 2.8;

  public static final BigDecimal EXTREME_RSI_UP = new BigDecimal("75");
  public static final BigDecimal EXTREME_RSI_DOWN = new BigDecimal("25");

  public static final BigDecimal MIN_PROFIT_THRESHOLD = new BigDecimal("0.4");
  public static final int POSITION_TIMEOUT_SECONDS = 180;

  public static final BigDecimal DOWNTREND_THRESHOLD = new BigDecimal("0.00025");
  public static final int DOWNTREND_SCORE_THRESHOLD = 7;

}