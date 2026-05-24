package dev.prjbtrad001.app.core;

import dev.prjbtrad001.app.bot.BotParameters;
import dev.prjbtrad001.app.dto.KlineDto;
import dev.prjbtrad001.infra.exception.TradeException;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Builder
@JBossLog
@NoArgsConstructor
public class MarketAnalyzer {

  public MarketConditions analyzeMarket(List<KlineDto> klines, BotParameters parameters) {
    if (klines == null || klines.isEmpty()) {
      throw new TradeException("Empty klines data for market analysis");
    }

    List<BigDecimal> closePrices = new ArrayList<>();
    List<BigDecimal> volumes     = new ArrayList<>();
    List<BigDecimal> highPrices  = new ArrayList<>();
    List<BigDecimal> lowPrices   = new ArrayList<>();

    for (KlineDto kline : klines) {
      closePrices.add(new BigDecimal(kline.getClosePrice()));
      volumes.add(new BigDecimal(kline.getVolume()));
      highPrices.add(new BigDecimal(kline.getHighPrice()));
      lowPrices.add(new BigDecimal(kline.getLowPrice()));
    }

    // RSI — Wilder's smoothed method (fixed vs previous broken implementation)
    BigDecimal rsi = calculateRSI(closePrices, 14);

    // SMAs for legacy signal checks
    SMAValues sma = calculateSMAs(closePrices, parameters);

    // Volume
    BigDecimal currentVolume = volumes.getLast();
    BigDecimal averageVolume = calculateAverage(volumes);

    // Support / Resistance
    BigDecimal support    = identifySupport(lowPrices, closePrices);
    BigDecimal resistance = identifyResistance(highPrices, closePrices);

    BigDecimal currentPrice = closePrices.getLast();

    // EMAs — short-term for entry timing
    BigDecimal ema8  = calculateEMA(closePrices, 8);
    BigDecimal ema21 = calculateEMA(closePrices, 21);

    // EMAs — swing-trade trend filters
    BigDecimal ema50  = calculateEMA(closePrices, 50);
    BigDecimal ema200 = calculateEMA(closePrices, 200);  // primary trend filter

    BigDecimal momentum   = calculateMomentum(closePrices, 10);
    BigDecimal volatility = calculateVolatility(closePrices, 14);

    BigDecimal[] bollinger  = calculateBollingerBands(closePrices, 20, 2);
    BigDecimal  priceSlope  = calculatePriceSlope(closePrices);

    // MACD with signal line — enables crossover detection
    BigDecimal[] macdResult = calculateMACDWithSignal(closePrices);
    BigDecimal macd          = macdResult[0];
    BigDecimal macdSignal    = macdResult[1];
    BigDecimal macdHistogram = macdResult[2];

    BigDecimal[] stochastic = calculateStochastic(closePrices, highPrices, lowPrices, 14, 3);
    BigDecimal  atr         = calculateATR(highPrices, lowPrices, closePrices, 14);
    BigDecimal  obv         = calculateOBV(closePrices, volumes);

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
      .ema200(ema200)
      .momentum(momentum)
      .volatility(volatility)
      .bollingerUpper(bollinger[0])
      .bollingerMiddle(bollinger[1])
      .bollingerLower(bollinger[2])
      .priceSlope(priceSlope)
      .macd(macd)
      .macdSignal(macdSignal)
      .macdHistogram(macdHistogram)
      .stochasticK(stochastic[0])
      .stochasticD(stochastic[1])
      .atr(atr)
      .obv(obv)
      .build();
  }

  // ─────────────────────────────────────────────────────────────
  //  RSI — Wilder's Smoothed Moving Average (correct implementation)
  //
  //  The previous version had two bugs:
  //    1. Used indexOf() which breaks on duplicate prices
  //    2. Averaged ALL gains/losses instead of using Wilder's smoothing
  // ─────────────────────────────────────────────────────────────
  public static BigDecimal calculateRSI(List<BigDecimal> closePrices, int period) {
    if (closePrices.size() <= period) {
      throw new IllegalArgumentException("Insufficient data for RSI: need > " + period + " candles");
    }

    // Seed: simple average of first `period` changes
    BigDecimal sumGain = BigDecimal.ZERO;
    BigDecimal sumLoss = BigDecimal.ZERO;

    for (int i = 1; i <= period; i++) {
      BigDecimal change = closePrices.get(i).subtract(closePrices.get(i - 1));
      if (change.compareTo(BigDecimal.ZERO) > 0) {
        sumGain = sumGain.add(change);
      } else {
        sumLoss = sumLoss.add(change.abs());
      }
    }

    BigDecimal avgGain = sumGain.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
    BigDecimal avgLoss = sumLoss.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);

