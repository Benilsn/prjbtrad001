package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.bot.BotParameters;
import dev.prjbtrad001.app.bot.PurchaseStrategy;
import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.app.bot.Status;
import dev.prjbtrad001.app.core.MarketAnalyzer;
import dev.prjbtrad001.app.core.MarketConditions;
import dev.prjbtrad001.app.core.TradingConstants;
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
import java.time.LocalDateTime;
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
    String botTypeName = "[" + parameters.getBotType() + "] - ";

//    if (hasRecentTrade(status, TradingConstants.MIN_TRADE_INTERVAL_MINUTES)) {
//      log(botTypeName + "⏳ Waiting for minimum interval between operations");
//      return;
//    }

    List<KlineDto> klines = tradingExecutor.getCandles(
      parameters.getBotType().toString(),
      parameters.getInterval(),
      parameters.getWindowResistanceSupport()
    );

    MarketAnalyzer marketAnalyzer = new MarketAnalyzer();
    MarketConditions conditions = marketAnalyzer.analyzeMarket(klines, parameters);

    // If we don't have an open position, evaluate buy
    if (!status.isLong()) {
      evaluateBuySignal(bot, conditions);
    } else {
      evaluateSellSignal(bot, conditions);
    }
  }

  private void evaluateBuySignal(SimpleTradeBot bot, MarketConditions conditions) {
    BotParameters parameters = bot.getParameters();
    String botTypeName = "[" + parameters.getBotType() + "] - ";

    // Refined conditions for purchase
    boolean rsiOversold = conditions.rsi().compareTo(parameters.getRsiPurchase()) <= 0;

    boolean bullishTrend = conditions.sma9().compareTo(conditions.sma21()) > 0 &&
      conditions.ema8().compareTo(conditions.ema21()) > 0;

    boolean touchedSupport =
      conditions.currentPrice()
        .compareTo(
          conditions.support()
            .multiply(
              BigDecimal.ONE.add(BigDecimal.valueOf(0.005)))) <= 0;

    boolean touchedBollingerLower = conditions.currentPrice().compareTo(conditions.bollingerLower()
      .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(0.02)))) <= 0;

    boolean strongVolume = conditions.currentVolume()
      .compareTo(conditions.averageVolume().multiply(parameters.getVolumeMultiplier())) >= 0;

    boolean positiveMonentum = conditions.momentum().compareTo(BigDecimal.ZERO) > 0;

    boolean lowVolatility = conditions.volatility().compareTo(BigDecimal.valueOf(3)) < 0;

    // Print logs of conditions
    log(botTypeName + "🔻 RSI Oversold: " + rsiOversold + " (" + conditions.rsi() + " <= " + parameters.getRsiPurchase() + ")");
    log(botTypeName + "📈 Bullish Trend: " + bullishTrend);
    log(botTypeName + "🛡️ Touched Support: " + touchedSupport);
    log(botTypeName + "📊 Volume: " + (strongVolume ? "STRONG" : "WEAK"));
    log(botTypeName + "🧲 Touched Bollinger Lower: " + touchedBollingerLower);

    TradingSignals buySignals = TradingSignals.builder()
      .rsiCondition(rsiOversold)
      .trendCondition(bullishTrend || touchedSupport)
      .volumeCondition(strongVolume)
      .priceCondition(touchedSupport || touchedBollingerLower)
      .momentumCondition(positiveMonentum)
      .volatilityCondition(lowVolatility)
      .stopLoss(false)
      .takeProfit(false)
      .build();

    if (bot.getStatus().isLong()
      && touchedBollingerLower
      && conditions.currentPrice().compareTo(bot.getStatus().getAveragePrice().multiply(BigDecimal.valueOf(0.98))) <= 0) {
      log(botTypeName + "🔵 BUY signal detected - adding to position");
      BigDecimal additionalAmount =
        calculateOptimalBuyAmount(bot, conditions)
          .multiply(BigDecimal.valueOf(0.5));
      executeBuyOrder(bot, additionalAmount);
      return;
    }

    if (buySignals.shouldBuy()) {
      log(botTypeName + "🔵 BUY signal detected!");
      executeBuyOrder(bot, calculateOptimalBuyAmount(bot, conditions));
    } else {
      log(botTypeName + "⚪ Insufficient conditions for purchase.");
    }
  }

  private void evaluateSellSignal(SimpleTradeBot bot, MarketConditions conditions) {
    BotParameters parameters = bot.getParameters();
    Status status = bot.getStatus();
    String botTypeName = "[" + parameters.getBotType() + "] - ";

    // Calculation of current profit/loss
    BigDecimal priceChangePercent = calculatePriceChangePercent(status, conditions.currentPrice());
    log(botTypeName + String.format("📉 Current price variation: %.2f%%", priceChangePercent));

    boolean rsiOverbought = conditions.rsi().compareTo(parameters.getRsiSale()) >= 0;
    boolean bearishTrend = conditions.sma9().compareTo(conditions.sma21()) < 0;
    boolean touchedResistance = conditions.currentPrice().compareTo(
      conditions.resistance().multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.005)))) >= 0;
    boolean touchedBollingerUpper = conditions.currentPrice().compareTo(
      conditions.bollingerUpper().multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.01)))) >= 0;
    boolean negativeMonentum = conditions.momentum().compareTo(BigDecimal.ZERO) < 0;

    // Evaluation of stop loss and take profit
    boolean reachedStopLoss = priceChangePercent.compareTo(parameters.getStopLossPercent().negate()) <= 0;
    boolean reachedTakeProfit = priceChangePercent.compareTo(parameters.getTakeProfitPercent()) >= 0;

    // Time stop - if position has been open for a long time
    boolean positionTimeout = checkPositionTimeout(bot, TradingConstants.POSITION_TIMEOUT_MINUTES) &&
      priceChangePercent.compareTo(BigDecimal.valueOf(0.5)) >= 0; // At least 0.5% profit

    // Dynamic stop loss if profit is already above 1.5%
    boolean dynamicStopLoss = priceChangePercent.compareTo(BigDecimal.valueOf(1.5)) >= 0 &&
      priceChangePercent.compareTo(priceChangePercent.multiply(BigDecimal.valueOf(0.7))) <= 0;

    log(botTypeName + "🔺 RSI Overbought: " + rsiOverbought);
    log(botTypeName + "📉 Bearish Trend: " + bearishTrend);
    log(botTypeName + "🧲 Touched Resistance/Upper Band: " + (touchedResistance || touchedBollingerUpper));
    log(botTypeName + "⛔ Stop Loss: " + reachedStopLoss + ", Take Profit: " + reachedTakeProfit);
    log(botTypeName + "⏱️ Position Timeout: " + positionTimeout);
    log(botTypeName + "🔄 Dynamic Stop Loss: " + dynamicStopLoss);

    TradingSignals sellSignals = TradingSignals.builder()
      .rsiCondition(rsiOverbought)
      .trendCondition(bearishTrend)
      .volumeCondition(false)
      .priceCondition(touchedResistance || touchedBollingerUpper)
      .momentumCondition(negativeMonentum && priceChangePercent.compareTo(BigDecimal.valueOf(0.8)) >= 0)
      .volatilityCondition(false)
      .stopLoss(reachedStopLoss || dynamicStopLoss)
      .takeProfit(reachedTakeProfit || positionTimeout)
      .build();

    if (sellSignals.shouldSell()) {
      log(botTypeName + "🔴 SELL signal detected!");
      executeSellOrder(bot);
    } else {
      log(botTypeName + "⚪ Maintaining current position.");
    }
  }

  private BigDecimal calculateOptimalBuyAmount(SimpleTradeBot bot, MarketConditions conditions) {
    BotParameters parameters = bot.getParameters();
    BigDecimal baseAmount = parameters.getPurchaseAmount();

    // Adjusts value based on market volatility
    if (conditions.volatility().compareTo(BigDecimal.valueOf(4)) >= 0) {
      // High volatility - reduce position
      return baseAmount.multiply(BigDecimal.valueOf(0.7));
    } else if (conditions.rsi().compareTo(BigDecimal.valueOf(20)) <= 0) {
      // Very low RSI - buying opportunity
      return baseAmount.multiply(BigDecimal.valueOf(1.2));
    }

    return baseAmount;
  }

  private void executeBuyOrder(SimpleTradeBot bot, BigDecimal purchaseAmount) {
    BotParameters parameters = bot.getParameters();
    Status status = bot.getStatus();
    String botTypeName = "[" + parameters.getBotType() + "] - ";

    BigDecimal valueToBuy = purchaseAmount;
    if (parameters.getPurchaseStrategy().equals(PurchaseStrategy.PERCENTAGE)) {
      valueToBuy = tradingExecutor
        .getBalance()
        .orElseThrow(() -> new TradeException(BALANCE_NOT_FOUND.getMessage()))
        .balance()
        .multiply(purchaseAmount)
        .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
    }

    log(botTypeName + "💰 Executing buy order: " + valueToBuy);

    TradeOrderDto order = tradingExecutor
      .placeBuyOrder(parameters.getBotType().name(), valueToBuy)
      .orElseThrow(() -> new TradeException(FAILED_TO_PLACE_BUY_ORDER.getMessage()));

    BigDecimal totalQuantityExecuted = order.quantity();
    BigDecimal totalSpentBRL = order.totalSpentBRL();

    BigDecimal newTotalQuantity = status.getQuantity() != null ?
      status.getQuantity().add(totalQuantityExecuted) : totalQuantityExecuted;
    BigDecimal newTotalPurchased = status.getTotalPurchased() != null ?
      status.getTotalPurchased().add(totalSpentBRL) : totalSpentBRL;

    BigDecimal newAveragePrice = newTotalPurchased.divide(newTotalQuantity, 8, RoundingMode.HALF_UP);

    status.setQuantity(newTotalQuantity);
    status.setTotalPurchased(newTotalPurchased);
    status.setAveragePrice(newAveragePrice);
    status.setLastPurchaseTime(LocalDateTime.now());
    status.setLong(true);

    log(botTypeName + "✅ Purchase executed: Average price = " + newAveragePrice + ", Quantity = " + newTotalQuantity);
  }

  private void executeSellOrder(SimpleTradeBot bot) {
    BotParameters parameters = bot.getParameters();
    Status status = bot.getStatus();
    String botTypeName = "[" + parameters.getBotType() + "] - ";

    if (!status.isLong() || status.getQuantity() == null || status.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
      log(botTypeName + "⚠️ Attempted to sell without an open position!");
      return;
    }

    log(botTypeName + "💰 Executing sell order");

    TradeOrderDto order = tradingExecutor
      .placeSellOrder(parameters.getBotType().name())
      .orElseThrow(() -> new TradeException(FAILED_TO_PLACE_SELL_ORDER.getMessage()));

    // Calculate total profit
    BigDecimal totalReceived = order.trades().stream()
      .map(t -> t.price().multiply(t.quantity()))
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal investedAmount = status.getTotalPurchased();
    BigDecimal profit = totalReceived.subtract(investedAmount);
    BigDecimal totalProfit = status.getProfit() != null ?
      status.getProfit().add(profit) : profit;

    // Calculate profit percentage
    BigDecimal profitPercent = profit
      .divide(investedAmount, 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));

    // Reset status after sale
    status.setProfit(totalProfit);
    status.setQuantity(BigDecimal.ZERO);
    status.setTotalPurchased(BigDecimal.ZERO);
    status.setAveragePrice(BigDecimal.ZERO);
    status.setLastPurchaseTime(null);
    status.setLong(false);

    log(botTypeName + "✅ Sale executed successfully");
    log(botTypeName + String.format("💰 Profit: R$%.2f (%.2f%%)", profit, profitPercent));
    log(botTypeName + String.format("💰 Accumulated profit: R$%.2f", totalProfit));
  }

  private boolean hasRecentTrade(Status status, int minutesAgo) {
    return status.getLastPurchaseTime() != null &&
      status.getLastPurchaseTime().plusMinutes(minutesAgo).isAfter(LocalDateTime.now());
  }

  private boolean checkPositionTimeout(SimpleTradeBot bot, int timeoutMinutes) {
    if (!bot.getStatus().isLong()) return false;
    return bot.getStatus().getLastPurchaseTime()
      .plusMinutes(timeoutMinutes)
      .isBefore(LocalDateTime.now());
  }

  private BigDecimal calculatePriceChangePercent(Status status, BigDecimal currentPrice) {
    if (status.getAveragePrice() == null || status.getAveragePrice().compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }

    return currentPrice.subtract(status.getAveragePrice())
      .divide(status.getAveragePrice(), 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));
  }
}