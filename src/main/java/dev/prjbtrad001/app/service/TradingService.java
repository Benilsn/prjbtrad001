package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.bot.*;
import dev.prjbtrad001.app.dto.KlineDto;
import dev.prjbtrad001.app.dto.TradeOrderDto;
import dev.prjbtrad001.infra.exception.TradeException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static dev.prjbtrad001.app.utils.LogUtils.log;
import static dev.prjbtrad001.infra.exception.ErrorCode.*;

@JBossLog
@ApplicationScoped
public class TradingService {

  @Inject
  BinanceService binanceService;

  public void analyzeMarket(SimpleTradeBot bot) {
    BotParameters parameters = bot.getParameters();
    Status status = bot.getStatus();
    List<BigDecimal> closePrices = new ArrayList<>();
    List<BigDecimal> volumes = new ArrayList<>();

    List<KlineDto> klines = binanceService.getCandles(parameters.getBotType().toString(), parameters.getInterval(), parameters.getWindowResistanceSupport());

    klines.forEach(kline -> {
      closePrices.add(new BigDecimal(kline.getClosePrice()));
      volumes.add(new BigDecimal(kline.getVolume()));
    });

    BigDecimal rsi = calculateRSI(last(closePrices, 15), 14);
    BigDecimal sma9 = calculateAverage(last(closePrices, parameters.getSmaShort()));
    BigDecimal sma21 = calculateAverage(last(closePrices, parameters.getSmaLong()));
    BigDecimal currentVolume = volumes.getLast();
    BigDecimal averageVolume = calculateAverage(volumes);

    BigDecimal support = Collections.min(last(closePrices, 30));
    BigDecimal resistance = Collections.max(last(closePrices, 30));
    BigDecimal currentPrice = closePrices.getLast();

    String botTypeName = "[" + parameters.getBotType() + "] - ";
    BigDecimal range = resistance.subtract(support);
    BigDecimal tolerance = range.multiply(BigDecimal.valueOf(0.1));

    boolean rsiOversold = rsi.compareTo(parameters.getRsiPurchase()) <= 0;
    boolean touchedSupport = currentPrice.compareTo(support.add(tolerance)) <= 0;

    boolean bullishTrend =
      sma9.compareTo(sma21) > 0
        || (currentPrice.compareTo(sma9) > 0
        && currentPrice.compareTo(sma21) > 0);

    boolean strongVolume =
      currentVolume.compareTo(averageVolume.multiply(parameters.getVolumeMultiplier())) >= 0;

    log(botTypeName + "📊 Volume: " + (strongVolume ? "STRONG" : "WEAK") + " (Current Volume: " + currentVolume + " >= Average Volume: " + averageVolume.multiply(parameters.getVolumeMultiplier()) + ")");
    log(botTypeName + "🔻 RSI Oversold: " + rsiOversold + " (" + rsi + " <= " + parameters.getRsiPurchase() + ")" + " - RSI: " + rsi);
    log(botTypeName + "📉 Bullish Trend: " + bullishTrend + " (SMA9: " + sma9 + " >  SMA21: " + sma21 + ")");
    log(botTypeName + "\uD83D\uDEE1\uFE0F Touched Support: " + touchedSupport + " (Current Price: " + currentPrice + " <= Support: " + (support.add(tolerance)) + ")");

    double buyPoints = 0;
    if (rsiOversold) buyPoints += 1.0;
    if (bullishTrend) buyPoints += 1.0;
    if (touchedSupport) buyPoints += 0.4;
    if (strongVolume) buyPoints += 0.4;

    boolean shouldBuy = buyPoints >= 1.75;

    if (shouldBuy) {
      log(botTypeName + "🔵 BUY signal detected!");

      BigDecimal valueToBuy = parameters.getPurchaseAmount();
      if (parameters.getPurchaseStrategy().equals(PurchaseStrategy.PERCENTAGE))
        valueToBuy =
          binanceService
            .getBalance().orElseThrow(() -> new TradeException(BALANCE_NOT_FOUND.getMessage()))
            .balance()
            .multiply(parameters.getPurchaseAmount())
            .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

      TradeOrderDto order =
        binanceService.placeBuyOrder(bot.getParameters().getBotType().name(), valueToBuy)
          .orElseThrow(() -> new TradeException(FAILED_TO_PLACE_BUY_ORDER.getMessage()));

      status.setValueAtTheTimeOfLastPurchase(currentPrice);
      status.setTotalPurchased(order.totalSpentBRL());
      status.setQuantity(order.quantity());
      status.setLong(true);

      return;
    }

    boolean rsiOverbought = rsi.compareTo(parameters.getRsiSale()) >= 0;
    boolean touchedResistance = currentPrice.compareTo(resistance.subtract(tolerance)) >= 0;
    boolean bearishTrend = sma9.compareTo(sma21) < 0;
    boolean weakVolume = currentVolume.compareTo(averageVolume) < 0;

    log(botTypeName + "🔺 RSI Overbought: " + rsiOverbought + " (" + rsi + " >= " + parameters.getRsiSale() + ")" + " - RSI: " + rsi);
    log(botTypeName + "📈 Bearish Trend: " + bearishTrend + " (SMA9: " + sma9 + " < SMA21: " + sma21 + ")");
    log(botTypeName + "\uD83D\uDE80 Touched Resistance: " + touchedResistance + " (Current Price: " + currentPrice + " >= Resistance: " + (resistance.subtract(tolerance)) + ")");

    boolean reachedStopLoss = false;
    boolean reachedTakeProfit = false;

    if (status.isLong()) {
      BigDecimal valueAtTheTimeOfLastPurchase = status.getValueAtTheTimeOfLastPurchase();

      BigDecimal priceChangePercent =
        currentPrice
          .subtract(valueAtTheTimeOfLastPurchase)
          .divide(valueAtTheTimeOfLastPurchase, 8, RoundingMode.HALF_UP)
          .multiply(BigDecimal.valueOf(100));

      reachedStopLoss =
        priceChangePercent
          .compareTo(parameters.getStopLossPercent()
            .negate()) <= 0;

      reachedTakeProfit =
        priceChangePercent
          .compareTo(parameters.getTakeProfitPercent()) >= 0;

      log(botTypeName + "📉 Price change: " + String.format("%.2f", priceChangePercent) + "%");
      log(botTypeName + "⛔ Stop Loss reached: " + reachedStopLoss);
    }

    double sellPoints = 0;
    if (rsiOverbought) sellPoints += 1.0;
    if (bearishTrend) sellPoints += 1.0;
    if (touchedResistance) sellPoints += 0.4;
    if (weakVolume) sellPoints += 0.4;

    boolean shouldSell = sellPoints >= 1.75 || reachedStopLoss || reachedTakeProfit;

    if (shouldSell) {
      if (!status.isLong()) {
        log(botTypeName + "🟡 SELL signal detected, but no position to sell!");
        return;
      }
      log(botTypeName + "🔴 SELL signal detected!");

      TradeOrderDto order =
        binanceService.placeSellOrder(bot.getParameters().getBotType().name())
          .orElseThrow(() -> new TradeException(FAILED_TO_PLACE_SELL_ORDER.getMessage()));

      BigDecimal profit =
        order.quantity()
            .multiply(order.trades().getFirst().price())
              .subtract(status.getValueAtTheTimeOfLastPurchase());

      bot.getStatus().setLong(false);

      if (status.getProfit() != null){
        status.setProfit(status.getProfit().add(profit));
      } else{
        status.setProfit(profit);
      }

      log(botTypeName + "Profit from sell: " + status.getProfit());
    } else {
      log(botTypeName + "🟡 No action recommended at this time.");
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

}
