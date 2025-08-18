package dev.prjbtrad001.app.core;

import java.math.BigDecimal;

public class TradingConstants {
  public static final double BUY_THRESHOLD = 1.8;
  public static final double SELL_THRESHOLD = 2.0;
  public static final BigDecimal MIN_PROFIT_THRESHOLD = BigDecimal.valueOf(0.3);
  public static final int POSITION_TIMEOUT_SECONDS = 3600;
}