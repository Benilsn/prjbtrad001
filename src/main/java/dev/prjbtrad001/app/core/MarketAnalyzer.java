package dev.prjbtrad001.app.core;

import dev.prjbtrad001.app.bot.BotParameters;
import dev.prjbtrad001.app.dto.KlineDto;
import dev.prjbtrad001.infra.exception.TradeException;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Builder
@JBossLog
@NoArgsConstructor
public class MarketAnalyzer {

  public MarketConditions analyzeMarket(List<KlineDto> klines, BotParameters parameters) {
    if (klines == null || klines.isEmpty()) {
      throw new TradeException("Empty klines data for market analysis");
    }

    List<BigDecimal> closePrices = new ArrayList<>();
    List<BigDecimal> volumes = new ArrayList<>();
    List<BigDecimal> highPrices = new ArrayList<>();
    List<BigDecimal> lowPrices = new ArrayList<>();

    klines.forEach(kline -> {
      closePrices.add(new BigDecimal(kline.getClosePrice()));
      volumes.add(new BigDecimal(kline.getVolume()));
      highPrices.add(new BigDecimal(kline.getHighPrice()));
      lowPrices.add(new BigDecimal(kline.getLowPrice()));
    });

    BigDecimal rsi = calculateRSI(last(closePrices, 15), 14);
    SMAValues sma = calculateSMAs(closePrices, parameters);
    BigDecimal currentVolume = volumes.getLast();
    BigDecimal averageVolume = calculateAverage(volumes);
    BigDecimal support = identifySupport(lowPrices, closePrices);
    BigDecimal resistance = identifyResistance(highPrices, closePrices);
    BigDecimal currentPrice = closePrices.getLast();
    BigDecimal ema8 = calculateEMA(closePrices, 8);
    BigDecimal ema21 = calculateEMA(closePrices, 21);
    BigDecimal ema50 = calculateEMA(closePrices, 50);
    BigDecimal ema100 = calculateEMA(closePrices, 100);
    BigDecimal momentum = calculateMomentum(closePrices, 10);
    BigDecimal volatility = calculateVolatility(closePrices, 14);
    BigDecimal[] bollinger = calculateBollingerBands(closePrices, 20, 2);
    BigDecimal priceSlope = calculatePriceSlope(closePrices);
    BigDecimal macd = calculateMACD(closePrices);
    BigDecimal[] stochastic = calculateStochastic(closePrices, highPrices, lowPrices, 14, 3);
    BigDecimal atr = calculateATR(highPrices, lowPrices, closePrices, 14);
    BigDecimal obv = calculateOBV(closePrices, volumes);

    return MarketConditions.builder()
      .rsi(rsi)
      .sma9(sma.sma9())
      .sma21(sma.sma21())
      .support(support)
      .resistance(resistance)
      .currentPrice(currentPrice)
      .currentVolume(currentVolume)
      .averageVolume(averageVolume)
      .ema8(ema8)
      .ema21(ema21)
      .ema50(ema50)
      .ema100(ema100)
      .momentum(momentum)
      .volatility(volatility)
      .bollingerUpper(bollinger[0])
      .bollingerMiddle(bollinger[1])
      .bollingerLower(bollinger[2])
      .priceSlope(priceSlope)
      .macd(macd)
      .stochasticK(stochastic[0])
      .stochasticD(stochastic[1])
      .atr(atr)
      .obv(obv)
      .build();
  }

