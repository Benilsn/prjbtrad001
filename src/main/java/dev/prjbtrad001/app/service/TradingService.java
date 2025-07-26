package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.bot.*;
import dev.prjbtrad001.app.dto.Kline;
import lombok.experimental.UtilityClass;
import lombok.extern.jbosslog.JBossLog;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static dev.prjbtrad001.app.utils.LogUtils.log;

@JBossLog
@UtilityClass
public class TradingService {

  public static void analyzeMarket(SimpleTradeBot bot) {
    BotParameters parameters = bot.getParameters();
    List<Double> closePrices = new ArrayList<>();
    List<Double> volumes = new ArrayList<>();

    List<Kline> klines = BinanceService.getCandles(parameters.getBotType().toString(), parameters.getInterval(), parameters.getWindowResistanceSupport());

    klines.forEach(kline -> {
      closePrices.add(Double.parseDouble(kline.getClosePrice()));
      volumes.add(Double.parseDouble(kline.getVolume()));
    });

    double rsi = calculateRSI(last(closePrices, 15), 14);
    double sma9 = calculateAverage(last(closePrices, parameters.getSmaShort()));
    double sma21 = calculateAverage(last(closePrices, parameters.getSmaLong()));
    double currentVolume = volumes.getLast();
    double averageVolume = calculateAverage(volumes);

    double support = Collections.min(last(closePrices, 30));
    double resistance = Collections.max(last(closePrices, 30));
    double currentPrice = closePrices.getLast();

    String botTypeName = "[" + parameters.getBotType() + "] - ";
    double range = resistance - support;
    double tolerance = range * 0.1;

    boolean rsiOversold = rsi <= parameters.getRsiPurchase();
    boolean touchedSupport = currentPrice <= support + tolerance;
    boolean bullishTrend = (sma9 > sma21) || currentPrice > sma9 && currentPrice > sma21;
    boolean strongVolume = currentVolume >= averageVolume * parameters.getVolumeMultiplier();
    boolean weakVolume = currentVolume < averageVolume;

    log(botTypeName + "📊 Volume: " + (strongVolume ? "STRONG" : "WEAK") + " (Current Volume: " + currentVolume + " >= Average Volume: " + averageVolume * parameters.getVolumeMultiplier() + ")");
    log(botTypeName + "🔻 RSI Oversold: " + rsiOversold + " (" + rsi + " <= " + parameters.getRsiPurchase() + ")" + " - RSI: " + rsi);
    log(botTypeName + "📉 Bullish Trend: " + bullishTrend + " (SMA9: " + sma9 + " >  SMA21: " + sma21 + ")");
    log(botTypeName + "\uD83D\uDEE1\uFE0F Touched Support: " + touchedSupport + " (Current Price: " + currentPrice + " <= Support: " + (support + tolerance) + ")");

    double buyPoints = 0;
    if (rsiOversold) buyPoints += 1.0;
    if (bullishTrend) buyPoints += 1.0;
    if (touchedSupport) buyPoints += 0.8;
    if (strongVolume) buyPoints += 0.4;

    boolean shouldBuy = buyPoints >= 1.75;

    if (shouldBuy) {
      log(botTypeName + "🔵 BUY signal detected!");

      double quantity = parameters.getPurchaseAmount();
      if (parameters.getPurchaseStrategy().equals(PurchaseStrategy.PERCENTAGE)) {
        quantity = (Wallet.get() * parameters.getPurchaseAmount()) / 100;
      }

      double purchasePrice = currentPrice + bot.getStatus().getPurchasePrice();
      bot.setStatus(
        Status.builder()
          .purchasePrice(purchasePrice)
          .purchaseTime(Instant.now())
          .quantity(quantity)
          .lastPrice(currentPrice)
          .lastRsi(rsi)
          .lastSmaShort(sma9)
          .lastSmaLong(sma21)
          .actualSupport(support)
          .actualResistance(resistance)
          .lastVolume(currentVolume)
          .build());

      bot.buy(quantity);
      return;
    }

    boolean rsiOverbought = rsi >= parameters.getRsiSale();
    boolean touchedResistance = currentPrice >= resistance - tolerance;
    boolean bearishTrend = sma9 < sma21;

    log(botTypeName + "🔺 RSI Overbought: " + rsiOverbought + " (" + rsi + " >= " + parameters.getRsiSale() + ")" + " - RSI: " + rsi);
    log(botTypeName + "📈 Bearish Trend: " + bearishTrend + " (SMA9: " + sma9 + " < SMA21: " + sma21 + ")");
    log(botTypeName + "\uD83D\uDE80 Touched Resistance: " + touchedResistance + " (Current Price: " + currentPrice + " >= Resistance: " + (resistance - tolerance) + ")");

    double sellPoints = 0;
    if (rsiOverbought) sellPoints += 1.0;
    if (bearishTrend) sellPoints += 1.0;
    if (touchedResistance) sellPoints += 0.4;
    if (weakVolume) sellPoints += 0.4;
    boolean reachedStopLoss = false;
    boolean reachedTakeProfit = false;
    boolean isLong = bot.getStatus().isLong();

    if (isLong) {
      double purchasePrice = bot.getStatus().getPurchasePrice();
      double priceChangePercent = ((currentPrice - purchasePrice) / purchasePrice) * 100;

      reachedStopLoss = priceChangePercent <= -parameters.getStopLossPercent();
      reachedTakeProfit = priceChangePercent >= parameters.getTakeProfitPercent();

      log(botTypeName + "📉 Price change: " + String.format("%.2f", priceChangePercent) + "%");
      log(botTypeName + "⛔ Stop Loss reached: " + reachedStopLoss);
      log(botTypeName + "💰 Take Profit reached: " + reachedTakeProfit);
    }

    boolean shouldSell = sellPoints >= 1.75 || reachedStopLoss || reachedTakeProfit;

    if (shouldSell) {
      if (!isLong) {
        log(botTypeName + "🟡 SELL signal detected, but no position to sell!");
        return;
      }
      log(botTypeName + "🔴 SELL signal detected!");

      double criptoAmount = bot.getStatus().getQuantity() / bot.getStatus().getPurchasePrice();
      double realizedProfit = criptoAmount * currentPrice;

      bot.sell(realizedProfit);

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
    if (list.size() < n) throw new IllegalArgumentException("List too short");
    return list.subList(list.size() - n, list.size());
  }


}
