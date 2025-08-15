package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.bot.BotParameters;
import dev.prjbtrad001.app.bot.PurchaseStrategy;
import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.app.bot.Status;
import dev.prjbtrad001.app.core.*;
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
import java.util.ArrayList;
import java.util.List;

import static dev.prjbtrad001.app.core.MarketAnalyzer.calculateRSI;
import static dev.prjbtrad001.app.utils.LogUtils.log;
import static dev.prjbtrad001.infra.exception.ErrorCode.*;

@JBossLog
@ApplicationScoped
public class TradingService {

  @Inject
  TradingExecutor tradingExecutor;

  @Inject
  LogService logService;

  @Transactional
  public void analyzeMarket(SimpleTradeBot bot) {
    BotParameters parameters = bot.getParameters();
    Status status = bot.getStatus();
    String botTypeName = "[" + parameters.getBotType() + "] - ";

    List<KlineDto> klines =
      tradingExecutor.getCandles(
        parameters.getBotType().toString(),
        parameters.getInterval(),
        parameters.getCandlesAnalyzed()
      );

    if (bot.isTradingPaused()) {
      log(botTypeName + "⛔ Trading paused until: " + bot.getPauseUntil() + " due to consecutive losses: (" + bot.getConsecutiveLosses() + ")", true);
      return;
    }

    MarketAnalyzer marketAnalyzer = new MarketAnalyzer();
    MarketConditions conditions = marketAnalyzer.analyzeMarket(klines, parameters);
    boolean isDownTrend = isDownTrendMarket(conditions, klines, botTypeName);

    logService.logSignals(bot, conditions, isDownTrend);
    if (!status.isLong()) {
      if (isDownTrend) {
        evaluateBuySignalInDownTrend(bot, conditions, klines);
      } else {
        evaluateBuySignal(bot, conditions);
      }
    } else {
      if (isDownTrend) {
        evaluateSellSignalInDownTrend(bot, conditions, klines);
      } else {
        evaluateSellSignal(bot, conditions);
      }
    }
  }

