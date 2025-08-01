package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.bot.*;
import dev.prjbtrad001.app.core.MarketAnalyzer;
import dev.prjbtrad001.app.core.MarketConditions;
import dev.prjbtrad001.app.core.TradingSignals;
import dev.prjbtrad001.app.dto.KlineDto;
import dev.prjbtrad001.app.dto.TradeOrderDto;
import dev.prjbtrad001.domain.core.TradingExecutor;
import dev.prjbtrad001.infra.exception.TradeException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.jbosslog.JBossLog;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static dev.prjbtrad001.app.utils.LogUtils.log;
import static dev.prjbtrad001.infra.exception.ErrorCode.*;

@JBossLog
@ApplicationScoped
public class TradingService {

  @Inject
  TradingExecutor tradingExecutor;

  @Transactional
  public void analyzeMarket(SimpleTradeBot bot) {
    BotParameters parameters = bot.getParameters();
    Status status = bot.getStatus();

    List<KlineDto> klines = tradingExecutor.getCandles(parameters.getBotType().toString(), parameters.getInterval(), parameters.getWindowResistanceSupport());

    MarketAnalyzer marketAnalyzer = new MarketAnalyzer();
    MarketConditions conditions = marketAnalyzer.analyzeMarket(klines, parameters);

    String botTypeName = "[" + parameters.getBotType() + "] - ";
    BigDecimal range = conditions.resistance().subtract(conditions.support());
    BigDecimal tolerance = range.multiply(BigDecimal.valueOf(0.1));

    boolean rsiOversold =
      conditions.rsi()
        .compareTo(parameters.getRsiPurchase()) <= 0;

    boolean touchedSupport =
      conditions.currentPrice()
        .compareTo(conditions.support()
          .add(tolerance)) <= 0;

    boolean bullishTrend = conditions.sma9().compareTo(conditions.sma21()) > 0
      && conditions.currentPrice().compareTo(conditions.sma9()) > 0;

    boolean strongVolume =
      conditions.currentVolume()
        .compareTo(conditions.averageVolume()
          .multiply(parameters.getVolumeMultiplier())) >= 0;

    TradingSignals buySignals = TradingSignals.builder()
      .rsiCondition(rsiOversold)
      .trendCondition(bullishTrend)
      .volumeCondition(strongVolume)
      .priceCondition(touchedSupport)
      .stopLoss(false)
      .takeProfit(false)
      .build();

    log(botTypeName + "📊 Volume: " + (strongVolume ? "STRONG" : "WEAK") + " (Current Volume: " + conditions.currentVolume() + " >= Average Volume: " + conditions.averageVolume().multiply(parameters.getVolumeMultiplier()) + ")");
    log(botTypeName + "🔻 RSI Oversold: " + rsiOversold + " (" + conditions.rsi() + " <= " + parameters.getRsiPurchase() + ")" + " - RSI: " + conditions.rsi());
    log(botTypeName + "📉 Bullish Trend: " + bullishTrend + " (SMA9: " + conditions.sma9() + " >  SMA21: " + conditions.sma21() + ")");
    log(botTypeName + "\uD83D\uDEE1\uFE0F Touched Support: " + touchedSupport + " (Current Price: " + conditions.currentPrice() + " <= Support: " + (conditions.support().add(tolerance)) + ")");

    if (buySignals.shouldBuy()) {
      log(botTypeName + "🔵 BUY signal detected!");

      BigDecimal valueToBuy = parameters.getPurchaseAmount();
      if (parameters.getPurchaseStrategy().equals(PurchaseStrategy.PERCENTAGE)) {
        valueToBuy = tradingExecutor
          .getBalance()
          .orElseThrow(() -> new TradeException(BALANCE_NOT_FOUND.getMessage()))
          .balance()
          .multiply(parameters.getPurchaseAmount())
          .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
      }

      TradeOrderDto order = tradingExecutor
        .placeBuyOrder(bot.getParameters().getBotType().name(), valueToBuy)
        .orElseThrow(() -> new TradeException(FAILED_TO_PLACE_BUY_ORDER.getMessage()));

      // Calculates average price and total quantity
      BigDecimal totalQuantityExecuted = order.quantity();
      BigDecimal totalSpentBRL = order.totalSpentBRL();

      // Updates state considering previous purchases
      BigDecimal newTotalQuantity = status.getQuantity() != null ?
        status.getQuantity().add(totalQuantityExecuted) : totalQuantityExecuted;
      BigDecimal newTotalPurchased = status.getTotalPurchased() != null ?
        status.getTotalPurchased().add(totalSpentBRL) : totalSpentBRL;

      // Calculates new average price
      BigDecimal newAveragePrice = newTotalPurchased.divide(newTotalQuantity, 8, RoundingMode.HALF_UP);

      // Updates status
      status.setQuantity(newTotalQuantity);
      status.setTotalPurchased(newTotalPurchased);
      status.setAveragePrice(newAveragePrice);
      status.setLong(true);
      return;
    }

    boolean rsiOverbought = conditions.rsi().compareTo(parameters.getRsiSale()) >= 0;
    boolean touchedResistance = conditions.currentPrice().compareTo(conditions.resistance().subtract(tolerance)) >= 0;
    boolean weakVolume = conditions.currentVolume().compareTo(conditions.averageVolume()) < 0;
    boolean reachedStopLoss = false;
    boolean reachedTakeProfit = false;

    if (status.isLong()) {
      BigDecimal averagePrice = status.getAveragePrice();
      BigDecimal priceChangePercent =
        conditions.currentPrice()
          .subtract(averagePrice)
          .divide(averagePrice, 8, RoundingMode.HALF_UP)
          .multiply(BigDecimal.valueOf(100));

      reachedStopLoss = priceChangePercent.compareTo(parameters.getStopLossPercent().negate()) <= 0;
      reachedTakeProfit = priceChangePercent.compareTo(parameters.getTakeProfitPercent()) >= 0;

      log(botTypeName + String.format("📉 Price change: %.2f%%", priceChangePercent));
      log(botTypeName + "⛔ Stop Loss reached: " + reachedStopLoss);
      log(botTypeName + "💹 Take Profit reached: " + reachedTakeProfit);
    }

    TradingSignals sellSignals = TradingSignals.builder()
      .rsiCondition(rsiOverbought)
      .trendCondition(!bullishTrend)
      .volumeCondition(weakVolume)
      .priceCondition(touchedResistance)
      .stopLoss(reachedStopLoss)
      .takeProfit(reachedTakeProfit)
      .build();

    log(botTypeName + "🔺 RSI Overbought: " + rsiOverbought + " (" + conditions.rsi() + " >= " + parameters.getRsiSale() + ")" + " - RSI: " + conditions.rsi());
    log(botTypeName + "📈 Bearish Trend: " + !bullishTrend + " (SMA9: " + conditions.sma9() + " < SMA21: " + conditions.sma21() + ")");
    log(botTypeName + "\uD83D\uDE80 Touched Resistance: " + touchedResistance + " (Current Price: " + conditions.currentPrice() + " >= Resistance: " + (conditions.resistance().subtract(tolerance)) + ")");

    if (sellSignals.shouldSell()) {
      if (!status.isLong()) {
        log(botTypeName + "🟡 SELL signal detected, but no position to sell!");
        return;
      }
      TradeOrderDto order = tradingExecutor
        .placeSellOrder(bot.getParameters().getBotType().name())
        .orElseThrow(() -> new TradeException(FAILED_TO_PLACE_SELL_ORDER.getMessage()));

      // Calculates total amount received in the sale
      BigDecimal totalReceived = order.trades().stream()
        .map(t -> t.price().multiply(t.quantity()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

      // Calculate profit loss
      BigDecimal investedAmount = status.getTotalPurchased();
      BigDecimal profit = totalReceived.subtract(investedAmount);

      // Accumulates the total profit
      BigDecimal totalProfit = status.getProfit() != null ?
        status.getProfit().add(profit) : profit;

      // Reset status after sale
      status.setProfit(totalProfit);
      status.setQuantity(BigDecimal.ZERO);
      status.setTotalPurchased(BigDecimal.ZERO);
      status.setAveragePrice(BigDecimal.ZERO);
      status.setLong(false);

      log(botTypeName + "💰 Sale completed");
      log(botTypeName + "🔹 Trade profit: R$" + profit);
      log(botTypeName + "🔹 Total accumulated profit: R$" + totalProfit);
    } else {
      log(botTypeName + "🟡 No action recommended at this time.");
    }
  }


}
