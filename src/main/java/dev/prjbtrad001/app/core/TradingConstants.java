package dev.prjbtrad001.app.core;

import java.math.BigDecimal;

public class TradingConstants {
  public static final double BUY_THRESHOLD =  3.5;
  public static final double SELL_THRESHOLD =  3.0;

  public static final BigDecimal MIN_PROFIT_THRESHOLD = new BigDecimal("0.4");

  public static final BigDecimal DOWNTREND_THRESHOLD = new BigDecimal("0.00025");
  public static final int DOWNTREND_SCORE_THRESHOLD = 7;

}