  private void evaluateBuySignal(SimpleTradeBot bot, MarketConditions conditions) {
    BotParameters parameters = bot.getParameters();
    String botTypeName = "[" + parameters.getBotType() + "] - ";

    BigDecimal totalFees = BigDecimal.valueOf(0.2);
    BigDecimal potentialProfit = estimatePotentialProfit(conditions);

    if (potentialProfit.compareTo(totalFees) <= 0) {
      log(botTypeName + "⚠️ Potential profit too low compared to fees. Skipping trade.");
      return;
    }

    boolean rsiOversold = conditions.rsi().compareTo(parameters.getRsiPurchase()) <= 0;

    boolean ema8AboveEma21 = conditions.ema8().compareTo(conditions.ema21()) > 0;
    boolean bullishTrend = conditions.sma9().compareTo(conditions.sma21()) > 0 && ema8AboveEma21;

    boolean touchedSupport = conditions.currentPrice()
      .compareTo(conditions.support().multiply(BigDecimal.ONE.add(BigDecimal.valueOf(0.006)))) <= 0;

    boolean touchedBollingerLower = conditions.currentPrice().compareTo(conditions.bollingerLower()
      .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(0.02)))) <= 0;

    boolean strongVolume = conditions.currentVolume()
      .compareTo(conditions.averageVolume().multiply(parameters.getVolumeMultiplier())) >= 0;

    boolean acceleratingMomentum = conditions.momentum().compareTo(BigDecimal.valueOf(0.01)) > 0;

    boolean lowVolatility = conditions.volatility().compareTo(BigDecimal.valueOf(4)) < 0;

    boolean extremeRsi =
      conditions.rsi().compareTo(BigDecimal.valueOf(10)) < 0
      || conditions.rsi().compareTo(BigDecimal.valueOf(90)) > 0;

    TradingSignals buySignals = TradingSignals.builder()
      .rsiCondition(rsiOversold)
      .trendCondition(bullishTrend)
      .volumeCondition(strongVolume)
      .priceCondition(touchedSupport || touchedBollingerLower)
      .momentumCondition(acceleratingMomentum)
      .volatilityCondition(lowVolatility)
      .extremeRsi(extremeRsi)
      .extremeLowVolume(conditions.currentVolume().compareTo(conditions.averageVolume().multiply(BigDecimal.valueOf(0.2))) < 0)
      .strongDowntrend(conditions.priceSlope().compareTo(BigDecimal.valueOf(-0.0002)) < 0 && !ema8AboveEma21)
      //Sell only signals
      .stopLoss(false)
      .positionTimeout(false)
      .takeProfit(false)
      .emergencyExit(false)
      .minimumProfitReached(false)
      .build();

    if (buySignals.shouldBuy()) {
      String reason;
      if (rsiOversold && touchedSupport) {
        reason = "RSI + Support";
      } else if (rsiOversold && touchedBollingerLower) {
        reason = "RSI + Bollinger";
      } else if (bullishTrend && touchedSupport) {
        reason = "Bullish + Support";
      } else if (rsiOversold && acceleratingMomentum) {
        reason = "RSI + Momentum";
      } else if (bullishTrend && strongVolume) {
        reason = "Bullish + Volume";
      } else {
        reason = "Signal Score";
      }

      log(botTypeName + "🔵 BUY signal detected! Reason: " + reason);
      executeBuyOrder(bot, calculateOptimalBuyAmount(bot, conditions));
    } else {
      log(botTypeName + "⚪ Insufficient conditions for purchase.");
    }

    logService.processBuySignalLogs(bot, conditions, botTypeName);
  }

  private void evaluateBuySignalInDownTrend(SimpleTradeBot bot, MarketConditions conditions, List<KlineDto> klines) {
    BotParameters parameters = bot.getParameters();
    String botTypeName = "[" + parameters.getBotType() + "] - ";

    boolean extremeDrop = conditions.priceSlope().compareTo(BigDecimal.valueOf(-0.002)) < 0 &&
      conditions.volatility().compareTo(BigDecimal.valueOf(4.0)) > 0;

    if (extremeDrop) {
      log(botTypeName + "⚠️ Extreme drop detected. Avoiding buying during crash.");
      return;
    }

    boolean oversold = conditions.rsi().compareTo(parameters.getRsiPurchase()) <= 0;
    boolean nearSupport = conditions.currentPrice().compareTo(
      conditions.support().multiply(BigDecimal.ONE.add(BigDecimal.valueOf(0.005)))) <= 0;

    boolean volumeSignal = conditions.currentVolume().compareTo(
      conditions.averageVolume().multiply(BigDecimal.valueOf(1.2))) >= 0;

    boolean strongRejection = isPriceRejection(klines.getLast());
    boolean potentialBounce = conditions.momentum().compareTo(BigDecimal.valueOf(-0.05)) > 0 &&
      conditions.momentum().compareTo(BigDecimal.valueOf(0.1)) < 0;
    boolean rsiDivergence = hasRsiDivergence(klines, conditions);

    int signals = 0;
    if (oversold) signals += 2;
    if (nearSupport) signals += 3;
    if (volumeSignal) signals += 2;
    if (strongRejection) signals += 3;
    if (potentialBounce) signals += 1;
    if (rsiDivergence) signals += 3;

    boolean highVolumeWithoutReversal =
      volumeSignal && conditions.momentum().compareTo(BigDecimal.valueOf(-0.15)) < 0;

    if (highVolumeWithoutReversal) signals -= 3;

    BigDecimal bandPosition = calculatePositionInBollingerBand(conditions);
    if (bandPosition.compareTo(BigDecimal.valueOf(0.1)) <= 0) signals += 2;

    int requiredSignals = 7;

    log(botTypeName + "Signs of purchase in Downtrend: " + signals + "/" + requiredSignals);

    if (signals >= requiredSignals) {
      BigDecimal signalStrength = calculateScalpingSignalStrength(conditions)
        .multiply(BigDecimal.valueOf(0.5));

      BigDecimal reducedAmount = calculateOptimalBuyAmount(bot, conditions)
        .multiply(signalStrength);

      log(botTypeName + "🔵 BUY in Downtrend! Signals: " + signals + " Strength: " + signalStrength + " Value: " + reducedAmount);
      executeBuyOrder(bot, reducedAmount);
    } else {
      log(botTypeName + "⚪ NO BUY signal in Downtrend! Signal strength: " + signals);
    }
  }

  private void evaluateSellSignal(SimpleTradeBot bot, MarketConditions conditions) {
    BotParameters parameters = bot.getParameters();
    Status status = bot.getStatus();
    String botTypeName = "[" + parameters.getBotType() + "] - ";

    boolean isVeryNewPosition =
      status.getLastPurchaseTime() != null
        && status.getLastPurchaseTime()
        .plusSeconds(Math.max(bot.getIntervalInSeconds(), (int) (bot.getIntervalInSeconds() * (1.5 / conditions.volatility().doubleValue()))))
        .isAfter(LocalDateTime.now());

    if (isVeryNewPosition) {
      log(botTypeName + "⏳ Very recent position, awaiting before considering exit.");
      return;
    }

    BigDecimal priceChangePercent = calculatePriceChangePercent(status, conditions.currentPrice());

    if (applyTrailingStop(bot, conditions)) {
      return;
    }

    boolean rsiOverbought = conditions.rsi().compareTo(parameters.getRsiSale()) >= 0;
    boolean bearishTrend = conditions.sma9().compareTo(conditions.sma21()) < 0;
    boolean touchedResistance = conditions.currentPrice().compareTo(
      conditions.resistance().multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.005)))) >= 0;
    boolean touchedBollingerUpper = conditions.currentPrice().compareTo(
      conditions.bollingerUpper().multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.01)))) >= 0;
    boolean negativeMonentum = conditions.momentum().compareTo(BigDecimal.ZERO) < 0;

    boolean reachedTakeProfit = priceChangePercent.compareTo(parameters.getTakeProfitPercent()) >= 0;

    boolean positionTimeout = checkPositionTimeout(bot, conditions);

    boolean reachedStopLoss = priceChangePercent.compareTo(parameters.getStopLossPercent().negate()) <= 0;

    BigDecimal minProfitThreshold = BigDecimal.valueOf(0.3);

    log(botTypeName + String.format("📉 Current variation: %.2f%% (least for profit: %.2f%%)", priceChangePercent, minProfitThreshold));

    TradingSignals sellSignals = TradingSignals.builder()
      .rsiCondition(rsiOverbought)
      .trendCondition(bearishTrend)
      .priceCondition(touchedResistance || touchedBollingerUpper)
      .momentumCondition(negativeMonentum)
      .stopLoss(reachedStopLoss)
      .takeProfit(reachedTakeProfit)
      .positionTimeout(positionTimeout)
      .emergencyExit(isEmergencyExit(conditions))
      .minimumProfitReached(priceChangePercent.compareTo(minProfitThreshold) >= 0)

      //Buy only signals
      .volumeCondition(false)
      .volatilityCondition(false)
      .build();

    if (sellSignals.shouldSell()) {
      String reason;
      if (reachedStopLoss) {
        reason = "Stop Loss";
      } else if (reachedTakeProfit) {
        reason = "Take Profit";
      } else if (positionTimeout) {
        reason = "Timeout";
      } else if (isEmergencyExit(conditions)) {
        reason = "Emergency Exit";
      } else if (rsiOverbought) {
        reason = "RSI Overbought";
      } else if (bearishTrend && (touchedResistance || touchedBollingerUpper)) {
        reason = "Bearish + Resistance";
      } else {
        reason = "Signal Score";
      }

      log(botTypeName + "🔴 SELL signal detected! Reason: " + reason);
      executeSellOrder(bot);
    } else {
      log(botTypeName + "⚪ Maintaining current position.");
    }

    logService.processSellSignalLogs(bot, conditions, botTypeName);
  }

  private void evaluateSellSignalInDownTrend(SimpleTradeBot bot, MarketConditions conditions, List<KlineDto> klines) {
    BotParameters parameters = bot.getParameters();
    Status status = bot.getStatus();
    String botTypeName = "[" + parameters.getBotType() + "] - ";

    BigDecimal priceChangePercent = calculatePriceChangePercent(status, conditions.currentPrice());
    log(botTypeName + String.format("📉 Current variation: %.2f%% (least for profit: %.2f%%)", priceChangePercent, 0.3));

    boolean isVeryNewPosition =
      status.getLastPurchaseTime() != null
        && status.getLastPurchaseTime()
        .plusSeconds(Math.max(bot.getIntervalInSeconds() * 2, (int) (bot.getIntervalInSeconds() * (2.5 / conditions.volatility().doubleValue()))))
        .isAfter(LocalDateTime.now());

    if (isVeryNewPosition) {
      log(botTypeName + "⏳  Very recent position, awaiting before considering exit.");
      return;
    }

    BigDecimal volatilityFactor;
    if (conditions.volatility().compareTo(BigDecimal.valueOf(3)) > 0) {
      volatilityFactor = BigDecimal.valueOf(0.4);
    } else if (conditions.volatility().compareTo(BigDecimal.valueOf(1.5)) > 0) {
      volatilityFactor = BigDecimal.valueOf(0.6);
    } else {
      volatilityFactor = BigDecimal.valueOf(0.8);
    }

    BigDecimal adjustedTP = parameters.getTakeProfitPercent().multiply(volatilityFactor);
    BigDecimal adjustedSL = parameters.getStopLossPercent().multiply(volatilityFactor);

    boolean priceWeakening = conditions.momentum().compareTo(BigDecimal.valueOf(-0.15)) < 0;
    boolean strongDowntrend = conditions.priceSlope().compareTo(BigDecimal.valueOf(-0.05)) < 0;

    if (priceWeakening || strongDowntrend) {
      adjustedTP = adjustedTP.multiply(BigDecimal.valueOf(0.6));
    }

    boolean reversalPattern = hasRecentReversalPattern(klines);

    boolean strongMomentumNegativeTendency = conditions.momentum().compareTo(BigDecimal.valueOf(-0.25)) < 0;
    boolean tinyProfit = priceChangePercent.compareTo(BigDecimal.valueOf(0.15)) >= 0;
    boolean fullTakeProfit = priceChangePercent.compareTo(adjustedTP) >= 0;
    boolean stopLoss =
      priceChangePercent.compareTo(adjustedSL.negate()) <= 0
        && (strongMomentumNegativeTendency || checkConsistentDowntrend(klines, 3));

    boolean rsiReversal =
      conditions.rsi().compareTo(BigDecimal.valueOf(50)) > 0 &&
        priceChangePercent.compareTo(BigDecimal.valueOf(0.2)) > 0;

    boolean positionTimeout = checkPositionTimeout(bot, conditions);

    if (fullTakeProfit ||
      (stopLoss && !isAtSupportLevel(conditions)) ||
      rsiReversal ||
      positionTimeout ||
      reversalPattern ||
      (tinyProfit && strongDowntrend)) {

      String reason = fullTakeProfit ? "Take Profit" :
        (stopLoss ? "Stop Loss" + (strongMomentumNegativeTendency ? " (strong momentum)" : "") :
          (positionTimeout ? "Timeout" :
            (reversalPattern ? "Reversal pattern" :
              (rsiReversal ? "RSI Reversal" : "Tiny Profit in Strong Downtrend"))));

      log(botTypeName + "🔴 SELL signal in downtrend! Reason: " + reason);
      executeSellOrder(bot);
    }
  }

  private BigDecimal calculateOptimalBuyAmount(SimpleTradeBot bot, MarketConditions conditions) {
    BotParameters parameters = bot.getParameters();
    BigDecimal baseAmount = parameters.getPurchaseAmount();
    BigDecimal adjustmentFactor = BigDecimal.ONE;

    // Ajuste por volatilidade - mais agressivo para scalping (reação mais rápida)
    if (conditions.volatility().compareTo(BigDecimal.valueOf(1.5)) >= 0) {
      BigDecimal volatilityFactor =
        BigDecimal.ONE
          .subtract(conditions.volatility().multiply(BigDecimal.valueOf(0.06)));
      volatilityFactor = volatilityFactor.max(BigDecimal.valueOf(0.5));
      adjustmentFactor = adjustmentFactor.multiply(volatilityFactor);
    }

    // Ajuste por RSI - mantido mas com sensibilidade aumentada para scalping
    if (conditions.rsi().compareTo(BigDecimal.valueOf(35)) <= 0) {
      BigDecimal rsiBoost =
        BigDecimal.valueOf(1.4)
          .subtract(conditions.rsi().divide(BigDecimal.valueOf(35), 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(0.4)));
      adjustmentFactor = adjustmentFactor.multiply(rsiBoost);
    }

    // Ajuste por proximidade ao suporte - mais relevante para scalping
    BigDecimal priceToSupport = conditions.currentPrice().divide(conditions.support(), 8, RoundingMode.HALF_UP);
    if (priceToSupport.compareTo(BigDecimal.valueOf(1.01)) <= 0) {  // Até 1% acima do suporte (mais sensível)
      adjustmentFactor = adjustmentFactor.multiply(BigDecimal.valueOf(1.2));  // +20%
    }

    // Ajuste por posição nas Bandas de Bollinger - crucial para scalping
    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    BigDecimal pricePosition = conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP);

    if (pricePosition.compareTo(BigDecimal.valueOf(0.15)) <= 0) {  // Mais próximo da banda inferior
      adjustmentFactor = adjustmentFactor.multiply(BigDecimal.valueOf(1.15));  // +15%
    }

    // Redução mais agressiva durante tendência de baixa para scalping
    if (conditions.ema50().compareTo(conditions.ema100()) < 0 &&
      conditions.priceSlope().compareTo(BigDecimal.ZERO) < 0) {
      adjustmentFactor = adjustmentFactor.multiply(BigDecimal.valueOf(0.5));  // -50%
    }

    // Limites de segurança ajustados para scalping (entre 20% e 130% do valor base)
    BigDecimal marketBasedAmount = baseAmount.multiply(
      adjustmentFactor.max(BigDecimal.valueOf(0.2)).min(BigDecimal.valueOf(1.3))
    );

    // Aplica o ajuste baseado no histórico de operações (resultados anteriores)
    return bot.getAdjustedPositionSize(marketBasedAmount);
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

    log(botTypeName + "✅ Purchase executed successfully");
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
    bot.addTradeResult(profit.compareTo(BigDecimal.ZERO) > 0);

    log(botTypeName + String.format("💰 Profit after fees: R$%.2f (%.2f%%)", profit, profitPercent));
    log(botTypeName + String.format("💰 Accumulated profit: R$%.2f", totalProfit));
    log(botTypeName + "✅ Sale executed successfully");
  }

  private boolean checkPositionTimeout(SimpleTradeBot bot, MarketConditions conditions) {
    if (!bot.getStatus().isLong()) return false;

    int timeoutSeconds =
      switch (bot.getParameters().getInterval()) {
        case "1m" -> 600;
        case "3m" -> 1200;
        case "5m" -> 1800;
        default -> 900;
      };

    double volatilityFactor = Math.min(2.0, Math.max(0.5, conditions.volatility().doubleValue() / 2.0));
    int adjustedTimeout = (int) (timeoutSeconds / volatilityFactor);

    return bot.getStatus().getLastPurchaseTime()
      .plusSeconds(adjustedTimeout)
      .isBefore(LocalDateTime.now());
  }

  public static BigDecimal calculatePriceChangePercent(Status status, BigDecimal currentPrice) {
    if (status.getAveragePrice() == null || status.getAveragePrice().compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }

    return currentPrice.subtract(status.getAveragePrice())
      .divide(status.getAveragePrice(), 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));
  }

  private boolean isDownTrendMarket(MarketConditions conditions, List<KlineDto> klines, String botTypeName) {
    boolean emaFastDown = conditions.ema8().compareTo(conditions.ema21()) < 0;
    boolean veryShortTermDown = conditions.currentPrice().compareTo(conditions.sma9()) < 0;

    BigDecimal slopeIntensity = conditions.priceSlope().abs();
    boolean steepDecline = conditions.priceSlope().compareTo(BigDecimal.valueOf(-0.0001)) < 0 &&
      slopeIntensity.compareTo(BigDecimal.valueOf(0.0005)) > 0;

    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    BigDecimal positionInBand = conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP);
    boolean movingDownInBand = positionInBand.compareTo(BigDecimal.valueOf(0.5)) < 0 &&
      conditions.currentPrice().compareTo(conditions.bollingerMiddle()) < 0;

    boolean strongVolume =
      conditions.currentVolume().compareTo(
        conditions.averageVolume()
          .multiply(BigDecimal.valueOf(1.2))) > 0;

    boolean negativeMomentum = conditions.momentum().compareTo(BigDecimal.valueOf(-0.05)) < 0;

    int score = 0;
    if (strongVolume) score += 2;
    if (emaFastDown) score += 2;
    if (veryShortTermDown) score += 3;
    if (steepDecline) score += 4;
    if (movingDownInBand) score += 2;
    if (negativeMomentum) score += 3;
    if (hasConsistentDownCandles(klines, 5)) score += 2;

    return score >= 5;
  }

  private boolean hasConsistentDownCandles(List<KlineDto> klines, int periods) {
    int downCount = 0;
    for (int i = klines.size() - periods; i < klines.size(); i++) {
      BigDecimal close = new BigDecimal(klines.get(i).getClosePrice());
      BigDecimal prevClose = new BigDecimal(klines.get(i - 1).getClosePrice());
      if (close.compareTo(prevClose) < 0) downCount++;
    }
    return downCount >= periods * 0.7; // 70% das velas de baixa
  }

  private boolean applyTrailingStop(SimpleTradeBot bot, MarketConditions conditions) {
    Status status = bot.getStatus();
    BigDecimal currentProfit = calculatePriceChangePercent(status, conditions.currentPrice());
    BigDecimal taxCost = BigDecimal.valueOf(0.2);
    String botTypeName = "[" + bot.getParameters().getBotType() + "] - ";

    BigDecimal activationThreshold = taxCost.add(BigDecimal.valueOf(0.12));

    BigDecimal volatilityFactor;
    if (conditions.volatility().compareTo(BigDecimal.valueOf(2.0)) > 0) {
      volatilityFactor = BigDecimal.valueOf(0.95);
    } else if (conditions.volatility().compareTo(BigDecimal.valueOf(1.0)) > 0) {
      volatilityFactor = BigDecimal.valueOf(0.9);
    } else {
      volatilityFactor = BigDecimal.valueOf(0.85);
    }

    if (currentProfit.compareTo(activationThreshold) > 0) {
      BigDecimal trailingLevel = currentProfit.multiply(volatilityFactor);
      trailingLevel = trailingLevel.max(taxCost.add(BigDecimal.valueOf(0.05)));

      if (status.getTrailingStopLevel() == null ||
        trailingLevel.compareTo(status.getTrailingStopLevel()) > 0) {
        status.setTrailingStopLevel(trailingLevel);
        log(botTypeName + "🔄 Trailing stop: " + trailingLevel.setScale(2, RoundingMode.HALF_UP) + "%");
      }
    }

    boolean strongNegativeMomentum = conditions.momentum().compareTo(BigDecimal.valueOf(-0.08)) < 0 &&
      currentProfit.compareTo(taxCost.add(BigDecimal.valueOf(0.08))) > 0;

    if ((status.getTrailingStopLevel() != null &&
      currentProfit.compareTo(status.getTrailingStopLevel()) < 0 &&
      currentProfit.compareTo(taxCost) > 0) || strongNegativeMomentum) {

      String reason = strongNegativeMomentum ? "negative momentum" : "stop level";
      log(botTypeName + "🔴 Trailing Stop executed for " + reason + ": " + currentProfit + "%");
      executeSellOrder(bot);
      return true;
    }

    return false;
  }

  private boolean isEmergencyExit(MarketConditions conditions) {
    return conditions.priceSlope().compareTo(BigDecimal.valueOf(-0.15)) < 0 &&
      conditions.volatility().compareTo(BigDecimal.valueOf(3.5)) > 0;
  }

  private boolean isPriceRejection(KlineDto lastKline) {
    BigDecimal openPrice = new BigDecimal(lastKline.getOpenPrice()).setScale(8, RoundingMode.HALF_UP);
    BigDecimal closePrice = new BigDecimal(lastKline.getClosePrice()).setScale(8, RoundingMode.HALF_UP);
    BigDecimal highPrice = new BigDecimal(lastKline.getHighPrice()).setScale(8, RoundingMode.HALF_UP);
    BigDecimal lowPrice = new BigDecimal(lastKline.getLowPrice()).setScale(8, RoundingMode.HALF_UP);

    BigDecimal bodySize = openPrice.compareTo(closePrice) > 0
      ? openPrice.subtract(closePrice)
      : closePrice.subtract(openPrice);

    BigDecimal upperShadow = highPrice.subtract(openPrice.max(closePrice));
    BigDecimal lowerShadow = openPrice.min(closePrice).subtract(lowPrice);

    // Verifica rejeição de preço inferior (sombra inferior longa)
    boolean bullishRejection = lowerShadow.compareTo(bodySize.multiply(BigDecimal.valueOf(2))) > 0
      && closePrice.compareTo(openPrice) > 0;

    // Verifica rejeição de preço superior (sombra superior longa)
    boolean bearishRejection = upperShadow.compareTo(bodySize.multiply(BigDecimal.valueOf(2))) > 0
      && closePrice.compareTo(openPrice) < 0;

    return bullishRejection || bearishRejection;
  }

  private BigDecimal calculateScalpingSignalStrength(MarketConditions conditions) {
    int positiveSignals = 0;
    int totalSignals = 8; // Mais sinais para análise detalhada

    // RSI - mais sensível para scalping
    if (conditions.rsi().compareTo(BigDecimal.valueOf(40)) <= 0) positiveSignals++;
    if (conditions.rsi().compareTo(BigDecimal.valueOf(30)) <= 0) positiveSignals++;
    if (conditions.rsi().compareTo(BigDecimal.valueOf(20)) <= 0) positiveSignals++;

    // Proximidade ao suporte
    BigDecimal priceToSupport = conditions.currentPrice().divide(conditions.support(), 8, RoundingMode.HALF_UP);
    if (priceToSupport.compareTo(BigDecimal.valueOf(1.005)) <= 0) positiveSignals += 2; // Peso maior para suporte

    // Momentum positivo recente (comparado ao cenário anterior)
    if (conditions.momentum().compareTo(BigDecimal.valueOf(-0.05)) > 0) positiveSignals++;
    if (conditions.momentum().compareTo(BigDecimal.valueOf(0.0)) > 0) positiveSignals++;

    // Posição nas bandas de Bollinger
    BigDecimal bandPosition = calculatePositionInBollingerBand(conditions);
    if (bandPosition.compareTo(BigDecimal.valueOf(0.2)) <= 0) positiveSignals++;
    if (bandPosition.compareTo(BigDecimal.valueOf(0.1)) <= 0) positiveSignals++;

    // Cálculo da força (entre 0.3 e 1.0)
    BigDecimal signalRatio = BigDecimal.valueOf(positiveSignals)
      .divide(BigDecimal.valueOf(totalSignals), 8, RoundingMode.HALF_UP);

    return BigDecimal.valueOf(0.3).add(signalRatio.multiply(BigDecimal.valueOf(0.7)));
  }

  private BigDecimal estimatePotentialProfit(MarketConditions conditions) {
    BigDecimal volatilityWeight = conditions.rsi().compareTo(BigDecimal.valueOf(40)) < 0 ?
      BigDecimal.valueOf(0.4) : BigDecimal.valueOf(0.3);
    BigDecimal volatilityFactor = conditions.volatility().multiply(volatilityWeight);

    BigDecimal volumeMultiplier = conditions.currentVolume()
      .divide(conditions.averageVolume(), 8, RoundingMode.HALF_UP)
      .min(BigDecimal.valueOf(2.0)).max(BigDecimal.valueOf(0.5));

    BigDecimal distanceToResistance = conditions.resistance()
      .subtract(conditions.currentPrice())
      .divide(conditions.currentPrice(), 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100))
      .multiply(volumeMultiplier);

    BigDecimal trendComponent = conditions.ema8().compareTo(conditions.ema21()) > 0 ?
      BigDecimal.valueOf(0.2) : BigDecimal.ZERO;

    return volatilityFactor
      .add(distanceToResistance.multiply(BigDecimal.valueOf(0.2)))
      .add(trendComponent);
  }

  private boolean hasRecentReversalPattern(List<KlineDto> klines) {
    if (klines == null || klines.size() < 3) {
      return false;
    }

    // Obtém as últimas 3 velas para análise de padrões
    KlineDto currentCandle = klines.getLast();
    KlineDto previousCandle = klines.get(klines.size() - 2);
    KlineDto thirdCandle = klines.get(klines.size() - 3);

    // Calcula propriedades das velas
    boolean currentBullish = new BigDecimal(currentCandle.getClosePrice()).compareTo(
      new BigDecimal(currentCandle.getOpenPrice())) > 0;
    boolean previousBullish = new BigDecimal(previousCandle.getClosePrice()).compareTo(
      new BigDecimal(previousCandle.getOpenPrice())) > 0;
    boolean thirdBullish = new BigDecimal(thirdCandle.getClosePrice()).compareTo(
      new BigDecimal(thirdCandle.getOpenPrice())) > 0;

    // Padrão de estrela da manhã (morning star)
    boolean morningStar = !thirdBullish && !previousBullish && currentBullish &&
      isPriceRejection(previousCandle) &&
      new BigDecimal(currentCandle.getClosePrice()).compareTo(
        new BigDecimal(thirdCandle.getOpenPrice())) > 0;

    // Padrão de martelo (hammer) - vela com sombra inferior longa
    boolean hammer = isPriceRejection(currentCandle) &&
      new BigDecimal(currentCandle.getLowPrice()).compareTo(
        new BigDecimal(previousCandle.getLowPrice())) < 0 &&
      currentBullish;

    // Padrão de engolfo de baixa (bearish engulfing)
    boolean bearishEngulfing = !currentBullish && previousBullish &&
      new BigDecimal(currentCandle.getOpenPrice()).compareTo(
        new BigDecimal(previousCandle.getClosePrice())) > 0 &&
      new BigDecimal(currentCandle.getClosePrice()).compareTo(
        new BigDecimal(previousCandle.getOpenPrice())) < 0;

    return morningStar || hammer || bearishEngulfing;
  }

  private boolean checkConsistentDowntrend(List<KlineDto> klines, int periods) {
    if (klines.size() < periods + 1) return false;

    int downtrendCount = 0;
    for (int i = klines.size() - periods; i < klines.size(); i++) {
      BigDecimal currentClose = new BigDecimal(klines.get(i).getClosePrice());
      BigDecimal previousClose = new BigDecimal(klines.get(i - 1).getClosePrice());
      if (currentClose.compareTo(previousClose) < 0) {
        downtrendCount++;
      }
    }

    return downtrendCount >= periods * 0.7; // 70% das velas são de queda
  }

  private boolean isAtSupportLevel(MarketConditions conditions) {
    BigDecimal priceToSupport = conditions.currentPrice().divide(conditions.support(), 8, RoundingMode.HALF_UP);
    return priceToSupport.compareTo(BigDecimal.valueOf(1.01)) <= 0;
  }

  private boolean hasRsiDivergence(List<KlineDto> klines, MarketConditions conditions) {
    if (klines.size() < 10) return false;

    // Verifica se preço faz mínimos mais baixos enquanto RSI faz mínimos mais altos
    BigDecimal currentPrice = conditions.currentPrice();
    BigDecimal previousLow = new BigDecimal(klines.get(klines.size() - 3).getLowPrice());

    // Precisamos calcular o RSI anterior para comparar
    List<BigDecimal> previousPrices = new ArrayList<>();
    for (int i = 0; i < klines.size() - 2; i++) {
      previousPrices.add(new BigDecimal(klines.get(i).getClosePrice()));
    }

    BigDecimal previousRsi = calculateRSI(previousPrices, 14);

    return currentPrice.compareTo(previousLow) < 0 && conditions.rsi().compareTo(previousRsi) > 0;
  }

  private BigDecimal calculatePositionInBollingerBand(MarketConditions conditions) {
    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    return conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP);
  }

}