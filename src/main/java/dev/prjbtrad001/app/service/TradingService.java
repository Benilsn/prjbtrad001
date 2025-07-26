package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.bot.*;
import dev.prjbtrad001.app.dto.Kline;
import lombok.Builder;
import lombok.experimental.UtilityClass;
import lombok.extern.jbosslog.JBossLog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static dev.prjbtrad001.app.utils.LogUtils.log;

@JBossLog
@UtilityClass
public class TradingService {

  public static void analyzeMarket(SimpleTradeBot bot) {
    List<BigDecimal> closePrices = new ArrayList<>();
    List<BigDecimal> volumes = new ArrayList<>();

    List<Kline> klines = BinanceService.getCandles(bot.getParameters().getBotType().toString(), bot.getParameters().getInterval(), bot.getParameters().getWindowResistanceSupport());

    klines.forEach(kline -> {
      closePrices.add(new BigDecimal(kline.getClosePrice()));
      volumes.add(new BigDecimal(kline.getVolume()));
    });

    MarketTrend marketTrend =
      MarketTrend.builder()
        .botTypeName("[" + bot.getParameters().getBotType() + "] - ")
        .rsi(calculateRSI(last(closePrices, 15), 14))
        .smaShort(calculateAverage(last(closePrices, bot.getParameters().getSmaShort())))
        .smaLong(calculateAverage(last(closePrices, bot.getParameters().getSmaLong())))
        .currentPrice(closePrices.getLast())
        .support(Collections.min(last(closePrices, 30)))
        .resistance(Collections.max(last(closePrices, 30)))
        .currentVolume(volumes.getLast())
        .averageVolume(calculateAverage(volumes))
        .tolerance(Collections.max(last(closePrices, 30)).subtract(Collections.min(last(closePrices, 30))).multiply(BigDecimal.valueOf(0.1)))
        .build();

    if (!analyzeBuy(marketTrend, bot))     analyzeSell(marketTrend, bot);
  }

  private static boolean analyzeBuy(MarketTrend marketTrend, SimpleTradeBot bot) {
    BotParameters parameters = bot.getParameters();
    boolean bought = false;

    boolean rsiOversold =
      marketTrend.rsi.compareTo(parameters.getRsiPurchase()) <= 0;

    boolean touchedSupport =
      marketTrend.currentPrice.compareTo(marketTrend.support.add(marketTrend.tolerance)) <= 0;

    boolean bullishTrend =
      marketTrend.smaShort
        .compareTo(marketTrend.smaLong) > 0
        || (marketTrend.currentPrice.compareTo(marketTrend.smaShort) > 0
        && marketTrend.currentPrice.compareTo(marketTrend.smaLong) > 0);

    boolean strongVolume =
      marketTrend
        .currentVolume
        .compareTo(marketTrend.averageVolume.multiply(parameters.getVolumeMultiplier())) >= 0;

    log(marketTrend.botTypeName + "📊 Volume: " + (strongVolume ? "STRONG" : "WEAK") + " (Current Volume: " + marketTrend.currentVolume + " >= Average Volume: " + marketTrend.averageVolume.multiply(parameters.getVolumeMultiplier()) + ")");
    log(marketTrend.botTypeName + "🔻 RSI Oversold: " + rsiOversold + " (" + marketTrend.rsi + " <= " + parameters.getRsiPurchase() + ")" + " - RSI: " + marketTrend.rsi);
    log(marketTrend.botTypeName + "📉 Bullish Trend: " + bullishTrend + " (SMA9: " + marketTrend.smaShort + " >  SMA21: " + marketTrend.smaLong + ")");
    log(marketTrend.botTypeName + "\uD83D\uDEE1\uFE0F Touched Support: " + touchedSupport + " (Current Price: " + marketTrend.currentPrice + " <= Support: " + (marketTrend.support.add(marketTrend.tolerance)) + ")");

    double buyPoints = 0;
    if (rsiOversold) buyPoints += 1.0;
    if (bullishTrend) buyPoints += 1.0;
    if (touchedSupport) buyPoints += 0.8;
    if (strongVolume) buyPoints += 0.4;

    boolean shouldBuy = buyPoints >= 1.75;

    if (shouldBuy) {
      log(marketTrend.botTypeName + "🔵 BUY signal detected!");

      BigDecimal valueToBuy = parameters.getPurchaseAmount();
      if (parameters.getPurchaseStrategy().equals(PurchaseStrategy.PERCENTAGE)) {
        valueToBuy = Wallet.get()
          .multiply(parameters.getPurchaseAmount())
          .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
      }

      BigDecimal quantity =
        valueToBuy
          .divide(marketTrend.currentPrice, 8, RoundingMode.HALF_UP);

      if (bot.getStatus().isLong()) {
        bot.getStatus().setQuantity(bot.getStatus().getQuantity().add(quantity));
      } else {
        bot.getStatus().setQuantity(quantity);
      }

      bot.getStatus().setPurchasePrice(marketTrend.currentPrice);
      bot.getStatus().setPurchaseTime(Instant.now());
      bot.getStatus().setLastPrice(marketTrend.currentPrice);
      bot.getStatus().setLastRsi(marketTrend.rsi);
      bot.getStatus().setLastSmaShort(marketTrend.smaShort);
      bot.getStatus().setLastSmaLong(marketTrend.smaLong);
      bot.getStatus().setActualSupport(marketTrend.support);
      bot.getStatus().setActualResistance(marketTrend.resistance);
      bot.getStatus().setLastVolume(marketTrend.currentVolume);

      bot.buy(valueToBuy);
      bought = true;
    }
    return bought;
  }

