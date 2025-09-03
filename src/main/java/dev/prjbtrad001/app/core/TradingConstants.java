package dev.prjbtrad001.app.core;

import java.math.BigDecimal;

public class TradingConstants {
  public static final double BUY_THRESHOLD = 4.0;
  public static final double SELL_THRESHOLD = 3.0;

  // Thresholds de compra por tipo de mercado
  public static final double BUY_THRESHOLD_STRONG_UPTREND = 4.5;  // Mais seletivo em alta forte
  public static final double BUY_THRESHOLD_WEAK_UPTREND = 3.8;    // Moderado em alta fraca
  public static final double BUY_THRESHOLD_RANGE_BOUND = 3.5;     // Mais permissivo em mercado lateral
  public static final double BUY_THRESHOLD_WEAK_DOWNTREND = 4.8;  // Mais seletivo em baixa fraca
  public static final double BUY_THRESHOLD_STRONG_DOWNTREND = 5.5; // Muito seletivo em baixa forte
  public static final double BUY_THRESHOLD_HIGH_VOLATILITY = 5.0;  // Cuidadoso em alta volatilidade
  public static final double BUY_THRESHOLD_TREND_REVERSAL = 3.3;   // Mais permissivo em reversão

  // Thresholds de venda por tipo de mercado
  public static final double SELL_THRESHOLD_STRONG_UPTREND = 3.5;  // Mais seletivo para vender em alta forte
  public static final double SELL_THRESHOLD_WEAK_UPTREND = 3.2;    // Moderado em alta fraca
  public static final double SELL_THRESHOLD_RANGE_BOUND = 2.8;     // Mais ágil para vender em mercado lateral
  public static final double SELL_THRESHOLD_WEAK_DOWNTREND = 2.5;  // Mais ágil para vender em baixa fraca
  public static final double SELL_THRESHOLD_STRONG_DOWNTREND = 2.0; // Muito ágil para vender em baixa forte
  public static final double SELL_THRESHOLD_HIGH_VOLATILITY = 2.5;  // Ágil em alta volatilidade
  public static final double SELL_THRESHOLD_TREND_REVERSAL = 3.0;   // Normal em reversão

  public static final BigDecimal MIN_PROFIT_THRESHOLD = new BigDecimal("0.4");

  public static final BigDecimal DOWNTREND_THRESHOLD = new BigDecimal("0.00025");
  public static final int DOWNTREND_SCORE_THRESHOLD = 7;

}