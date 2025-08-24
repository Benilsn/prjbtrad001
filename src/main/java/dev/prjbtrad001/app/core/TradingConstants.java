package dev.prjbtrad001.app.core;

import java.math.BigDecimal;

public class TradingConstants {
  // Ajustes nos limiares de decisão
  public static final double BUY_THRESHOLD = 2.5;       // Aumentado para exigir sinais mais fortes
  public static final double SELL_THRESHOLD = 2.8;      // Aumentado para maior precisão

  // Ajustes nos limiares de RSI
  public static final BigDecimal EXTREME_RSI_UP = new BigDecimal("75");
  public static final BigDecimal EXTREME_RSI_DOWN = new BigDecimal("25");

  // Ajustes para lucro mínimo e timeouts
  public static final BigDecimal MIN_PROFIT_THRESHOLD = new BigDecimal("0.4");  // Aumentado para garantir lucro após taxas
  public static final int POSITION_TIMEOUT_SECONDS = 1800;  // Aumentado para 30 min

  // Novos parâmetros para controle de mercados voláteis
  public static final BigDecimal HIGH_VOLATILITY_THRESHOLD = new BigDecimal("2.5");
  public static final BigDecimal DOWNTREND_THRESHOLD = new BigDecimal("0.00025");
  public static final int DOWNTREND_SCORE_THRESHOLD = 7;

  // Parâmetros para ajuste dinâmico de posição
  public static final BigDecimal MAX_POSITION_MULTIPLIER = new BigDecimal("1.3");
  public static final BigDecimal MIN_POSITION_MULTIPLIER = new BigDecimal("0.3");
}