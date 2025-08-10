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
import java.util.List;

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

    if (hasRecentClosedPosition(status, TradingConstants.MIN_TRADE_INTERVAL_SECONDS)) {
      log("[" + parameters.getBotType() + "] - ⏳ Waiting for minimum interval between operations");
      return;
    }

    List<KlineDto> klines =
      tradingExecutor.getCandles(
        parameters.getBotType().toString(),
        parameters.getInterval(),
        parameters.getCandlesAnalyzed()
      );

    if (bot.isTradingPaused()) {
      log("[" + parameters.getBotType() + "] - ⛔ Trading paused until: " + bot.getPauseUntil() + " due to consecutive losses: (" + bot.getConsecutiveLosses() + ")");
      return;
    }

    if (!MarketFilter.isMarketFavorable(klines)) {
      log("[" + parameters.getBotType() + "] - 🚫 Market conditions unfavorable - no trading");
      return;
    }

    MarketAnalyzer marketAnalyzer = new MarketAnalyzer();
    MarketConditions conditions = marketAnalyzer.analyzeMarket(klines, parameters);
    boolean isDownTrend = isDownTrendMarket(conditions);

    if (isDownTrend && !status.isLong()) {
      log("[" + parameters.getBotType() + "] - 🔻 Strong downtrend detected - avoiding new positions");
      return;
    }

    logService.logSignals(bot, conditions, isDownTrend);
    if (!status.isLong()) {
      evaluateBuySignal(bot, conditions);
    } else {
      evaluateSellSignal(bot, conditions);
    }
  }

  private void evaluateBuySignal(SimpleTradeBot bot, MarketConditions conditions) {
    BotParameters parameters = bot.getParameters();
    String botTypeName = "[" + parameters.getBotType() + "] - ";

//    if (isDownTrend) {
//      boolean oversold = conditions.rsi().compareTo(parameters.getRsiPurchase()) <= 0;
//
//      BigDecimal supportFactor = BigDecimal.ONE.subtract(BigDecimal.valueOf(0.015));
//      boolean nearSupport = conditions.currentPrice().compareTo(
//        conditions.support().multiply(supportFactor)) <= 0;
//
//      boolean adequateVolume = conditions.currentVolume().compareTo(
//        conditions.averageVolume().multiply(BigDecimal.valueOf(0.8))) >= 0;
//
//      boolean potentialBounce = conditions.momentum().compareTo(BigDecimal.ZERO) > 0;
//
//      if ((oversold ? 1 : 0) + (nearSupport ? 1 : 0) + (adequateVolume ? 1 : 0) + (potentialBounce ? 1 : 0) >= 3) {
//        BigDecimal signalStrength = calculateSignalStrength(conditions);
//        BigDecimal reducedAmount = calculateOptimalBuyAmount(bot, conditions)
//          .multiply(signalStrength);
//
//        log(botTypeName + "🔵 BUY in Downtrend! Strength: " + signalStrength + " Value: " + reducedAmount);
//        executeBuyOrder(bot, reducedAmount);
//      } else {
//        log(botTypeName + "⚪ NO BUY signal in Downtrend!");
//      }
//      return;
//    }

    BigDecimal totalFees = BigDecimal.valueOf(0.2);
    BigDecimal potentialProfit = estimatePotentialProfit(conditions);

    if (potentialProfit.compareTo(totalFees) <= 0) {
      log(botTypeName + "⚠️ Potential profit too low compared to fees. Skipping trade.");
      return;
    }

    boolean rsiOversold = conditions.rsi().compareTo(parameters.getRsiPurchase()) <= 0;

    boolean ema8AboveEma21 = conditions.ema8().compareTo(conditions.ema21()) > 0;
    boolean bullishTrend = conditions.sma9().compareTo(conditions.sma21()) > 0 && ema8AboveEma21;

    boolean touchedSupport =
      conditions.currentPrice()
        .compareTo(conditions.support().multiply(BigDecimal.ONE.add(BigDecimal.valueOf(0.005)))) <= 0;

    boolean touchedBollingerLower = conditions.currentPrice().compareTo(conditions.bollingerLower()
      .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(0.02)))) <= 0;

    boolean strongVolume = conditions.currentVolume()
      .compareTo(conditions.averageVolume().multiply(parameters.getVolumeMultiplier())) >= 0;

    boolean positiveMonentum = conditions.momentum().compareTo(BigDecimal.ZERO) > 0;

    boolean lowVolatility = conditions.volatility().compareTo(BigDecimal.valueOf(3)) < 0;

    TradingSignals buySignals = TradingSignals.builder()
      .rsiCondition(rsiOversold)
      .trendCondition(bullishTrend)
      .volumeCondition(strongVolume)
      .priceCondition(touchedSupport || touchedBollingerLower)
      .momentumCondition(positiveMonentum)
      .volatilityCondition(lowVolatility)
      .extremeRsi(conditions.rsi().compareTo(BigDecimal.valueOf(70)) > 0)
      .extremeLowVolume(conditions.currentVolume().compareTo(conditions.averageVolume().multiply(BigDecimal.valueOf(0.2))) < 0)
      .strongDowntrend(conditions.priceSlope().compareTo(BigDecimal.valueOf(-0.0001)) < 0 && ema8AboveEma21)
      //Sell only signals
      .stopLoss(false)
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
      } else if (rsiOversold && positiveMonentum) {
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

  private void evaluateSellSignal(SimpleTradeBot bot, MarketConditions conditions) {
    BotParameters parameters = bot.getParameters();
    Status status = bot.getStatus();
    String botTypeName = "[" + parameters.getBotType() + "] - ";

    BigDecimal priceChangePercent = calculatePriceChangePercent(status, conditions.currentPrice());

    if (applyTrailingStop(bot, conditions)) {
      return;
    }

//    if (isDownTrend) {
//      // Cálculos de TP/SL dinâmicos baseados na volatilidade
//      BigDecimal volatilityFactor;
//      if (conditions.volatility().compareTo(BigDecimal.valueOf(3)) > 0) {
//        volatilityFactor = BigDecimal.valueOf(0.4); // Volatilidade alta - TP/SL mais curtos
//      } else if (conditions.volatility().compareTo(BigDecimal.valueOf(1.5)) > 0) {
//        volatilityFactor = BigDecimal.valueOf(0.6); // Volatilidade média
//      } else {
//        volatilityFactor = BigDecimal.valueOf(0.8); // Volatilidade baixa
//      }
//
//      BigDecimal adjustedTP = parameters.getTakeProfitPercent().multiply(volatilityFactor);
//      BigDecimal adjustedSL = parameters.getStopLossPercent().multiply(volatilityFactor);
//
//      // Take profit mais agressivo em downtrend forte
//      boolean priceWeakening = conditions.momentum().compareTo(BigDecimal.valueOf(-0.2)) < 0;
//      boolean strongDowntrend = conditions.priceSlope().compareTo(BigDecimal.valueOf(-0.05)) < 0;
//
//      if (priceWeakening && strongDowntrend) {
//        adjustedTP = adjustedTP.multiply(BigDecimal.valueOf(0.7));
//      }
//
//      boolean smallTakeProfit = priceChangePercent.compareTo(adjustedTP) >= 0;
//      boolean tightStopLoss = priceChangePercent.compareTo(adjustedSL.negate()) <= 0;
//
//      // Reversão de RSI como sinal adicional de saída
//      boolean rsiReversal = conditions.rsi().compareTo(BigDecimal.valueOf(55)) > 0 &&
//        priceChangePercent.compareTo(BigDecimal.valueOf(0.3)) > 0;
//
//      boolean timeout = checkPositionTimeout(bot, conditions, TradingConstants.POSITION_TIMEOUT_SECONDS / 3) &&
//        priceChangePercent.compareTo(BigDecimal.ZERO) >= 0;
//
//      log(botTypeName + "🔻 Downtrend detected");
//      log(botTypeName + "💰 Adjusted TP (>= " + adjustedTP + "%): " + smallTakeProfit + " (" + priceChangePercent + "%)");
//      log(botTypeName + "⛔ Adjusted SL (<= -" + adjustedSL + "%): " + tightStopLoss + " (" + priceChangePercent + "%)");
//      log(botTypeName + "🔄 RSI Reversal: " + rsiReversal);
//
//      if (smallTakeProfit || tightStopLoss || rsiReversal || timeout) {
//        String reason = smallTakeProfit ? "Take Profit" : (tightStopLoss ? "Stop Loss" : (timeout ? "Timeout" : "RSI Reversal"));
//        log(botTypeName + "🔴 SELL signal in downtrend! Reason: " + reason);
//        executeSellOrder(bot);
//        return;
//      }
//    }

    boolean rsiOverbought = conditions.rsi().compareTo(parameters.getRsiSale()) >= 0;
    boolean bearishTrend = conditions.sma9().compareTo(conditions.sma21()) < 0;
    boolean touchedResistance = conditions.currentPrice().compareTo(
      conditions.resistance().multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.005)))) >= 0;
    boolean touchedBollingerUpper = conditions.currentPrice().compareTo(
      conditions.bollingerUpper().multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.01)))) >= 0;
    boolean negativeMonentum = conditions.momentum().compareTo(BigDecimal.ZERO) < 0;

    boolean reachedTakeProfit = priceChangePercent.compareTo(parameters.getTakeProfitPercent()) >= 0;

    boolean positionTimeout =
      checkPositionTimeout(bot, conditions, TradingConstants.POSITION_TIMEOUT_SECONDS) &&
        priceChangePercent.compareTo(BigDecimal.valueOf(0.3)) >= 0;

    boolean reachedStopLoss = priceChangePercent.compareTo(parameters.getStopLossPercent().negate()) <= 0;

    // Minimum profit considering fees
    BigDecimal minProfitThreshold = BigDecimal.valueOf(0.3);

    log(botTypeName + String.format("📉 Current variation: %.2f%% (least for profit: %.2f%%)", priceChangePercent, minProfitThreshold));

    TradingSignals sellSignals = TradingSignals.builder()
      .rsiCondition(rsiOverbought)
      .trendCondition(bearishTrend)
      .priceCondition(touchedResistance || touchedBollingerUpper)
      .momentumCondition(negativeMonentum)
      .stopLoss(reachedStopLoss)
      .takeProfit(reachedTakeProfit || positionTimeout)
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
    status.setLastSellTime(LocalDateTime.now());
    bot.addTradeResult(profit.compareTo(BigDecimal.ZERO) > 0);

    log(botTypeName + String.format("💰 Profit after fees: R$%.2f (%.2f%%)", profit, profitPercent));
    log(botTypeName + String.format("💰 Accumulated profit: R$%.2f", totalProfit));
    log(botTypeName + "✅ Sale executed successfully");
  }

  private boolean checkPositionTimeout(SimpleTradeBot bot, MarketConditions conditions, int timeoutSeconds) {
    if (!bot.getStatus().isLong()) return false;

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

  private boolean isDownTrendMarket(MarketConditions conditions) {
    // Indicadores mais sensíveis para scalping
    boolean emaFastDown = conditions.ema8().compareTo(conditions.ema21()) < 0;
    boolean veryShortTermDown = conditions.currentPrice().compareTo(conditions.sma9()) < 0;

    // Análise de intensidade da queda
    BigDecimal slopeIntensity = conditions.priceSlope().abs();
    boolean steepDecline = conditions.priceSlope().compareTo(BigDecimal.valueOf(-0.0001)) < 0 &&
      slopeIntensity.compareTo(BigDecimal.valueOf(0.0005)) > 0;

    // Posição relativa nas bandas (mais sensível)
    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    BigDecimal positionInBand = conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP);
    boolean movingDownInBand = positionInBand.compareTo(BigDecimal.valueOf(0.5)) < 0 &&
      conditions.currentPrice().compareTo(conditions.bollingerMiddle()) < 0;

    // Momentum negativo recente (curto prazo)
    boolean negativeMomentum = conditions.momentum().compareTo(BigDecimal.valueOf(-0.05)) < 0;

    // Sistema de pontuação ponderado
    int score = 0;
    if (emaFastDown) score += 2;
    if (veryShortTermDown) score += 3;  // Maior peso para indicador mais rápido
    if (steepDecline) score += 4;       // Alto peso para quedas intensas
    if (movingDownInBand) score += 2;
    if (negativeMomentum) score += 3;

    // Limiar mais sensível para scalping
    return score >= 5;  // Ajuste este valor conforme necessário
  }

  private boolean applyTrailingStop(SimpleTradeBot bot, MarketConditions conditions) {
    Status status = bot.getStatus();
    BigDecimal currentProfit = calculatePriceChangePercent(status, conditions.currentPrice());
    BigDecimal taxCost = BigDecimal.valueOf(0.2);

    if (currentProfit.compareTo(taxCost.add(BigDecimal.valueOf(0.30))) > 0) {
      BigDecimal trailingLevel = currentProfit.multiply(BigDecimal.valueOf(0.8));
      trailingLevel = trailingLevel.max(taxCost.add(BigDecimal.valueOf(0.05)));

      if (status.getTrailingStopLevel() == null ||
        trailingLevel.compareTo(status.getTrailingStopLevel()) > 0) {
        status.setTrailingStopLevel(trailingLevel);
        log("[" + bot.getParameters().getBotType() + "] - 🔄 Trailing stop: " + trailingLevel.setScale(2, RoundingMode.HALF_UP) + "%");
      }
    }

    if (status.getTrailingStopLevel() != null &&
      currentProfit.compareTo(status.getTrailingStopLevel()) < 0 &&
      currentProfit.compareTo(taxCost) > 0) {
      log("[" + bot.getParameters().getBotType() + "] - 🔴 Trailing Stop executado: " + currentProfit + "%");
      executeSellOrder(bot);
      return true;
    }

    return false;
  }

  private boolean isEmergencyExit(MarketConditions conditions) {
    return conditions.priceSlope().compareTo(BigDecimal.valueOf(-0.15)) < 0 &&
      conditions.volatility().compareTo(BigDecimal.valueOf(3.5)) > 0;
  }

  private boolean hasRecentClosedPosition(Status status, int secondsAgo) {
    return
      status.getLastSellTime() != null
        && status.getLastSellTime().plusSeconds(secondsAgo).isAfter(LocalDateTime.now());
  }

  private BigDecimal estimatePotentialProfit(MarketConditions conditions) {
    BigDecimal volatilityFactor = conditions.volatility().multiply(BigDecimal.valueOf(0.3));

    BigDecimal distanceToResistance = conditions.resistance()
      .subtract(conditions.currentPrice())
      .divide(conditions.currentPrice(), 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));

    return volatilityFactor.add(distanceToResistance.multiply(BigDecimal.valueOf(0.2)));
  }

}