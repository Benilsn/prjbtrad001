package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.dto.Kline;
import dev.prjbtrad001.domain.core.BotType;
import lombok.experimental.UtilityClass;
import lombok.extern.jbosslog.JBossLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static dev.prjbtrad001.app.utils.LogUtils.log;

@JBossLog
@UtilityClass
public class TradingService {

  public static void analyzeMarket(BotType botType, String interval, int limit, int smaShort, int smaLong, double rsiPurchase, double rsiSale, double volumeMultiplier) {
    List<Double> closePrices = new ArrayList<>();
    List<Double> volumes = new ArrayList<>();

    List<Kline> klines = BinanceService.getCandles(botType.toString(), interval, limit);

    klines.forEach(kline -> {
      closePrices.add(Double.parseDouble(kline.getClosePrice()));
      volumes.add(Double.parseDouble(kline.getVolume()));
    });

    double rsi = calculateRSI(last(closePrices, 15), 14);
    double sma9 = calculateAverage(last(closePrices, smaShort));
    double sma21 = calculateAverage(last(closePrices, smaLong));
    double currentVolume = volumes.getLast();
    double averageVolume = calculateAverage(volumes);

    double support = Collections.min(last(closePrices, 30));
    double resistance = Collections.max(last(closePrices, 30));
    double currentPrice = closePrices.getLast();

    String botTypeName = "[" + botType + "] - ";
    double range = resistance - support;
    double tolerance = range * 0.1;

    boolean rsiOversold = rsi < rsiPurchase;
    boolean touchedSupport = currentPrice <= support + tolerance;
    boolean bullishTrend = sma9 > sma21;
    boolean strongVolumeBuy = currentVolume >= averageVolume * volumeMultiplier;

    log(botTypeName + "🔻 RSI Oversold: " + rsiOversold + " (" + rsi + " < " + rsiPurchase + ")" + " - RSI: " + rsi);
    log(botTypeName + "📉 Bullish Trend: " + bullishTrend + " (SMA9: " + sma9 + " >  SMA21: " + sma21 + ")");
    log(botTypeName + "🟢 Touched Support: " + touchedSupport + " (Current Price: " + currentPrice + " <= Support: " + (support + tolerance) + ")");
    log(botTypeName + "📊 Volume for Buy: " + strongVolumeBuy + " (Current Volume: " + currentVolume + " >= Average Volume: " + averageVolume * volumeMultiplier + ")");

    boolean shouldBuy = ((rsiOversold && touchedSupport) || (bullishTrend && touchedSupport)) && strongVolumeBuy;

    boolean rsiOverbought = rsi > rsiSale;
    boolean touchedResistance = currentPrice >= resistance - tolerance;
    boolean bearishTrend = sma9 < sma21;
    boolean strongVolumeSell = currentVolume >= averageVolume * 0.9;

    log(botTypeName + "🔺 RSI Overbought: " + rsiOverbought + " (" + rsi + " > " + rsiSale + ")" + " - RSI: " + rsi);
    log(botTypeName + "📈 Bearish Trend: " + bearishTrend + " (SMA9: " + sma9 + " < SMA21: " + sma21 + ")");
    log(botTypeName + "🔴 Touched Resistance: " + touchedResistance + " (Current Price: " + currentPrice + " >= Resistance: " + (resistance - tolerance) + ")");
    log(botTypeName + "📊 Volume for Sell: " + strongVolumeSell + " (Current Volume: " + currentVolume + " >= Average Volume: " + averageVolume * 0.9 + ")");

    boolean shouldSell = ((rsiOverbought && touchedResistance) || (bearishTrend && touchedResistance)) && strongVolumeSell;

    if (bullishTrend && shouldBuy && !shouldSell) {
      log(botTypeName + "🔵 BUY signal detected!");
    } else if (bearishTrend && shouldSell && !shouldBuy) {
      log(botTypeName + "🔴 SELL signal detected!");
    } else if (!bullishTrend && !bearishTrend && shouldBuy && shouldSell) {
      log(botTypeName + "🟡 Both BUY and SELL conditions met, but no clear trend.");
    } else {
      log(botTypeName + "🟡 No action recommended at this time.");
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
