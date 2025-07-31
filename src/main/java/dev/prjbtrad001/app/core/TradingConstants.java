package dev.prjbtrad001.app.core;

import java.math.BigDecimal;

public class TradingConstants {
  public static final double BUY_THRESHOLD = 1.75;
  public static final double SELL_THRESHOLD = 1.75;
  public static final BigDecimal TOLERANCE_MULTIPLIER = BigDecimal.valueOf(0.1);
  public static final int PRICE_SCALE = 8;
}