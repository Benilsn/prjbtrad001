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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static dev.prjbtrad001.app.core.TradingConstants.*;
import static dev.prjbtrad001.app.utils.LogUtils.*;
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

    if (bot.isTradingPaused()) {
      log(botTypeName + "⛔ Trading paused until: " + bot.getPauseUntil() + " due to consecutive losses: (" + bot.getConsecutiveLosses() + ")", true);
      return;
    }

    List<KlineDto> klines =
      tradingExecutor.getCandles(
        parameters.getBotType().toString(),
        parameters.getInterval(),
        parameters.getCandlesAnalyzed()
      );

    MarketAnalyzer marketAnalyzer = new MarketAnalyzer();
    MarketConditions conditions = marketAnalyzer.analyzeMarket(klines, parameters);
    boolean isDownTrend = isDownTrendMarket(conditions, botTypeName);

    if (!status.isLong()) {
      evaluateBuySignal(bot, conditions, klines.getLast(), isDownTrend);
    } else {
      evaluateSellSignal(bot, conditions, klines, isDownTrend);
    }
  }

  private void evaluateBuySignal(SimpleTradeBot bot, MarketConditions conditions, KlineDto lastKline, boolean isDownTrend) {
    BotParameters parameters = bot.getParameters();
    String botTypeName = "[" + parameters.getBotType() + "] - ";

    BigDecimal totalFees = BigDecimal.valueOf(0.2);
    BigDecimal potentialProfit = estimatePotentialProfit(conditions);

    if (potentialProfit.compareTo(totalFees) <= 0) {
      log(botTypeName + "⚠️ Potential profit too low compared to fees. Skipping trade.");
      return;
    }

    boolean buySignal = false;
    Queue<String> operationLogsList = new LinkedList<>();
    boolean rsiOversold = conditions.rsi().compareTo(parameters.getRsiPurchase()) <= 0;
    boolean ema8AboveEma21 = conditions.ema8().compareTo(conditions.ema21()) > 0;
    boolean bullishTrend = conditions.sma9().compareTo(conditions.sma21()) > 0 && ema8AboveEma21;
    boolean strongVolume = conditions.currentVolume().compareTo(conditions.averageVolume().multiply(parameters.getVolumeMultiplier())) >= 0;
    boolean touchedBollingerLower = conditions.currentPrice().compareTo(conditions.bollingerLower()
      .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(isDownTrend ? 0.01 : 0.02)))) <= 0;
    boolean positiveMomentum = conditions.momentum().compareTo(BigDecimal.ZERO) > 0;
    boolean touchedSupport =
      !isDownTrend
        ? conditions.currentPrice().compareTo(conditions.support().multiply(BigDecimal.ONE.add(BigDecimal.valueOf(0.005)))) <= 0
        : conditions.currentPrice().compareTo(conditions.support().multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.01)))) <= 0;

    boolean macdPositiveCrossover = conditions.macd() != null &&
      conditions.macd().compareTo(BigDecimal.ZERO) > 0 ||
      (conditions.macd().compareTo(BigDecimal.valueOf(-0.5)) > 0 &&
        conditions.macd().compareTo(conditions.macd().subtract(BigDecimal.valueOf(0.2))) > 0);

    boolean stochasticBullish = conditions.stochasticK() != null &&
      conditions.stochasticD() != null &&
      conditions.stochasticK().compareTo(BigDecimal.valueOf(25)) < 0 &&
      conditions.stochasticK().compareTo(conditions.stochasticD()) > 0;

    boolean lowVolatilityExpansion = conditions.atr() != null &&
      conditions.atr().compareTo(conditions.atr().multiply(BigDecimal.valueOf(0.8))) > 0;

    boolean positiveMoney = conditions.obv() != null &&
      conditions.obv().compareTo(BigDecimal.ZERO) > 0;

    operationLogsList.add(buildLog(botTypeName + String.format("🔎 Buy Signals: RSI %.2f [%s] | EMA8/EMA21 %.2f/%.2f [%s]",
      conditions.rsi(), rsiOversold ? "✅" : "❌",
      conditions.ema8(), conditions.ema21(), bullishTrend ? "✅" : "❌"), true));

    operationLogsList.add(buildLog(botTypeName + String.format("🔎 Price Signals: Price/Support %.2f/%.2f [%s] | Price/BolLower %.2f/%.2f [%s] | Vol Ratio %.2f [%s]",
      conditions.currentPrice(), conditions.support(), touchedSupport ? "✅" : "❌",
      conditions.currentPrice(), conditions.bollingerLower(), touchedBollingerLower ? "✅" : "❌",
      conditions.currentVolume().divide(conditions.averageVolume(), 4, RoundingMode.HALF_UP), strongVolume ? "✅" : "❌"), true));

    operationLogsList.add(buildLog(botTypeName + String.format("🔎 Advanced Indicators: Momentum %.2f [%s] | MACD %.4f [%s] | Stochastic K/D %.2f/%.2f [%s]",
      conditions.momentum(), positiveMomentum ? "✅" : "❌",
      conditions.macd(), macdPositiveCrossover ? "✅" : "❌",
      conditions.stochasticK(), conditions.stochasticD(), stochasticBullish ? "✅" : "❌"), true));

    if (isDownTrend) {
      boolean adequateVolume = conditions.currentVolume().compareTo(conditions.averageVolume().multiply(BigDecimal.valueOf(0.8))) >= 0;
      boolean volumeSpike = conditions.currentVolume().compareTo(conditions.averageVolume().multiply(BigDecimal.valueOf(1.5))) >= 0;
      boolean volumeSpikeWithoutReversal = volumeSpike && conditions.momentum().compareTo(BigDecimal.valueOf(-0.1)) < 0;
      boolean priceRejection = isPriceRejection(lastKline);
      boolean fastDrop = conditions.priceSlope().compareTo(BigDecimal.valueOf(-0.001)) < 0;

      int signals =
        (rsiOversold ? 1 : 0) +
          (touchedSupport ? 2 : 0) +
          (adequateVolume ? 1 : 0) +
          (volumeSpike ? 2 : 0) +
          (positiveMomentum ? 1 : 0) +
          (priceRejection ? 2 : 0) +
          (fastDrop && rsiOversold ? 1 : 0) +
          (macdPositiveCrossover ? 2 : 0) +
          (stochasticBullish ? 2 : 0) +
          (lowVolatilityExpansion ? 1 : 0) +
          (positiveMoney ? 1 : 0);

      if (volumeSpikeWithoutReversal) signals -= 2;
      if (conditions.rsi().compareTo(EXTREME_RSI_UP) >= 0
        || (conditions.rsi().compareTo(EXTREME_RSI_DOWN) <= 0
        && conditions.momentum().compareTo(BigDecimal.valueOf(-0.2)) < 0))
        signals -= 2;

      if (signals >= 4) {
        buySignal = true;
        BigDecimal signalStrength = calculateScalpingSignalStrength(conditions);
        BigDecimal reducedAmount = calculateOptimalBuyAmount(bot, conditions).multiply(signalStrength);

        operationLogsList.add(buildLog(botTypeName + "🔵 BUY in Downtrend! Signals: " + signals + " Strength: " + signalStrength + " Value: " + reducedAmount, true));
        executeBuyOrder(bot, reducedAmount);
      } else {
        operationLogsList.add(buildLog(botTypeName + "⚪ NO BUY signal in Downtrend! Signal strength: " + signals, true));
      }
    } else {
      boolean lowVolatility = conditions.volatility().compareTo(BigDecimal.valueOf(3)) < 0;

      TradingSignals buySignals =
        TradingSignals.builder()
          .rsiCondition(rsiOversold)
          .trendCondition(bullishTrend)
          .volumeCondition(strongVolume)
          .priceCondition(touchedSupport || touchedBollingerLower)
          .momentumCondition(positiveMomentum)
          .volatilityCondition(lowVolatility)
          .extremeRsi(conditions.rsi().compareTo(EXTREME_RSI_UP) >= 0)
          .extremeLowVolume(conditions.currentVolume().compareTo(conditions.averageVolume().multiply(BigDecimal.valueOf(0.2))) < 0)
          .strongDowntrend(conditions.priceSlope().compareTo(BigDecimal.valueOf(-0.0001)) < 0 && ema8AboveEma21)
          .priceRejection(macdPositiveCrossover)
          .bollBandPressure(stochasticBullish)
          .consecutiveCandles(lowVolatilityExpansion || positiveMoney)
          //Sell only signals
          .stopLoss(false)
          .positionTimeout(false)
          .takeProfit(false)
          .emergencyExit(false)
          .minimumProfitReached(false)
          .build();

      if (buySignals.shouldBuy()) {
        buySignal = true;
        String reason;
        if (rsiOversold && touchedSupport) {
          reason = "RSI + Support";
        } else if (rsiOversold && touchedBollingerLower) {
          reason = "RSI + Bollinger";
        } else if (bullishTrend && touchedSupport) {
          reason = "Bullish + Support";
        } else if (rsiOversold && positiveMomentum) {
          reason = "RSI + Momentum";
        } else if (bullishTrend && strongVolume) {
          reason = "Bullish + Volume";
        } else if (macdPositiveCrossover && stochasticBullish) {
          reason = "MACD + Stochastic";
        } else if (stochasticBullish && touchedSupport) {
          reason = "Stochastic + Support";
        } else {
          reason = "Signal Score";
        }

        operationLogsList.add(buildLog(botTypeName + "🔵 BUY signal detected! Reason: " + reason, true));
        executeBuyOrder(bot, calculateOptimalBuyAmount(bot, conditions));
      } else {
        operationLogsList.add(buildLog(botTypeName + "⚪ Insufficient conditions for purchase.", true));
      }
    }
    logMarketConditions(bot, conditions, isDownTrend, operationLogsList, buySignal);
  }

  private void evaluateSellSignal(SimpleTradeBot bot, MarketConditions conditions, List<KlineDto> klines, boolean isDownTrend) {
    BotParameters parameters = bot.getParameters();
    Status status = bot.getStatus();
    String botTypeName = "[" + parameters.getBotType() + "] - ";
    String trend = isDownTrend ? "downtrend" : "normal trend ";
    boolean sellSignal = false;
    Queue<String> operationLogsList = new LinkedList<>();

    BigDecimal priceChangePercent = calculatePriceChangePercent(status, conditions.currentPrice());

    if (applyTrailingStop(bot, conditions, isDownTrend, sellSignal, operationLogsList)) {
      return;
    }

    boolean macdNegativeCrossover = conditions.macd() != null &&
      (conditions.macd().compareTo(BigDecimal.ZERO) < 0 ||
        (conditions.macd().compareTo(BigDecimal.valueOf(0.5)) < 0 &&
          conditions.macd().compareTo(conditions.macd().add(BigDecimal.valueOf(0.2))) < 0));

    boolean stochasticOverbought = conditions.stochasticK() != null &&
      conditions.stochasticD() != null &&
      conditions.stochasticK().compareTo(BigDecimal.valueOf(75)) > 0 &&
      conditions.stochasticK().compareTo(conditions.stochasticD()) < 0;

    boolean highVolatility = conditions.atr() != null &&
      conditions.atr().compareTo(conditions.atr().multiply(BigDecimal.valueOf(1.5))) > 0;

    boolean negativeMoney = conditions.obv() != null &&
      conditions.obv().compareTo(BigDecimal.ZERO) < 0;

    boolean rsiOverbought = conditions.rsi().compareTo(parameters.getRsiSale()) >= 0;
    boolean bearishTrend = conditions.sma9().compareTo(conditions.sma21()) < 0;
    boolean touchedResistance = conditions.currentPrice().compareTo(
      conditions.resistance().multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.005)))) >= 0;
    boolean touchedBollingerUpper = conditions.currentPrice().compareTo(
      conditions.bollingerUpper().multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.01)))) >= 0;
    boolean negativeMomentum = conditions.momentum().compareTo(BigDecimal.ZERO) < 0;
    boolean reachedTakeProfit = priceChangePercent.compareTo(parameters.getTakeProfitPercent()) >= 0;
    boolean positionTimeout = checkPositionTimeout(bot, conditions, priceChangePercent);
    boolean reachedStopLoss = priceChangePercent.compareTo(parameters.getStopLossPercent().negate()) <= 0;

    operationLogsList.add(buildLog(botTypeName + String.format("🔎 Sell Signals: RSI %.2f [%s] | SMA9/SMA21 %.2f/%.2f [%s]",
      conditions.rsi(), rsiOverbought ? "✅" : "❌",
      conditions.sma9(), conditions.sma21(), bearishTrend ? "✅" : "❌"), true));

    operationLogsList.add(buildLog(botTypeName + String.format("🔎 Price Signals: Price/Resist %.2f/%.2f [%s] | Price/BolUpper %.2f/%.2f [%s] | Momentum %.2f [%s]",
      conditions.currentPrice(), conditions.resistance(), touchedResistance ? "✅" : "❌",
      conditions.currentPrice(), conditions.bollingerUpper(), touchedBollingerUpper ? "✅" : "❌",
      conditions.momentum(), negativeMomentum ? "✅" : "❌"), true));

    operationLogsList.add(buildLog(botTypeName + String.format("🔎 Advanced Indicators: MACD %.4f [%s] | Stochastic K/D %.2f/%.2f [%s]",
      conditions.macd(), macdNegativeCrossover ? "✅" : "❌",
      conditions.stochasticK(), conditions.stochasticD(), stochasticOverbought ? "✅" : "❌"), true));

    operationLogsList.add(buildLog(botTypeName + String.format("🔎 Stop/Profit: Change %.2f%% | TP %.2f%% [%s] | SL %.2f%% [%s] | Min Profit [%s]",
      priceChangePercent, parameters.getTakeProfitPercent(), reachedTakeProfit ? "✅" : "❌",
      parameters.getStopLossPercent(), reachedStopLoss ? "✅" : "❌",
      priceChangePercent.compareTo(BigDecimal.valueOf(0.4)) >= 0 ? "✅" : "❌"), true));

    if (isDownTrend) {
      BigDecimal volatilityFactor;
      if (conditions.volatility().compareTo(BigDecimal.valueOf(3.5)) > 0) {
        volatilityFactor = BigDecimal.valueOf(0.35);
      } else if (conditions.volatility().compareTo(BigDecimal.valueOf(2.5)) > 0) {
        volatilityFactor = BigDecimal.valueOf(0.5);
      } else if (conditions.volatility().compareTo(BigDecimal.valueOf(1.5)) > 0) {
        volatilityFactor = BigDecimal.valueOf(0.65);
      } else if (conditions.volatility().compareTo(BigDecimal.valueOf(0.8)) > 0) {
        volatilityFactor = BigDecimal.valueOf(0.8);
      } else if (conditions.volatility().compareTo(BigDecimal.valueOf(0.4)) > 0) {
        volatilityFactor = BigDecimal.valueOf(0.9);
      } else {
        volatilityFactor = BigDecimal.valueOf(1.0);
      }

      BigDecimal tpFactor = volatilityFactor;
      BigDecimal slFactor = volatilityFactor;

      boolean priceWeakening = conditions.momentum().compareTo(BigDecimal.valueOf(-0.15)) < 0;
      boolean strongDowntrend = conditions.priceSlope().compareTo(BigDecimal.valueOf(-0.05)) < 0;

      if (priceWeakening || strongDowntrend) {
        tpFactor = tpFactor.multiply(BigDecimal.valueOf(0.6));
        slFactor = slFactor.multiply(BigDecimal.valueOf(0.7));
      }

      BigDecimal adjustedTP = parameters.getTakeProfitPercent().multiply(tpFactor);
      BigDecimal adjustedSL = parameters.getStopLossPercent().multiply(slFactor);

      boolean reversalPattern = hasRecentReversalPattern(klines, conditions);
      boolean strongMomentumNegativeTendency = conditions.momentum().compareTo(BigDecimal.valueOf(-0.25)) < 0;
      boolean tinyProfit = priceChangePercent.compareTo(MIN_PROFIT_THRESHOLD) >= 0;
      boolean fullTakeProfit = priceChangePercent.compareTo(adjustedTP) >= 0;
      boolean enhancedSellSignal =
        strongMomentumNegativeTendency
          && checkConsistentDowntrend(klines, 3)
          && !isAtSupportLevel(conditions);

      boolean stopLoss = priceChangePercent.compareTo(adjustedSL.negate()) <= 0;

      boolean rsiReversal =
        conditions.rsi().compareTo(BigDecimal.valueOf(50)) > 0
          && priceChangePercent.compareTo(BigDecimal.valueOf(0.2)) > 0;

      boolean timeout = checkPositionTimeout(bot, conditions, priceChangePercent);

      // Novos sinais de venda
      boolean strongTechnicalSellSignal =
        (macdNegativeCrossover && stochasticOverbought) ||
          (highVolatility && negativeMoney) ||
          (stochasticOverbought && conditions.rsi().compareTo(BigDecimal.valueOf(65)) > 0);

      if (fullTakeProfit
        || stopLoss
        || rsiReversal
        || timeout
        || reversalPattern
        || (tinyProfit && strongDowntrend)
        || enhancedSellSignal
        || (tinyProfit && strongTechnicalSellSignal)) {

        sellSignal = true;
        String reason = fullTakeProfit ? "Take Profit" :
          stopLoss ? "Stop Loss" :
            timeout ? "Timeout" :
              reversalPattern ? "Reversal Pattern" :
                rsiReversal ? "RSI Reversal" :
                  strongTechnicalSellSignal ? "Technical Sell Signals" :
                    enhancedSellSignal ? "Enhanced Sell Signal" :
                      "Tiny Profit in Strong Downtrend";

        operationLogsList.add(buildLog(botTypeName + "🔴 SELL signal in downtrend! Reason: " + reason, true));
        executeSellOrder(bot);
      }
    } else {
      boolean isEmergencyExit = isEmergencyExit(conditions);

      TradingSignals sellSignals =
        TradingSignals.builder()
          .rsiCondition(rsiOverbought)
          .trendCondition(bearishTrend)
          .priceCondition(touchedResistance || touchedBollingerUpper)
          .momentumCondition(negativeMomentum)
          .stopLoss(reachedStopLoss)
          .takeProfit(reachedTakeProfit)
          .positionTimeout(positionTimeout)
          .emergencyExit(isEmergencyExit)
          .minimumProfitReached(priceChangePercent.compareTo(MIN_PROFIT_THRESHOLD) >= 0)
          .priceRejection(macdNegativeCrossover)
          .bollBandPressure(stochasticOverbought)
          .consecutiveCandles(highVolatility || negativeMoney)
          //Buy only signals
          .volumeCondition(false)
          .volatilityCondition(false)
          .build();

      if (sellSignals.shouldSell()) {
        sellSignal = true;
        String reason;
        if (reachedStopLoss) {
          reason = "Stop Loss";
        } else if (reachedTakeProfit) {
          reason = "Take Profit";
        } else if (positionTimeout) {
          reason = "Timeout";
        } else if (isEmergencyExit) {
          reason = "Emergency Exit";
        } else if (rsiOverbought && macdNegativeCrossover) {
          reason = "RSI + MACD";
        } else if (stochasticOverbought && touchedResistance) {
          reason = "Stochastic + Resistance";
        } else if (rsiOverbought) {
          reason = "RSI Overbought";
        } else if (bearishTrend && (touchedResistance || touchedBollingerUpper)) {
          reason = "Bearish + Resistance";
        } else {
          reason = "Signal Score";
        }

        operationLogsList.add(buildLog(botTypeName + "🔴 SELL signal detected! Reason: " + reason, true));
        executeSellOrder(bot);
        return;
      }
    }
    operationLogsList.add(buildLog(botTypeName + "⚪ No SELL signal in " + trend + ", maintaining current position.", true));
    logMarketConditions(bot, conditions, isDownTrend, operationLogsList, sellSignal);
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

  private boolean checkPositionTimeout(SimpleTradeBot bot, MarketConditions conditions, BigDecimal priceChangePercent) {
    if (!bot.getStatus().isLong()) return false;

    boolean absoluteMaximumTimeReached =
      bot.getStatus().getLastPurchaseTime()
        .plusMinutes(120)
        .isBefore(LocalDateTime.now());

    if (absoluteMaximumTimeReached) {
      return true;
    }

    boolean strongPositiveMomentum = conditions.momentum().compareTo(BigDecimal.valueOf(0.2)) > 0;

    boolean significantProfit = priceChangePercent.compareTo(BigDecimal.valueOf(0.8)) > 0;

    boolean extendedTimeReached =
      bot.getStatus().getLastPurchaseTime()
        .plusMinutes(90)
        .isBefore(LocalDateTime.now());

    if (extendedTimeReached && (strongPositiveMomentum && significantProfit)) {
      return true;
    }

    double volatilityFactor = Math.min(2.0, Math.max(0.5, conditions.volatility().doubleValue() / 2.0));
    int adjustedTimeout = (int) (TradingConstants.POSITION_TIMEOUT_SECONDS / volatilityFactor);

    return
      bot.getStatus().getLastPurchaseTime()
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

  private boolean isDownTrendMarket(MarketConditions conditions, String botTypeName) {
    boolean emaFastDown = conditions.ema8().compareTo(conditions.ema21()) < 0;
    boolean emaSlowDown = conditions.ema21().compareTo(conditions.ema50()) < 0;
    boolean veryShortTermDown = conditions.currentPrice().compareTo(conditions.sma9()) < 0;

    // Análise de declive de preço com maior precisão
    BigDecimal slopeIntensity = conditions.priceSlope().abs();
    boolean steepDecline = conditions.priceSlope().compareTo(
      TradingConstants.DOWNTREND_THRESHOLD.negate()) < 0 &&
      slopeIntensity.compareTo(TradingConstants.DOWNTREND_THRESHOLD.multiply(BigDecimal.valueOf(2))) > 0;

    // Análise de volume aprimorada
    boolean volumeDecline = conditions.currentVolume().compareTo(
      conditions.averageVolume().multiply(BigDecimal.valueOf(0.7))) < 0;
    boolean volumeSpike = conditions.currentVolume().compareTo(
      conditions.averageVolume().multiply(BigDecimal.valueOf(1.8))) > 0;

    // Posição nas bandas de Bollinger com métrica refinada
    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    BigDecimal positionInBand = conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP);
    boolean movingDownInBand = positionInBand.compareTo(BigDecimal.valueOf(0.4)) < 0 &&
      conditions.currentPrice().compareTo(conditions.bollingerMiddle()) < 0;

    // Momentum e MACD para confirmação de tendência
    boolean negativeMomentum = conditions.momentum().compareTo(BigDecimal.valueOf(-0.08)) < 0;
    boolean strongNegativeMomentum = conditions.momentum().compareTo(BigDecimal.valueOf(-0.18)) < 0;
    boolean macdNegative = conditions.macd() != null && conditions.macd().compareTo(BigDecimal.ZERO) < 0;

    // Stochastic para confirmação de sobrecompra/sobrevenda
    boolean stochasticOversold = conditions.stochasticK() != null &&
      conditions.stochasticK().compareTo(BigDecimal.valueOf(20)) < 0;

    // Cálculo de pontuação com mais indicadores
    int score = 0;
    if (emaFastDown) score += 2;
    if (emaSlowDown) score += 2;
    if (veryShortTermDown) score += 3;
    if (steepDecline) score += 4;
    if (movingDownInBand) score += 2;
    if (conditions.currentPrice().compareTo(conditions.bollingerMiddle().multiply(
      BigDecimal.valueOf(0.995))) < 0) score += 2;
    if (negativeMomentum) score += 2;
    if (strongNegativeMomentum) score += 3;
    if (volumeDecline) score += 1;
    if (volumeSpike && negativeMomentum) score += 3;
    if (macdNegative) score += 2;
    if (stochasticOversold) score -= 1; // Potencial reversão

    // Verifica crossover EMA (cruzamento para baixo é mais bearish)
    if (conditions.ema8().compareTo(conditions.ema21().multiply(new BigDecimal("0.998"))) < 0) {
      score += 3;
    }

    // Ajuste dinâmico do threshold baseado na volatilidade
    int threshold = TradingConstants.DOWNTREND_SCORE_THRESHOLD;
    if (conditions.volatility().compareTo(BigDecimal.valueOf(2.5)) > 0) {
      threshold += 2;
    } else if (conditions.volatility().compareTo(BigDecimal.valueOf(0.5)) < 0) {
      threshold -= 1;
    }

    log(botTypeName + "Downtrend score: " + score + " (threshold: " + threshold + ")", true);
    return score >= threshold;
  }

  private boolean applyTrailingStop(SimpleTradeBot bot, MarketConditions conditions, boolean isDownTrend, boolean sellSignal, Queue<String> operationLogsList) {
    Status status = bot.getStatus();
    BigDecimal currentProfit = calculatePriceChangePercent(status, conditions.currentPrice());
    BigDecimal taxCost = BigDecimal.valueOf(0.25); // Aumentado para margem de segurança
    String botTypeName = "[" + bot.getParameters().getBotType() + "] - ";

    BigDecimal activationThreshold = taxCost.add(BigDecimal.valueOf(0.15)); // Aumentado

    LocalDateTime purchaseTime = status.getLastPurchaseTime();
    long minutesHeld = purchaseTime != null
      ? java.time.Duration.between(purchaseTime, LocalDateTime.now()).toMinutes()
      : 0;

    BigDecimal timeAdjustment =
      minutesHeld > 60 ? BigDecimal.valueOf(0.92)
        : minutesHeld > 30 ? BigDecimal.valueOf(0.95)
        : minutesHeld > 15 ? BigDecimal.valueOf(0.97)
        : BigDecimal.valueOf(0.99);

    BigDecimal volatilityFactor;
    if (conditions.volatility().compareTo(BigDecimal.valueOf(3.0)) > 0) {
      volatilityFactor = BigDecimal.valueOf(0.65).multiply(timeAdjustment);
    } else if (conditions.volatility().compareTo(BigDecimal.valueOf(2.0)) > 0) {
      volatilityFactor = BigDecimal.valueOf(0.70).multiply(timeAdjustment);
    } else if (conditions.volatility().compareTo(BigDecimal.valueOf(1.0)) > 0) {
      volatilityFactor = BigDecimal.valueOf(0.75).multiply(timeAdjustment);
    } else {
      volatilityFactor = BigDecimal.valueOf(0.80).multiply(timeAdjustment);
    }

    // Verificar tendência para ajuste
    boolean positiveTrend = conditions.ema8().compareTo(conditions.ema21()) > 0;
    if (positiveTrend) {
      volatilityFactor = volatilityFactor.multiply(BigDecimal.valueOf(0.95));
    }

    // Só ativa o trailing quando o lucro for significativo
    if (currentProfit.compareTo(activationThreshold) > 0) {
      BigDecimal trailingLevel = currentProfit.multiply(volatilityFactor);
      trailingLevel = trailingLevel.max(taxCost.add(BigDecimal.valueOf(0.1)));

      if (status.getTrailingStopLevel() == null ||
        trailingLevel.compareTo(status.getTrailingStopLevel()) > 0) {
        status.setTrailingStopLevel(trailingLevel);
        operationLogsList.add(buildLog(botTypeName + "🔄 Trailing stop: " + trailingLevel.setScale(2, RoundingMode.HALF_UP) + "%", true));
      }
    }

    boolean strongNegativeMomentum = conditions.momentum().compareTo(BigDecimal.valueOf(-0.12)) < 0 &&
      currentProfit.compareTo(taxCost.add(BigDecimal.valueOf(0.15))) > 0;

    boolean highRsi = conditions.rsi().compareTo(BigDecimal.valueOf(70)) > 0 &&
      currentProfit.compareTo(taxCost.add(BigDecimal.valueOf(0.2))) > 0;

    boolean volumeDropOff = conditions.currentVolume().compareTo(
      conditions.averageVolume().multiply(BigDecimal.valueOf(0.5))) < 0 &&
      currentProfit.compareTo(taxCost.add(BigDecimal.valueOf(0.2))) > 0;

    if ((status.getTrailingStopLevel() != null &&
      currentProfit.compareTo(status.getTrailingStopLevel()) < 0 &&
      currentProfit.compareTo(taxCost) > 0) || strongNegativeMomentum || highRsi || volumeDropOff) {

      sellSignal = true;
      String reason = strongNegativeMomentum
        ? "negative momentum"
        : highRsi
        ? "High RSI"
        : volumeDropOff
        ? "Volume drop-off" : "Stop level";

      operationLogsList.add(buildLog(botTypeName + "🔴 Trailing Stop executado por" + " " + reason + ": " + currentProfit + "%", true));
      executeSellOrder(bot);
    }

    logMarketConditions(bot, conditions, isDownTrend, operationLogsList, sellSignal);
    return sellSignal;
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

    boolean bullishRejection = lowerShadow.compareTo(bodySize.multiply(BigDecimal.valueOf(2))) > 0
      && closePrice.compareTo(openPrice) > 0;

    boolean bearishRejection = upperShadow.compareTo(bodySize.multiply(BigDecimal.valueOf(2))) > 0
      && closePrice.compareTo(openPrice) < 0;

    return bullishRejection || bearishRejection;
  }

  private BigDecimal calculateScalpingSignalStrength(MarketConditions conditions) {
    int positiveSignals = 0;
    int totalSignals = 7; // Mais sinais para análise detalhada

    // Avalia RSI - mais sensível para scalping
    if (conditions.rsi().compareTo(BigDecimal.valueOf(40)) <= 0) positiveSignals++;
    if (conditions.rsi().compareTo(BigDecimal.valueOf(30)) <= 0) positiveSignals++;
    if (conditions.rsi().compareTo(BigDecimal.valueOf(20)) <= 0) positiveSignals++;

    // Avalia proximidade ao suporte - mais sensível
    BigDecimal priceToSupport = conditions.currentPrice().divide(conditions.support(), 8, RoundingMode.HALF_UP);
    if (priceToSupport.compareTo(BigDecimal.valueOf(1.005)) <= 0) positiveSignals++; // Extremamente próximo

    // Avalia momentum positivo com maior sensibilidade
    if (conditions.momentum().compareTo(BigDecimal.valueOf(0)) > 0) positiveSignals++;
    if (conditions.momentum().compareTo(BigDecimal.valueOf(0.1)) > 0) positiveSignals++;

    // Avalia posição nas bandas de Bollinger
    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    BigDecimal positionInBand = conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP);
    if (positionInBand.compareTo(BigDecimal.valueOf(0.2)) <= 0) positiveSignals++;

    // Calcula força do sinal entre 0.3 e 1.0 (mais amplo para scalping)
    BigDecimal signalRatio = BigDecimal.valueOf(positiveSignals)
      .divide(BigDecimal.valueOf(totalSignals), 8, RoundingMode.HALF_UP);

    return BigDecimal.valueOf(0.3)
      .add(signalRatio.multiply(BigDecimal.valueOf(0.7)));
  }

  private BigDecimal estimatePotentialProfit(MarketConditions conditions) {
    BigDecimal volatilityWeight =
      conditions.rsi().compareTo(BigDecimal.valueOf(35)) < 0
        ? BigDecimal.valueOf(0.6)
        : (conditions.rsi().compareTo(BigDecimal.valueOf(45)) < 0
        ? BigDecimal.valueOf(0.5)
        : BigDecimal.valueOf(0.4));

    BigDecimal volatilityFactor = conditions.volatility().multiply(volatilityWeight);

    BigDecimal volumeMultiplier =
      conditions.currentVolume().divide(conditions.averageVolume(), 8, RoundingMode.HALF_UP)
        .min(BigDecimal.valueOf(2.5))
        .max(BigDecimal.valueOf(0.6));

    BigDecimal distanceToResistance =
      conditions.resistance()
        .subtract(conditions.currentPrice())
        .divide(conditions.currentPrice(), 8, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100))
        .multiply(volumeMultiplier);

    BigDecimal trendComponent =
      conditions.ema8().compareTo(conditions.ema21()) > 0
        ? BigDecimal.valueOf(0.5)
        : (conditions.priceSlope().compareTo(BigDecimal.ZERO) > 0
        ? BigDecimal.valueOf(0.2)
        : BigDecimal.valueOf(0.05));

    BigDecimal riskToSupportLevel =
      conditions.currentPrice()
        .subtract(conditions.support())
        .divide(conditions.currentPrice(), 8, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100))
        .max(BigDecimal.valueOf(0.05));

    BigDecimal riskRewardRatio = BigDecimal.ONE;

    if (riskToSupportLevel.compareTo(BigDecimal.valueOf(0.05)) > 0) {
      riskRewardRatio = distanceToResistance.divide(
        riskToSupportLevel, 8, RoundingMode.HALF_UP).min(BigDecimal.valueOf(4.0));
    }

    return volatilityFactor
      .add(distanceToResistance.multiply(BigDecimal.valueOf(0.3)))
      .add(trendComponent)
      .add(BigDecimal.valueOf(0.1))
      .multiply(riskRewardRatio.multiply(BigDecimal.valueOf(0.15))
        .add(BigDecimal.valueOf(0.85)));
  }

  private boolean hasRecentReversalPattern(List<KlineDto> klines, MarketConditions conditions) {
    if (klines == null || klines.size() < 3) return false;

    boolean patternDetected = checkPatternFormations(klines);
    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    BigDecimal bandPosition = conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));

    if (patternDetected) {
      if (conditions.currentVolume().compareTo(conditions.averageVolume().multiply(BigDecimal.valueOf(0.9))) < 0) {
        return false;
      }

      if (conditions.momentum().compareTo(BigDecimal.valueOf(-0.05)) < 0) {
        return false;
      }

      if (bandPosition.compareTo(BigDecimal.valueOf(0.3)) < 0) {
        return false;
      }
    }

    return patternDetected;
  }

  private boolean checkPatternFormations(List<KlineDto> klines) {
    if (klines.size() < 4) return false;

    KlineDto current = klines.get(klines.size() - 1);
    KlineDto previous = klines.get(klines.size() - 2);
    KlineDto prePrevious = klines.get(klines.size() - 3);
    KlineDto prePre = klines.get(klines.size() - 4);

    BigDecimal currentOpen = new BigDecimal(current.getOpenPrice());
    BigDecimal currentClose = new BigDecimal(current.getClosePrice());
    BigDecimal currentHigh = new BigDecimal(current.getHighPrice());
    BigDecimal currentLow = new BigDecimal(current.getLowPrice());

    BigDecimal prevOpen = new BigDecimal(previous.getOpenPrice());
    BigDecimal prevClose = new BigDecimal(previous.getClosePrice());
    BigDecimal prevHigh = new BigDecimal(previous.getHighPrice());
    BigDecimal prevLow = new BigDecimal(previous.getLowPrice());

    BigDecimal ppOpen = new BigDecimal(prePrevious.getOpenPrice());
    BigDecimal ppClose = new BigDecimal(prePrevious.getClosePrice());
    BigDecimal ppHigh = new BigDecimal(prePrevious.getHighPrice());
    BigDecimal ppLow = new BigDecimal(prePrevious.getLowPrice());

    BigDecimal pppOpen = new BigDecimal(prePre.getOpenPrice());
    BigDecimal pppClose = new BigDecimal(prePre.getClosePrice());

    BigDecimal currentBody = currentClose.subtract(currentOpen).abs();
    BigDecimal prevBody = prevClose.subtract(prevOpen).abs();
    BigDecimal ppBody = ppClose.subtract(ppOpen).abs();

    BigDecimal currentUpperShadow = currentHigh.subtract(currentClose.max(currentOpen));
    BigDecimal currentLowerShadow = currentOpen.min(currentClose).subtract(currentLow);
    BigDecimal prevUpperShadow = prevHigh.subtract(prevClose.max(prevOpen));
    BigDecimal prevLowerShadow = prevOpen.min(prevClose).subtract(prevLow);

    boolean isBullishCurrent = currentClose.compareTo(currentOpen) > 0;
    boolean isBullishPrev = prevClose.compareTo(prevOpen) > 0;
    boolean isBullishPP = ppClose.compareTo(ppOpen) > 0;
    boolean isBullishPPP = pppClose.compareTo(pppOpen) > 0;

    boolean isHammer = !isBullishPrev && isBullishCurrent &&
      currentLowerShadow.compareTo(currentBody.multiply(BigDecimal.valueOf(2.5))) > 0 &&
      currentUpperShadow.compareTo(currentBody.multiply(BigDecimal.valueOf(0.3))) < 0 &&
      currentBody.compareTo(prevBody.multiply(BigDecimal.valueOf(0.8))) > 0;

    boolean isMorningStar = !isBullishPP && ppBody.compareTo(BigDecimal.valueOf(0)) > 0 &&
      prevBody.compareTo(ppBody.multiply(BigDecimal.valueOf(0.5))) < 0 &&
      isBullishCurrent &&
      currentClose.compareTo(ppOpen.add(ppBody.multiply(BigDecimal.valueOf(0.5)))) > 0;

    boolean isBullishEngulfing = !isBullishPrev && isBullishCurrent &&
      currentBody.compareTo(prevBody.multiply(BigDecimal.valueOf(1.8))) > 0 &&
      currentOpen.compareTo(prevClose) < 0 &&
      currentClose.compareTo(prevOpen) > 0;

    boolean isThreeBarReversal = !isBullishPPP && !isBullishPP && !isBullishPrev &&
      isBullishCurrent &&
      currentBody.compareTo(prevBody.add(ppBody).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP)) > 0;

    boolean isDragonFlyDoji = currentBody.compareTo(currentHigh.subtract(currentLow).multiply(BigDecimal.valueOf(0.05))) < 0 &&
      currentLowerShadow.compareTo(currentHigh.subtract(currentLow).multiply(BigDecimal.valueOf(0.7))) > 0;

    boolean isPriceRejection = currentLowerShadow.compareTo(currentBody.multiply(BigDecimal.valueOf(3.0))) > 0 &&
      prevLowerShadow.compareTo(prevBody) > 0 &&
      currentLow.compareTo(prevLow.multiply(BigDecimal.valueOf(0.999))) < 0 &&
      isBullishCurrent;

    return isHammer || isMorningStar || isBullishEngulfing || isThreeBarReversal || isDragonFlyDoji || isPriceRejection;
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

    return downtrendCount >= periods * 0.7;
  }

  private boolean isAtSupportLevel(MarketConditions conditions) {
    BigDecimal priceToSupport = conditions.currentPrice().divide(conditions.support(), 8, RoundingMode.HALF_UP);
    return priceToSupport.compareTo(BigDecimal.valueOf(1.01)) <= 0;
  }

  private void logMarketConditions(SimpleTradeBot bot, MarketConditions conditions, boolean isDownTrend, Queue<String> operationLogs, boolean operationSignals) {
    BotParameters parameters = bot.getParameters();
    Status status = bot.getStatus();
    String botTypeName = "[" + parameters.getBotType() + "] - ";
    String trend = isDownTrend ? "Downtrend" : "Normal trend";
    Queue<String> logList = new LinkedList<>();

    logList.add(buildLog(botTypeName + String.format("📊 %s | RSI: %.2f | SMA9: %.2f | SMA21: %.2f",
      trend, conditions.rsi(), conditions.sma9(), conditions.sma21()), true));

    logList.add(buildLog(botTypeName + String.format("💰 Price: %.2f | Support: %.2f | Resistance: %.2f",
      conditions.currentPrice(), conditions.support(), conditions.resistance()), true));

    logList.add(buildLog(botTypeName + String.format("📈 EMA8: %.2f | EMA21: %.2f | EMA50: %.2f | EMA100: %.2f",
      conditions.ema8(), conditions.ema21(), conditions.ema50(), conditions.ema100()), true));

    logList.add(buildLog(botTypeName + String.format("📉 Bollinger Bands: Lower=%.2f | Middle=%.2f | Upper=%.2f",
      conditions.bollingerLower(), conditions.bollingerMiddle(), conditions.bollingerUpper()), true));

    logList.add(buildLog(botTypeName + String.format("📊 Volume: Current=%.5f | Average=%.5f | Ratio=%.5f",
      conditions.currentVolume(), conditions.averageVolume(),
      conditions.currentVolume().divide(conditions.averageVolume(), 4, RoundingMode.HALF_UP)), true));

    logList.add(buildLog(botTypeName + String.format("📊 Momentum: %.2f | Volatility: %.2f | Slope: %.6f",
      conditions.momentum(), conditions.volatility(), conditions.priceSlope()), true));

    logList.add(buildLog(botTypeName + String.format("📊 MACD: %.4f | Stochastic K/D: %.2f/%.2f | ATR: %.4f",
      conditions.macd(), conditions.stochasticK(), conditions.stochasticD(), conditions.atr()), true));

    logList.add(buildLog(botTypeName + String.format("📊 OBV: %.2f | Band Position: %.2f%%",
      conditions.obv(), calculateBandPosition(conditions)), true));

    if (status.isLong()) {
      BigDecimal priceChangePercent = calculatePriceChange(status, conditions.currentPrice());
      LocalDateTime purchaseTime = status.getLastPurchaseTime();
      long minutesHeld = purchaseTime != null ?
        Duration.between(purchaseTime, LocalDateTime.now()).toMinutes() : 0;

      logList.add(buildLog(botTypeName + String.format("🔄 Position: LONG | Time: %d min | Change: %.2f%% | TP: %.2f%% | SL: %.2f%%",
        minutesHeld, priceChangePercent, parameters.getTakeProfitPercent(), parameters.getStopLossPercent()), true));

      if (status.getTrailingStopLevel() != null) {
        logList.add(buildLog(botTypeName + String.format("🔄 Trailing Stop active: %.2f%%", status.getTrailingStopLevel()), true));
      }
    } else {
      logList.add(buildLog(botTypeName + "🔄 Position: WAITING", true));
    }

    logList.addAll(operationLogs);

    if (operationSignals) {
      logCripto(logList, bot.getParameters().getBotType().name());
    } else {
      log(logList);
    }
  }

  private BigDecimal calculatePriceChange(Status status, BigDecimal currentPrice) {
    if (status.getAveragePrice() == null || status.getAveragePrice().compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }

    return currentPrice.subtract(status.getAveragePrice())
      .divide(status.getAveragePrice(), 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));
  }

  private BigDecimal calculateBandPosition(MarketConditions conditions) {
    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    return conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));
  }

}