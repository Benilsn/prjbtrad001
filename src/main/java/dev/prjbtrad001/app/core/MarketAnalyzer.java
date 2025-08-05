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

    // Indicadores adicionais
    BigDecimal ema8 = calculateEMA(closePrices, 8);
    BigDecimal ema21 = calculateEMA(closePrices, 21);
    BigDecimal ema50 = calculateEMA(closePrices, 50);
    BigDecimal ema100 = calculateEMA(closePrices, 100);
    BigDecimal momentum = calculateMomentum(closePrices, 10);
    BigDecimal volatility = calculateVolatility(closePrices, 14);
    BigDecimal[] bollinger = calculateBollingerBands(closePrices, 20, 2);
    BigDecimal priceSlope = calculatePriceSlope(closePrices);

    return new MarketConditions(
      rsi,
      sma.sma9(),
      sma.sma21(),
      support,
      resistance,
      currentPrice,
      currentVolume,
      averageVolume,
      ema8,
      ema21,
      ema50,
      ema100,
      momentum,
      volatility,
      bollinger[0],
      bollinger[1],
      bollinger[2],
      priceSlope
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

  private BigDecimal calculateEMA(List<BigDecimal> prices, int period) {
    if (prices.size() < period) return BigDecimal.ZERO;

    BigDecimal multiplier = new BigDecimal(2)
      .divide(new BigDecimal(period + 1), 8, RoundingMode.HALF_UP);

    BigDecimal ema = calculateAverage(prices.subList(0, period));

    for (int i = period; i < prices.size(); i++) {
      ema = prices.get(i).multiply(multiplier)
        .add(ema.multiply(BigDecimal.ONE.subtract(multiplier)));
    }

    return ema;
  }

  private BigDecimal calculateMomentum(List<BigDecimal> prices, int period) {
    if (prices.size() < period + 1) return BigDecimal.ZERO;

    BigDecimal current = prices.getLast();
    BigDecimal past = prices.get(prices.size() - period - 1);

    return current.subtract(past)
      .divide(past, 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));
  }

  private BigDecimal calculateVolatility(List<BigDecimal> prices, int period) {
    if (prices.size() < period) return BigDecimal.ZERO;

    List<BigDecimal> recentPrices = prices.subList(prices.size() - period, prices.size());
    BigDecimal avg = calculateAverage(recentPrices);

    BigDecimal sumSquaredDiff = recentPrices.stream()
      .map(p -> p.subtract(avg).pow(2))
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal variance = sumSquaredDiff.divide(new BigDecimal(period), 8, RoundingMode.HALF_UP);
    return sqrt(variance);
  }

  private BigDecimal[] calculateBollingerBands(List<BigDecimal> prices, int period, double stdDev) {
    BigDecimal sma = calculateAverage(prices.subList(prices.size() - period, prices.size()));
    BigDecimal std = calculateVolatility(prices, period);
    BigDecimal stdMultiplier = new BigDecimal(stdDev);

    BigDecimal upper = sma.add(std.multiply(stdMultiplier));
    BigDecimal lower = sma.subtract(std.multiply(stdMultiplier));

    return new BigDecimal[]{upper, sma, lower};
  }

  private BigDecimal sqrt(BigDecimal value) {
    return BigDecimal.valueOf(Math.sqrt(value.doubleValue()));
  }

  private List<BigDecimal> extractClosePrices(List<KlineDto> klines) {
    return klines.stream()
      .map(k -> new BigDecimal(k.getClosePrice()))
      .toList();
  }

  private BigDecimal calculateAverage(List<BigDecimal> values) {
    if (values == null || values.isEmpty()) {
      return BigDecimal.ZERO;
    }

    BigDecimal sum = values.stream()
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    return sum.divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
  }

  public BigDecimal calculatePriceSlope(List<BigDecimal> prices) {
    if (prices == null || prices.size() < 2) return BigDecimal.ZERO;
    BigDecimal firstPrice = prices.getFirst();
    BigDecimal lastPrice = prices.getLast();
    int periods = prices.size();

    return lastPrice.subtract(firstPrice)
      .divide(BigDecimal.valueOf(periods), 8, RoundingMode.HALF_UP);
  }


}
