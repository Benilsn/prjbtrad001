package dev.prjbtrad001.app.core;

import dev.prjbtrad001.app.bot.BotParameters;
import dev.prjbtrad001.app.dto.KlineDto;
import dev.prjbtrad001.infra.exception.TradeException;
import lombok.extern.jbosslog.JBossLog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@JBossLog
public class MarketAnalyzer {

  public MarketConditions analyzeMarket(List<KlineDto> klines, BotParameters parameters) {
    List<BigDecimal> closePrices = new ArrayList<>();
    List<BigDecimal> volumes = new ArrayList<>();

    klines.forEach(kline -> {
      closePrices.add(new BigDecimal(kline.getClosePrice()));
      volumes.add(new BigDecimal(kline.getVolume()));
    });

    BigDecimal rsi = calculateRSI(last(closePrices, 15), 14);
    SMAValues sma = calculateSMAs(closePrices, parameters);
    BigDecimal currentVolume = volumes.getLast();
    BigDecimal averageVolume = calculateAverage(volumes);

    BigDecimal support = Collections.min(last(closePrices, 30));
    BigDecimal resistance = Collections.max(last(closePrices, 30));
    BigDecimal currentPrice = closePrices.getLast();
    return
      new MarketConditions(
        rsi,
        sma.sma9(),
        sma.sma21(),
        support,
        resistance,
        currentPrice,
        currentVolume,
        averageVolume
      );
  }

  private BigDecimal calculateRSI(List<BigDecimal> closePrices, int period) {
    if (closePrices.size() <= period) {
      throw new IllegalArgumentException("Insufficient data for RSI calculation");
    }

    BigDecimal[] changes =
      closePrices.stream()
        .limit(closePrices.size() - 1)
        .map(price -> closePrices.get(closePrices.indexOf(price) + 1)
          .subtract(price))
        .toArray(BigDecimal[]::new);

    BigDecimal avgGain =
      Arrays.stream(changes)
        .filter(change -> change.compareTo(BigDecimal.ZERO) > 0)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);

    BigDecimal avgLoss =
      Arrays.stream(changes)
        .filter(change -> change.compareTo(BigDecimal.ZERO) < 0)
        .map(BigDecimal::abs)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);

    if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.valueOf(100);
    }

    BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
    return BigDecimal.valueOf(100)
      .subtract(BigDecimal.valueOf(100)
        .divide(BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP));
  }

  private SMAValues calculateSMAs(List<BigDecimal> closePrices, BotParameters parameters) {
    if (closePrices == null || closePrices.isEmpty()) {
      throw new TradeException("Empty price list for SMA calculation");
    }

    BigDecimal sma9 = calculateSMA(closePrices, parameters.getSmaShort());
    BigDecimal sma21 = calculateSMA(closePrices, parameters.getSmaLong());

    return new SMAValues(sma9, sma21);
  }

  private BigDecimal calculateSMA(List<BigDecimal> prices, int period) {
    if (prices.size() < period) {
      log.warn("Insufficient data for SMA: " + prices.size() + "/" + period);
      throw new TradeException("Insufficient data to calculate period sum " + period);
    }

    List<BigDecimal> lastNPrices =
      prices
        .subList(Math.max(0, prices.size() - period), prices.size());
    return calculateAverage(lastNPrices);
  }

  private static BigDecimal calculateAverage(List<BigDecimal> values) {
    if (values == null || values.isEmpty()) {
      return BigDecimal.ZERO;
    }

    BigDecimal sum = values.stream()
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal count = BigDecimal.valueOf(values.size());

    return sum.divide(count, 8, RoundingMode.HALF_UP);
  }

  private static List<BigDecimal> last(List<BigDecimal> list, int n) {
    if (list == null) {
      throw new IllegalArgumentException("List cannot be null");
    }
    if (n <= 0) {
      throw new IllegalArgumentException("Period must be greater than zero");
    }
    if (list.size() < n) {
      throw new IllegalArgumentException("Very short list: necessary " + n + " elements");
    }
    return list.subList(list.size() - n, list.size());
  }

}