    // Wilder's smoothing for remaining candles
    for (int i = period + 1; i < closePrices.size(); i++) {
      BigDecimal change = closePrices.get(i).subtract(closePrices.get(i - 1));
      BigDecimal gain   = change.compareTo(BigDecimal.ZERO) > 0 ? change : BigDecimal.ZERO;
      BigDecimal loss   = change.compareTo(BigDecimal.ZERO) < 0 ? change.abs() : BigDecimal.ZERO;

      avgGain = avgGain.multiply(BigDecimal.valueOf(period - 1)).add(gain)
        .divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
      avgLoss = avgLoss.multiply(BigDecimal.valueOf(period - 1)).add(loss)
        .divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP);
    }

    if (avgLoss.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.valueOf(100);

    BigDecimal rs = avgGain.divide(avgLoss, 10, RoundingMode.HALF_UP);
    return BigDecimal.valueOf(100)
      .subtract(BigDecimal.valueOf(100)
        .divide(BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP));
  }

  // ─────────────────────────────────────────────────────────────
  //  MACD with Signal Line and Histogram
  //  Returns [macd, signal, histogram]
  // ─────────────────────────────────────────────────────────────
  private BigDecimal[] calculateMACDWithSignal(List<BigDecimal> prices) {
    if (prices.size() < 26) {
      return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }

    // Build a series of MACD values so we can EMA them into a signal line
    List<BigDecimal> macdSeries = new ArrayList<>();

    // We need at least 26 candles to compute any EMA26
    // Walk forward from candle 26 onwards, computing EMA12 and EMA26 up to that point
    for (int end = 26; end <= prices.size(); end++) {
      List<BigDecimal> slice = prices.subList(0, end);
      BigDecimal e12 = calculateEMA(slice, 12);
      BigDecimal e26 = calculateEMA(slice, 26);
      macdSeries.add(e12.subtract(e26));
    }

    BigDecimal macd       = macdSeries.getLast();
    BigDecimal macdSignal = calculateEMA(macdSeries, Math.min(9, macdSeries.size()));
    BigDecimal histogram  = macd.subtract(macdSignal);

    return new BigDecimal[]{macd, macdSignal, histogram};
  }

  // ─────────────────────────────────────────────────────────────
  //  EMA
  // ─────────────────────────────────────────────────────────────
  private BigDecimal calculateEMA(List<BigDecimal> prices, int period) {
    if (prices.size() < period) return BigDecimal.ZERO;

    BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));
    BigDecimal ema = calculateAverage(prices.subList(0, period));

    for (int i = period; i < prices.size(); i++) {
      ema = prices.get(i).multiply(multiplier)
        .add(ema.multiply(BigDecimal.ONE.subtract(multiplier)));
    }
    return ema;
  }

  // ─────────────────────────────────────────────────────────────
  //  SMA helpers
  // ─────────────────────────────────────────────────────────────
  private SMAValues calculateSMAs(List<BigDecimal> closePrices, BotParameters parameters) {
    BigDecimal sma9  = calculateSMA(closePrices, parameters.getSmaShort());
    BigDecimal sma21 = calculateSMA(closePrices, parameters.getSmaLong());
    return new SMAValues(sma9, sma21);
  }

  private BigDecimal calculateSMA(List<BigDecimal> prices, int period) {
    if (prices.size() < period) {
      throw new TradeException("Insufficient data for SMA period " + period);
    }
    return calculateAverage(prices.subList(prices.size() - period, prices.size()));
  }

  // ─────────────────────────────────────────────────────────────
  //  Bollinger Bands
  // ─────────────────────────────────────────────────────────────
  private BigDecimal[] calculateBollingerBands(List<BigDecimal> prices, int period, double stdDev) {
    if (prices.size() < period) return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    BigDecimal sma = calculateAverage(prices.subList(prices.size() - period, prices.size()));
    BigDecimal std = calculateVolatility(prices, period);
    BigDecimal mult = BigDecimal.valueOf(stdDev);
    return new BigDecimal[]{sma.add(std.multiply(mult)), sma, sma.subtract(std.multiply(mult))};
  }

  // ─────────────────────────────────────────────────────────────
  //  Momentum, Volatility, ATR, OBV
  // ─────────────────────────────────────────────────────────────
  private BigDecimal calculateMomentum(List<BigDecimal> prices, int period) {
    if (prices.size() < period + 1) return BigDecimal.ZERO;
    BigDecimal current = prices.getLast();
    BigDecimal past    = prices.get(prices.size() - period - 1);
    return current.subtract(past)
      .divide(past, 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));
  }

  private BigDecimal calculateVolatility(List<BigDecimal> prices, int period) {
    if (prices.size() < period) return BigDecimal.ZERO;
    List<BigDecimal> slice = prices.subList(prices.size() - period, prices.size());
    BigDecimal avg = calculateAverage(slice);
    BigDecimal sumSq = slice.stream()
      .map(p -> p.subtract(avg).pow(2))
      .reduce(BigDecimal.ZERO, BigDecimal::add);
    return BigDecimal.valueOf(Math.sqrt(
      sumSq.divide(BigDecimal.valueOf(period), 10, RoundingMode.HALF_UP).doubleValue()
    ));
  }

  private BigDecimal calculateATR(List<BigDecimal> highPrices,
                                  List<BigDecimal> lowPrices,
                                  List<BigDecimal> closePrices,
                                  int period) {
    if (highPrices.size() < period + 1) return BigDecimal.ZERO;
    List<BigDecimal> trValues = new ArrayList<>();
    for (int i = 1; i < highPrices.size(); i++) {
      BigDecimal high      = highPrices.get(i);
      BigDecimal low       = lowPrices.get(i);
      BigDecimal prevClose = closePrices.get(i - 1);
      BigDecimal tr = high.subtract(low)
        .max(high.subtract(prevClose).abs())
        .max(low.subtract(prevClose).abs());
      trValues.add(tr);
    }
    return calculateAverage(last(trValues, Math.min(period, trValues.size())));
  }

  private BigDecimal calculateOBV(List<BigDecimal> closePrices, List<BigDecimal> volumes) {
    if (closePrices.size() < 2) return BigDecimal.ZERO;
    BigDecimal obv = BigDecimal.ZERO;
    for (int i = 1; i < closePrices.size(); i++) {
      int cmp = closePrices.get(i).compareTo(closePrices.get(i - 1));
      if (cmp > 0)      obv = obv.add(volumes.get(i));
      else if (cmp < 0) obv = obv.subtract(volumes.get(i));
    }
    return obv;
  }

  private BigDecimal calculatePriceSlope(List<BigDecimal> prices) {
    if (prices == null || prices.size() < 2) return BigDecimal.ZERO;
    return prices.getLast().subtract(prices.getFirst())
      .divide(BigDecimal.valueOf(prices.size()), 8, RoundingMode.HALF_UP);
  }

  // ─────────────────────────────────────────────────────────────
  //  Stochastic Oscillator
  // ─────────────────────────────────────────────────────────────
  private BigDecimal[] calculateStochastic(List<BigDecimal> closePrices,
                                           List<BigDecimal> highPrices,
                                           List<BigDecimal> lowPrices,
                                           int kPeriod, int dPeriod) {
    if (closePrices.size() < kPeriod) return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};

    List<BigDecimal> kValues = new ArrayList<>();
    for (int i = kPeriod - 1; i < closePrices.size(); i++) {
      List<BigDecimal> ph = highPrices.subList(i - kPeriod + 1, i + 1);
      List<BigDecimal> pl = lowPrices.subList(i - kPeriod + 1, i + 1);
      BigDecimal hhigh = Collections.max(ph);
      BigDecimal llow  = Collections.min(pl);
      BigDecimal close = closePrices.get(i);
      if (hhigh.equals(llow)) {
        kValues.add(BigDecimal.valueOf(50));
      } else {
        kValues.add(close.subtract(llow).multiply(BigDecimal.valueOf(100))
          .divide(hhigh.subtract(llow), 8, RoundingMode.HALF_UP));
      }
    }

    BigDecimal k = kValues.getLast();
    BigDecimal d = calculateAverage(last(kValues, Math.min(dPeriod, kValues.size())));
    return new BigDecimal[]{k, d};
  }

  // ─────────────────────────────────────────────────────────────
  //  Support / Resistance (simplified weighted recent extremes)
  // ─────────────────────────────────────────────────────────────
  private BigDecimal identifySupport(List<BigDecimal> lowPrices, List<BigDecimal> closePrices) {
    int lookback = Math.min(50, lowPrices.size());
    List<BigDecimal> recent = new ArrayList<>(last(lowPrices, lookback));
    recent.sort(BigDecimal::compareTo);
    if (recent.size() >= 3) {
      return recent.get(0).multiply(BigDecimal.valueOf(0.5))
        .add(recent.get(1).multiply(BigDecimal.valueOf(0.3)))
        .add(recent.get(2).multiply(BigDecimal.valueOf(0.2)));
    }
    return Collections.min(recent);
  }

  private BigDecimal identifyResistance(List<BigDecimal> highPrices, List<BigDecimal> closePrices) {
    int lookback = Math.min(50, highPrices.size());
    List<BigDecimal> recent = new ArrayList<>(last(highPrices, lookback));
    recent.sort(Collections.reverseOrder());
    if (recent.size() >= 3) {
      return recent.get(0).multiply(BigDecimal.valueOf(0.5))
        .add(recent.get(1).multiply(BigDecimal.valueOf(0.3)))
        .add(recent.get(2).multiply(BigDecimal.valueOf(0.2)));
    }
    return Collections.max(recent);
  }

  // ─────────────────────────────────────────────────────────────
  //  Utilities
  // ─────────────────────────────────────────────────────────────
  private static List<BigDecimal> last(List<BigDecimal> list, int n) {
    if (list.size() <= n) return list;
    return list.subList(list.size() - n, list.size());
  }

  private BigDecimal calculateAverage(List<BigDecimal> values) {
    if (values == null || values.isEmpty()) return BigDecimal.ZERO;
    return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
      .divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
  }
}