  private static void analyzeSell(MarketTrend marketTrend, SimpleTradeBot bot) {
    BotParameters parameters = bot.getParameters();

    boolean rsiOverbought =
      marketTrend.rsi.compareTo(parameters.getRsiSale()) >= 0;

    boolean touchedResistance =
      marketTrend.currentPrice.compareTo(
        marketTrend.resistance.subtract(marketTrend.tolerance)) >= 0;

    boolean bearishTrend =
      marketTrend.smaShort.compareTo(marketTrend.smaLong) < 0;

    boolean weakVolume =
      marketTrend.currentVolume.compareTo(marketTrend.averageVolume) < 0;

    log(marketTrend.botTypeName + "🔺 RSI Overbought: " + rsiOverbought + " (" + marketTrend.rsi + " >= " + parameters.getRsiSale() + ")" + " - RSI: " + marketTrend.rsi);
    log(marketTrend.botTypeName + "📈 Bearish Trend: " + bearishTrend + " (SMA9: " + marketTrend.smaShort + " < SMA21: " + marketTrend.smaLong + ")");
    log(marketTrend.botTypeName + "\uD83D\uDE80 Touched Resistance: " + touchedResistance + " (Current Price: " + marketTrend.currentPrice + " >= Resistance: " + (marketTrend.resistance.subtract(marketTrend.tolerance)) + ")");

    double sellPoints = 0;
    if (rsiOverbought) sellPoints += 1.0;
    if (bearishTrend) sellPoints += 1.0;
    if (touchedResistance) sellPoints += 0.4;
    if (weakVolume) sellPoints += 0.4;
    boolean reachedStopLoss = false;
    boolean reachedTakeProfit = false;
    boolean isLong = bot.getStatus().isLong();

    if (isLong) {
      BigDecimal purchasePrice = bot.getStatus().getPurchasePrice();
      BigDecimal priceChangePercent =
        ((marketTrend.currentPrice.subtract(purchasePrice)).divide(purchasePrice, 8, RoundingMode.HALF_UP)).multiply(BigDecimal.valueOf(100));

      reachedStopLoss = priceChangePercent.compareTo(parameters.getStopLossPercent().negate()) <= 0;
      reachedTakeProfit = priceChangePercent.compareTo(parameters.getTakeProfitPercent()) >= 0;

      log(marketTrend.botTypeName + "📉 Price change: " + String.format("%.2f", priceChangePercent) + "%");
      log(marketTrend.botTypeName + "⛔ Stop Loss reached: " + reachedStopLoss);
    }

    boolean shouldSell = sellPoints >= 1.75 || reachedStopLoss || reachedTakeProfit;

    if (shouldSell) {
      if (!isLong) {
        log(marketTrend.botTypeName + "🟡 SELL signal detected, but no position to sell!");
        return;
      }
      log(marketTrend.botTypeName + "🔴 SELL signal detected!");

      BigDecimal criptoAmount = bot.getStatus().getQuantity().divide(bot.getStatus().getPurchasePrice(), 8, RoundingMode.HALF_UP);
      BigDecimal realizedProfit = criptoAmount.multiply(marketTrend.currentPrice);

      bot.sell(realizedProfit);
    } else {
      log(marketTrend.botTypeName + "🟡 No action recommended at this time.");
    }
  }

  private static BigDecimal calculateRSI(List<BigDecimal> closePrices, int period) {
    BigDecimal gain = BigDecimal.ZERO;
    BigDecimal loss = BigDecimal.ZERO;

    for (int i = 1; i <= period; i++) {
      BigDecimal diff = closePrices.get(i).subtract(closePrices.get(i - 1));
      if (diff.compareTo(BigDecimal.ZERO) > 0) {
        gain = gain.add(diff);
      } else {
        loss = loss.add(diff.abs());
      }
    }

    BigDecimal periodBD = BigDecimal.valueOf(period);
    BigDecimal averageGain = gain.divide(periodBD, 8, RoundingMode.HALF_UP);
    BigDecimal averageLoss = loss.divide(periodBD, 8, RoundingMode.HALF_UP);

    if (averageLoss.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.valueOf(100);
    }

    BigDecimal rs = averageGain.divide(averageLoss, 8, RoundingMode.HALF_UP);
    BigDecimal rsi = BigDecimal.valueOf(100)
      .subtract(BigDecimal.valueOf(100)
        .divide(BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP));

    return rsi.setScale(8, RoundingMode.HALF_UP);
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
    if (list.size() < n) throw new IllegalArgumentException("List too short");
    return list.subList(list.size() - n, list.size());
  }

  @Builder
  public record MarketTrend(
    String botTypeName,
    BigDecimal rsi,
    BigDecimal smaShort,
    BigDecimal smaLong,
    BigDecimal currentPrice,
    BigDecimal support,
    BigDecimal resistance,
    BigDecimal currentVolume,
    BigDecimal averageVolume,
    BigDecimal tolerance

  ) {
    public MarketTrend {
      BigDecimal range = resistance.subtract(support);
      tolerance = range.multiply(BigDecimal.valueOf(0.1));
    }
  }

}
