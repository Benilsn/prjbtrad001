package dev.prjbtrad001.app.core;

import dev.prjbtrad001.app.dto.KlineDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class MarketFilter {

  public static boolean isMarketFavorable(List<KlineDto> klines) {
    BigDecimal downTrendStrength = calculateDownTrendStrength(klines);
    return downTrendStrength.compareTo(BigDecimal.valueOf(0.6)) < 0;
  }

  private static BigDecimal calculateDownTrendStrength(List<KlineDto> klines) {
    if (klines.size() < 24) return BigDecimal.ZERO;

    int downCandles = 0;
    int totalCandles = Math.min(24, klines.size());

    for (int i = klines.size() - totalCandles; i < klines.size(); i++) {
      KlineDto kline = klines.get(i);
      if (new BigDecimal(kline.getClosePrice()).compareTo(
        new BigDecimal(kline.getOpenPrice())) < 0) {
        downCandles++;
      }
    }

    return new BigDecimal(downCandles).divide(new BigDecimal(totalCandles), 8, RoundingMode.HALF_UP);
  }
}