  public static BigDecimal calculateRSI(List<BigDecimal> closePrices, int period) {
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

  private BigDecimal calculateMACD(List<BigDecimal> prices) {
    BigDecimal ema12 = calculateEMA(prices, 12);
    BigDecimal ema26 = calculateEMA(prices, 26);
    return ema12.subtract(ema26);
  }

  private BigDecimal[] calculateStochastic(List<BigDecimal> closePrices,
                                           List<BigDecimal> highPrices,
                                           List<BigDecimal> lowPrices,
                                           int kPeriod,
                                           int dPeriod) {
    if (closePrices.size() < kPeriod) {
      return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
    }

    List<BigDecimal> kValues = new ArrayList<>();

    for (int i = kPeriod - 1; i < closePrices.size(); i++) {
      List<BigDecimal> periodHigh = highPrices.subList(i - kPeriod + 1, i + 1);
      List<BigDecimal> periodLow = lowPrices.subList(i - kPeriod + 1, i + 1);

      BigDecimal highestHigh = Collections.max(periodHigh);
      BigDecimal lowestLow = Collections.min(periodLow);
      BigDecimal currentClose = closePrices.get(i);

      if (highestHigh.equals(lowestLow)) {
        kValues.add(BigDecimal.valueOf(50));
      } else {
        BigDecimal k = currentClose.subtract(lowestLow)
          .multiply(BigDecimal.valueOf(100))
          .divide(highestHigh.subtract(lowestLow), 8, RoundingMode.HALF_UP);
        kValues.add(k);
      }
    }

    BigDecimal k = kValues.getLast();
    BigDecimal d = calculateAverage(last(kValues, Math.min(dPeriod, kValues.size())));

    return new BigDecimal[]{k, d};
  }


  private BigDecimal calculateATR(List<BigDecimal> highPrices,
                                  List<BigDecimal> lowPrices,
                                  List<BigDecimal> closePrices,
                                  int period) {
    if (highPrices.size() < period + 1) return BigDecimal.ZERO;

    List<BigDecimal> trValues = new ArrayList<>();

    for (int i = 1; i < highPrices.size(); i++) {
      BigDecimal high = highPrices.get(i);
      BigDecimal low = lowPrices.get(i);
      BigDecimal prevClose = closePrices.get(i - 1);

      BigDecimal tr1 = high.subtract(low);
      BigDecimal tr2 = high.subtract(prevClose).abs();
      BigDecimal tr3 = low.subtract(prevClose).abs();

      BigDecimal tr = tr1.max(tr2).max(tr3);
      trValues.add(tr);
    }

    return calculateAverage(last(trValues, Math.min(period, trValues.size())));
  }

  private BigDecimal calculateOBV(List<BigDecimal> closePrices, List<BigDecimal> volumes) {
    if (closePrices.size() < 2) return BigDecimal.ZERO;

    BigDecimal obv = BigDecimal.ZERO;

    for (int i = 1; i < closePrices.size(); i++) {
      BigDecimal currentClose = closePrices.get(i);
      BigDecimal prevClose = closePrices.get(i - 1);
      BigDecimal currentVolume = volumes.get(i);

      if (currentClose.compareTo(prevClose) > 0) {
        obv = obv.add(currentVolume);
      } else if (currentClose.compareTo(prevClose) < 0) {
        obv = obv.subtract(currentVolume);
      }
    }

    return obv;
  }

  // Melhoria na identificação de suporte e resistência
  private BigDecimal identifySupport(List<BigDecimal> lowPrices, List<BigDecimal> closePrices) {
    int lookbackPeriod = Math.min(20, lowPrices.size());
    List<BigDecimal> recentLows = last(lowPrices, lookbackPeriod);
    List<BigDecimal> recentCloses = last(closePrices, lookbackPeriod);

    // Média ponderada dos menores valores
    recentLows.sort(BigDecimal::compareTo);

    BigDecimal support;
    if (recentLows.size() >= 3) {
      support = recentLows.get(0)
        .multiply(BigDecimal.valueOf(0.5))
        .add(recentLows.get(1).multiply(BigDecimal.valueOf(0.3)))
        .add(recentLows.get(2).multiply(BigDecimal.valueOf(0.2)));
    } else {
      support = Collections.min(recentLows);
    }

    // Ajuste baseado no preço de fechamento recente
    BigDecimal recentClose = recentCloses.getLast();
    if (recentClose.subtract(support).divide(support, 8, RoundingMode.HALF_UP)
      .compareTo(BigDecimal.valueOf(0.05)) > 0) {
      // Se o preço atual estiver mais de 5% acima do suporte, ajusta o nível
      support = support.add(recentClose).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
    }

    return support;
  }

  private BigDecimal identifyResistance(List<BigDecimal> highPrices, List<BigDecimal> closePrices) {
    int lookbackPeriod = Math.min(20, highPrices.size());
    List<BigDecimal> recentHighs = last(highPrices, lookbackPeriod);
    List<BigDecimal> recentCloses = last(closePrices, lookbackPeriod);

    // Média ponderada dos maiores valores
    recentHighs.sort(Collections.reverseOrder());

    BigDecimal resistance;
    if (recentHighs.size() >= 3) {
      resistance = recentHighs.get(0)
        .multiply(BigDecimal.valueOf(0.5))
        .add(recentHighs.get(1).multiply(BigDecimal.valueOf(0.3)))
        .add(recentHighs.get(2).multiply(BigDecimal.valueOf(0.2)));
    } else {
      resistance = Collections.max(recentHighs);
    }

    // Ajuste baseado no preço de fechamento recente
    BigDecimal recentClose = recentCloses.getLast();
    if (resistance.subtract(recentClose).divide(recentClose, 8, RoundingMode.HALF_UP)
      .compareTo(BigDecimal.valueOf(0.05)) > 0) {
      // Se a resistência estiver mais de 5% acima do preço atual, ajusta o nível
      resistance = resistance.add(recentClose).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
    }

    return resistance;
  }

}
