package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.dto.Kline;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JBossLog
@ApplicationScoped
public class TradingService {

  @Inject
  BinanceService binanceService;

  public void analyzeMarket() {
    List<Double> closePrices = new ArrayList<>();
    List<Double> volumes = new ArrayList<>();

    // 1. Get 1-minute candles (last 50)
    List<Kline> klines = binanceService.getCandles("BTCUSDT", "1m", 100);

    klines.forEach(kline -> {
      closePrices.add(Double.parseDouble(kline.getClosePrice()));
      volumes.add(Double.parseDouble(kline.getVolume()));
    });

    // 2. Indicators
    double rsi = calculateRSI(closePrices, 14);
    double sma9 = calculateAverage(last(closePrices, 9));
    double sma21 = calculateAverage(last(closePrices, 21));
    double currentVolume = volumes.getLast();
    double averageVolume = calculateAverage(volumes.subList(volumes.size() - 50, volumes.size()));
    double support = Collections.min(last(closePrices, 30));
    double resistance = Collections.max(last(closePrices, 30));
    double currentPrice = closePrices.getLast();

    // 3. Decision
    log.info("RSI: " + rsi);
    log.info("SMA9: " + sma9 + ", SMA21: " + sma21);
    log.info("Current Volume: " + currentVolume + ", Average Volume: " + averageVolume);
    log.info("Support: " + support + ", Resistance: " + resistance);
    log.info("Current Price: " + currentPrice);

    boolean rsiOversold = rsi < 30;
    boolean touchedSupport = currentPrice >= support;
    boolean bullishTrend = sma9 > sma21;
    boolean strongVolume = currentVolume > averageVolume * 1.2;

    if (rsiOversold && touchedSupport && bullishTrend && strongVolume) {
      log.info("🔵 BUY signal detected!");
    }

    boolean rsiOverbought = rsi > 70;
    boolean touchedResistance = currentPrice >= resistance;
    boolean bearishTrend = sma9 < sma21;

    if ((rsiOverbought || touchedResistance) && bearishTrend && strongVolume) {
      log.info("🔴 SELL signal detected!");
    } else {
      log.info("🟡 No action recommended at this time.");
    }
  }

  public static double calculateRSI(List<Double> closePrices, int period) {
    double gain = 0, loss = 0;

    for (int i = 1; i <= period; i++) {
      double diff = closePrices.get(i) - closePrices.get(i - 1);
      if (diff > 0) gain += diff;
      else loss += -diff;
    }

    double averageGain = gain / period;
    double averageLoss = loss / period;

    if (averageLoss == 0) return 100;
    double rs = averageGain / averageLoss;
    return 100 - (100 / (1 + rs));
  }

  public static double calculateAverage(List<Double> values) {
    return
      values
        .stream()
        .mapToDouble(v -> v)
        .average()
        .orElse(0);
  }

  public static List<Double> last(List<Double> list, int n) {
    return
      list
        .subList(list.size() - n, list.size());
  }

}
