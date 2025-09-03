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

import static dev.prjbtrad001.app.core.TradingConstants.MIN_PROFIT_THRESHOLD;
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
      evaluateBuySignal(bot, conditions, klines);
    } else {
      evaluateSellSignal(bot, conditions, klines);
    }
  }

  private void evaluateBuySignal(SimpleTradeBot bot, MarketConditions conditions, List<KlineDto> klines) {
    BotParameters parameters = bot.getParameters();
    String botTypeName = "[" + parameters.getBotType() + "] - ";

    BigDecimal totalFees = BigDecimal.valueOf(0.2);
    BigDecimal potentialProfit = estimatePotentialProfit(conditions);

    if (potentialProfit.compareTo(totalFees) <= 0) {
      log(botTypeName + "⚠️ Potential profit too low compared to fees. Skipping trade.");
      return;
    }

    // 🔹 Rsi condition
    boolean rsiOversold = conditions.rsi().compareTo(parameters.getRsiPurchase()) <= 0;

    // 🔹 Trend condition
    boolean ema8AboveEma21 = conditions.ema8().compareTo(conditions.ema21()) > 0;
    boolean sma9AboveSma21 = conditions.sma9().compareTo(conditions.sma21()) > 0;
    boolean bullishTrend = sma9AboveSma21 && ema8AboveEma21;

    // 🔹 Volume condition
    boolean strongVolume = conditions.currentVolume().compareTo(conditions.averageVolume().multiply(parameters.getVolumeMultiplier())) >= 0;

    // 🔹 Price condition
    boolean touchedBollingerLower = conditions.currentPrice().compareTo(conditions.bollingerLower().multiply(BigDecimal.valueOf(1.01))) <= 0;
    boolean touchedSupport = conditions.currentPrice().compareTo(conditions.support().multiply(BigDecimal.valueOf(1.01))) <= 0;

    // 🔹 MacD condition
    boolean macdPositive = conditions.macd() != null && conditions.macd().compareTo(BigDecimal.ZERO) > 0;

    // 🔹 Stochastic condition
    boolean stochasticBull = conditions.stochasticK() != null && conditions.stochasticD() != null &&
      conditions.stochasticK().compareTo(BigDecimal.valueOf(25)) < 0 &&
      conditions.stochasticK().compareTo(conditions.stochasticD()) > 0;

    // 🔹 Momentum condition
    boolean positiveMomentum = conditions.momentum().compareTo(BigDecimal.ZERO) > 0;

    // 🔹 Volatility condition
    boolean lowVolatility = conditions.volatility().compareTo(BigDecimal.valueOf(3)) < 0;

    boolean isPatternsAlignedForBuy = isPatternsAligned(klines, conditions, true);
    boolean bullishRejection = isBullishPriceRejection(klines.getLast(), conditions.averageVolume(), conditions.atr());

    log(botTypeName + "RSI Oversold: " + (rsiOversold ? "🟢" : "🔴") +
      " (RSI=" + conditions.rsi().setScale(3, RoundingMode.HALF_UP) + ", Threshold=" + parameters.getRsiPurchase().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "Bullish Trend: " + (bullishTrend ? "🟢" : "🔴") +
      " (EMA8=" + conditions.ema8().setScale(3, RoundingMode.HALF_UP) + ", EMA21=" + conditions.ema21().setScale(3, RoundingMode.HALF_UP) +
      " | SMA9=" + conditions.sma9().setScale(3, RoundingMode.HALF_UP) + ", SMA21=" + conditions.sma21().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "Strong Volume: " + (strongVolume ? "🟢" : "🔴") +
      " (Current=" + conditions.currentVolume().setScale(3, RoundingMode.HALF_UP) + ", Avg*Mult=" +
      conditions.averageVolume().multiply(parameters.getVolumeMultiplier()) + ")", true);
    log(botTypeName + "Touched Support: " + (touchedSupport ? "🟢" : "🔴") +
      " (Price=" + conditions.currentPrice().setScale(3, RoundingMode.HALF_UP) + ", Support=" + conditions.support().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "MACD Positive: " + (macdPositive ? "🟢" : "🔴") +
      " (MACD=" + conditions.macd().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "Stochastic Bull: " + (stochasticBull ? "🟢" : "🔴") +
      " (K=" + conditions.stochasticK().setScale(3, RoundingMode.HALF_UP) + ", D=" + conditions.stochasticD().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "Low Volatility: " + (lowVolatility ? "🟢" : "🔴") +
      " (Volatility=" + conditions.volatility().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "Patterns Aligned: " + (isPatternsAlignedForBuy ? "🟢" : "🔴") +
      " (Aligned with bullish setup = true)", true);
    log(botTypeName + "Positive Momentum: " + (positiveMomentum ? "🟢" : "🔴") +
      " (Momentum=" + conditions.momentum().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "Touched BollingerLower: " + (touchedBollingerLower ? "🟢" : "🔴") +
      " (Price=" + conditions.currentPrice().setScale(3, RoundingMode.HALF_UP) + ", Lower=" + conditions.bollingerLower().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "Bullish Price Rejection:" + (bullishRejection ? "🟢" : "🔴"), true);

    MarketType marketType = MarketType.classifyMarket(conditions);

    // Log do tipo de mercado
    log(botTypeName + "📊 Current market type: " + marketType, true);

    // Construir o objeto Bullish incluindo o tipo de mercado
    TradingSignals.Bullish tradingSignals =
      TradingSignals.Bullish.builder()
        .rsiCondition(rsiOversold)
        .trendCondition(bullishTrend)
        .volumeCondition(strongVolume)
        .priceCondition(touchedSupport || touchedBollingerLower)
        .macdCondition(macdPositive)
        .stochCondition(stochasticBull)
        .momentumCondition(positiveMomentum)
        .volatilityCondition(lowVolatility)
        .patternsCondition(isPatternsAlignedForBuy)
        .bullishRejection(bullishRejection)
        .marketType(marketType)
        .build();

    // Incluir razão da compra
    String buyReason = "";
    if (tradingSignals.shouldBuy()) {
      if (bullishRejection) buyReason = "Bullish price rejection";
      else if (touchedSupport || touchedBollingerLower) buyReason = "Price touched support";
      else if (rsiOversold) buyReason = "RSI oversold";
      else if (bullishTrend) buyReason = "Bullish trend confirmed";
      else if (positiveMomentum) buyReason = "Positive momentum";
      else buyReason = "Combination of buy signals";

      log(botTypeName + "🔵 BUY signal detected! Reason: " + buyReason);
      executeBuyOrder(bot, calculateOptimalBuyAmount(bot, conditions));
    } else {
      log(botTypeName + "⚪ Insufficient conditions for BUY.");
    }
  }

  private void evaluateSellSignal(SimpleTradeBot bot, MarketConditions conditions, List<KlineDto> klines) {
    BotParameters parameters = bot.getParameters();
    Status status = bot.getStatus();
    String botTypeName = "[" + parameters.getBotType() + "] - ";

    BigDecimal priceChangePercent = calculatePriceChangePercent(status, conditions.currentPrice());
    log(botTypeName + String.format("📉 Current variation: %.2f%% (least for profit: %.2f%%)", priceChangePercent, MIN_PROFIT_THRESHOLD));

    if (applyTrailingStop(bot, conditions)) {
      return;
    }

    // 🔹 Rsi condition
    boolean rsiOverbought = conditions.rsi().compareTo(parameters.getRsiSale()) >= 0;

    // 🔹 Trend condition
    boolean sma9BelowSma21 = conditions.sma9().compareTo(conditions.sma21()) < 0;
    boolean ema8BelowEma21 = conditions.ema8().compareTo(conditions.ema21()) < 0;
    boolean bearishTrend = sma9BelowSma21 && ema8BelowEma21;

    // 🔹 Price condition
    boolean touchedResistance = conditions.currentPrice().compareTo(conditions.resistance().multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.005)))) >= 0;
    boolean touchedBollingerUpper = conditions.currentPrice().compareTo(conditions.bollingerUpper().multiply(BigDecimal.valueOf(0.99))) >= 0;

    // 🔹 MacD condition
    boolean macdNegative = conditions.macd() != null && conditions.macd().compareTo(BigDecimal.ZERO) < 0;

    // 🔹 Stochastic condition
    boolean stochasticOverbought =
      conditions.stochasticK() != null
        && conditions.stochasticD() != null
        && conditions.stochasticK().compareTo(BigDecimal.valueOf(75)) > 0
        && conditions.stochasticK().compareTo(conditions.stochasticD()) < 0;

    // 🔹 Momentum condition
    boolean negativeMomentum = conditions.momentum().compareTo(BigDecimal.ZERO) < 0;

    // 🔹 Volatility condition
    boolean highVolatility =
      conditions.atr().compareTo(BigDecimal.valueOf(1.0)) >= 0 &&
        conditions.atr().compareTo(BigDecimal.valueOf(3.0)) <= 0 &&
        conditions.volatility().compareTo(BigDecimal.valueOf(20)) > 0;

    // 🔹 Take Profit / Stop Loss conditions
    boolean reachedTakeProfit = priceChangePercent.compareTo(parameters.getTakeProfitPercent()) >= 0;
    boolean reachedStopLoss = priceChangePercent.compareTo(parameters.getStopLossPercent().negate()) <= 0;
    boolean isPatternsAlignedForSell = isPatternsAligned(klines, conditions, false);
    boolean bearishRejection = isBearishPriceRejection(klines.getLast(), conditions.averageVolume(), conditions.atr());

    log(botTypeName + "RSI Overbought: " + (rsiOverbought ? "🟢" : "🔴") +
      " (RSI=" + conditions.rsi().setScale(3, RoundingMode.HALF_UP) + ", Threshold=" + parameters.getRsiSale().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "Bearish Trend: " + (bearishTrend ? "🟢" : "🔴") +
      " (EMA8=" + conditions.ema8().setScale(3, RoundingMode.HALF_UP) + ", EMA21=" + conditions.ema21().setScale(3, RoundingMode.HALF_UP) +
      " | SMA9=" + conditions.sma9().setScale(3, RoundingMode.HALF_UP) + ", SMA21=" + conditions.sma21().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "Touched Resistance: " + (touchedResistance ? "🟢" : "🔴") +
      " (Price=" + conditions.currentPrice().setScale(3, RoundingMode.HALF_UP) + ", Resistance=" + conditions.resistance().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "MACD Negative: " + (macdNegative ? "🟢" : "🔴") +
      " (MACD=" + conditions.macd().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "Negative Momentum: " + (negativeMomentum ? "🟢" : "🔴") +
      " (Momentum=" + conditions.momentum().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "High Volatility: " + (highVolatility ? "🟢" : "🔴") +
      " (ATR=" + conditions.atr().setScale(3, RoundingMode.HALF_UP) + ", Volatility=" + conditions.volatility().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "Reached Take Profit: " + (reachedTakeProfit ? "🟢" : "🔴") +
      " (PriceChange%=" + priceChangePercent + ", TP=" + parameters.getTakeProfitPercent().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "Reached Stop Loss : " + (reachedStopLoss ? "🟢" : "🔴") +
      " (PriceChange%=" + priceChangePercent + ", SL=" + parameters.getStopLossPercent().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "Patterns Aligned: " + (isPatternsAlignedForSell ? "🟢" : "🔴") +
      " (Aligned with bearish setup = false)", true);
    log(botTypeName + "Stochastic Overbought: " + (stochasticOverbought ? "🟢" : "🔴") +
      " (K=" + conditions.stochasticK().setScale(3, RoundingMode.HALF_UP) + ", D=" + conditions.stochasticD().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "Touched BollingerUpper: " + (touchedBollingerUpper ? "🟢" : "🔴") +
      " (Price=" + conditions.currentPrice().setScale(3, RoundingMode.HALF_UP) + ", Upper=" + conditions.bollingerUpper().setScale(3, RoundingMode.HALF_UP) + ")", true);
    log(botTypeName + "Bearish Price Rejection: " + (bearishRejection ? "🟢" : "🔴"), true);

    MarketType marketType = MarketType.classifyMarket(conditions);

    // Log do tipo de mercado
    log(botTypeName + "📊 Current market type: " + marketType, true);

    TradingSignals.Bearish tradingSignals =
      TradingSignals.Bearish.builder()
        .rsiCondition(rsiOverbought)
        .trendCondition(bearishTrend)
        .volumeCondition(highVolatility)
        .priceCondition(touchedResistance || touchedBollingerUpper)
        .macdCondition(macdNegative)
        .stochCondition(stochasticOverbought)
        .momentumCondition(negativeMomentum)
        .volatilityCondition(highVolatility)
        .stopLoss(reachedStopLoss)
        .takeProfit(reachedTakeProfit)
        .patternsCondition(isPatternsAlignedForSell)
        .bearishRejection(bearishRejection)
        .marketType(marketType)
        .build();


    String sellReason = "";
    if (tradingSignals.shouldSell()) {
      if (reachedTakeProfit) {
        sellReason = "Take Profit reached";
      } else if (reachedStopLoss) {
        sellReason = "Stop Loss reached";
      } else {
        // Determine the main reason for selling
        if (bearishRejection) sellReason = "Bearish price rejection";
        else if (touchedResistance || touchedBollingerUpper) sellReason = "Price touched resistance";
        else if (rsiOverbought) sellReason = "RSI overbought";
        else if (bearishTrend) sellReason = "Downtrend confirmed";
        else if (negativeMomentum) sellReason = "Negative momentum";
        else sellReason = "Combination of sell signals";
      }

      log(botTypeName + "🔴 SELL signal detected! Reason: " + sellReason);
      executeSellOrder(bot);
    } else {
      log(botTypeName + "⚪ No SELL signal, maintaining current position.", true);
    }
  }

  private BigDecimal calculateOptimalBuyAmount(SimpleTradeBot bot, MarketConditions conditions) {
    BotParameters parameters = bot.getParameters();
    BigDecimal baseAmount = parameters.getPurchaseAmount();
    BigDecimal adjustmentFactor = BigDecimal.ONE;

    if (conditions.volatility().compareTo(BigDecimal.valueOf(1.5)) >= 0) {
      BigDecimal volatilityFactor =
        BigDecimal.ONE
          .subtract(conditions.volatility().multiply(BigDecimal.valueOf(0.06)));
      volatilityFactor = volatilityFactor.max(BigDecimal.valueOf(0.5));
      adjustmentFactor = adjustmentFactor.multiply(volatilityFactor);
    }

    if (conditions.rsi().compareTo(BigDecimal.valueOf(35)) <= 0) {
      BigDecimal rsiBoost =
        BigDecimal.valueOf(1.4)
          .subtract(conditions.rsi().divide(BigDecimal.valueOf(35), 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(0.4)));
      adjustmentFactor = adjustmentFactor.multiply(rsiBoost);
    }

    BigDecimal priceToSupport = conditions.currentPrice().divide(conditions.support(), 8, RoundingMode.HALF_UP);
    if (priceToSupport.compareTo(BigDecimal.valueOf(1.01)) <= 0) {
      adjustmentFactor = adjustmentFactor.multiply(BigDecimal.valueOf(1.2));
    }

    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    BigDecimal pricePosition = conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP);

    if (pricePosition.compareTo(BigDecimal.valueOf(0.15)) <= 0) {
      adjustmentFactor = adjustmentFactor.multiply(BigDecimal.valueOf(1.15));
    }

    if (conditions.ema50().compareTo(conditions.ema100()) < 0 &&
      conditions.priceSlope().compareTo(BigDecimal.ZERO) < 0) {
      adjustmentFactor = adjustmentFactor.multiply(BigDecimal.valueOf(0.5));
    }

    BigDecimal marketBasedAmount = baseAmount.multiply(
      adjustmentFactor.max(BigDecimal.valueOf(0.2)).min(BigDecimal.valueOf(1.3))
    );

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

    BigDecimal totalReceived = order.trades().stream()
      .map(t -> t.price().multiply(t.quantity()))
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal investedAmount = status.getTotalPurchased();
    BigDecimal profit = totalReceived.subtract(investedAmount);
    BigDecimal totalProfit = status.getProfit() != null ?
      status.getProfit().add(profit) : profit;

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

    BigDecimal slopeIntensity = conditions.priceSlope().abs();
    boolean steepDecline = conditions.priceSlope().compareTo(
      TradingConstants.DOWNTREND_THRESHOLD.negate()) < 0 &&
      slopeIntensity.compareTo(TradingConstants.DOWNTREND_THRESHOLD.multiply(BigDecimal.valueOf(2))) > 0;

    boolean volumeDecline = conditions.currentVolume().compareTo(
      conditions.averageVolume().multiply(BigDecimal.valueOf(0.7))) < 0;
    boolean volumeSpike = conditions.currentVolume().compareTo(
      conditions.averageVolume().multiply(BigDecimal.valueOf(1.8))) > 0;

    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    BigDecimal positionInBand = conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP);
    boolean movingDownInBand = positionInBand.compareTo(BigDecimal.valueOf(0.4)) < 0 &&
      conditions.currentPrice().compareTo(conditions.bollingerMiddle()) < 0;

    boolean negativeMomentum = conditions.momentum().compareTo(BigDecimal.valueOf(-0.08)) < 0;
    boolean strongNegativeMomentum = conditions.momentum().compareTo(BigDecimal.valueOf(-0.18)) < 0;
    boolean macdNegative = conditions.macd() != null && conditions.macd().compareTo(BigDecimal.ZERO) < 0;

    boolean stochasticOversold = conditions.stochasticK() != null &&
      conditions.stochasticK().compareTo(BigDecimal.valueOf(20)) < 0;

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

    if (conditions.ema8().compareTo(conditions.ema21().multiply(new BigDecimal("0.998"))) < 0) {
      score += 3;
    }

    int threshold = TradingConstants.DOWNTREND_SCORE_THRESHOLD;
    if (conditions.volatility().compareTo(BigDecimal.valueOf(2.5)) > 0) {
      threshold += 2;
    } else if (conditions.volatility().compareTo(BigDecimal.valueOf(0.5)) < 0) {
      threshold -= 1;
    }

    log(botTypeName + "Downtrend score: " + score + " (threshold: " + threshold + ")", true);
    return score >= threshold;
  }

  private boolean applyTrailingStop(SimpleTradeBot bot, MarketConditions conditions) {
    Status status = bot.getStatus();
    BigDecimal currentProfit = calculatePriceChangePercent(status, conditions.currentPrice());
    BigDecimal taxCost = BigDecimal.valueOf(0.25);
    String botTypeName = "[" + bot.getParameters().getBotType() + "] - ";

    BigDecimal activationThreshold = taxCost.add(BigDecimal.valueOf(0.15));

    MarketType marketType = MarketType.classifyMarket(conditions);

    BigDecimal volatilityFactor = switch (marketType) {
      case HIGH_VOLATILITY -> BigDecimal.valueOf(0.55);
      case STRONG_UPTREND -> BigDecimal.valueOf(0.85);
      case WEAK_UPTREND -> BigDecimal.valueOf(0.80);
      case RANGE_BOUND -> BigDecimal.valueOf(0.70);
      case WEAK_DOWNTREND -> BigDecimal.valueOf(0.65);
      case STRONG_DOWNTREND -> BigDecimal.valueOf(0.60);
      case TREND_REVERSAL -> BigDecimal.valueOf(0.75);
    };

    LocalDateTime purchaseTime = status.getLastPurchaseTime();
    long minutesHeld = purchaseTime != null
      ? java.time.Duration.between(purchaseTime, LocalDateTime.now()).toMinutes()
      : 0;

    BigDecimal timeAdjustment = getTimeAdjustment(minutesHeld);
    volatilityFactor = volatilityFactor.multiply(timeAdjustment);

    boolean positiveTrend = conditions.ema8().compareTo(conditions.ema21()) > 0;
    if (positiveTrend) {
      volatilityFactor = volatilityFactor.multiply(BigDecimal.valueOf(0.95));
    }

    if (currentProfit.compareTo(activationThreshold) > 0) {
      BigDecimal trailingLevel = currentProfit.multiply(volatilityFactor);
      trailingLevel = trailingLevel.max(taxCost.add(BigDecimal.valueOf(0.1)));

      if (status.getTrailingStopLevel() == null ||
        trailingLevel.compareTo(status.getTrailingStopLevel()) > 0) {
        status.setTrailingStopLevel(trailingLevel);
        log(botTypeName + "🔄 Trailing stop: " + trailingLevel.setScale(2, RoundingMode.HALF_UP) + "%");
      }
    }

    boolean strongNegativeMomentum = conditions.momentum().compareTo(BigDecimal.valueOf(-0.12)) < 0 &&
      currentProfit.compareTo(taxCost.add(BigDecimal.valueOf(0.15))) > 0;

    boolean minimumProfitGreaterThanTax = currentProfit.compareTo(taxCost.add(BigDecimal.valueOf(0.2))) > 0;
    boolean highRsi = conditions.rsi().compareTo(BigDecimal.valueOf(70)) > 0 && minimumProfitGreaterThanTax;

    boolean volumeDropOff = conditions.currentVolume().compareTo(
      conditions.averageVolume().multiply(BigDecimal.valueOf(0.5))) < 0 &&
      minimumProfitGreaterThanTax;

    if ((status.getTrailingStopLevel() != null &&
      currentProfit.compareTo(status.getTrailingStopLevel()) < 0 &&
      currentProfit.compareTo(taxCost) > 0) || strongNegativeMomentum || highRsi || volumeDropOff) {

      String reason = strongNegativeMomentum
        ? "negative momentum"
        : highRsi
        ? "High RSI"
        : volumeDropOff
        ? "Volume drop-off" : "Stop level";

      log(botTypeName + "🔴 Trailing Stop executed by" +
        " " + reason + ": " + currentProfit + "%");
      executeSellOrder(bot);
      return true;
    }

    return false;
  }

  private BigDecimal getTimeAdjustment(long minutesHeld) {
    if (minutesHeld > 240) return BigDecimal.valueOf(0.85);
    if (minutesHeld > 120) return BigDecimal.valueOf(0.88);
    if (minutesHeld > 60) return BigDecimal.valueOf(0.92);
    if (minutesHeld > 30) return BigDecimal.valueOf(0.95);
    if (minutesHeld > 15) return BigDecimal.valueOf(0.97);
    return BigDecimal.valueOf(0.99);
  }

  private boolean isBullishPriceRejection(KlineDto lastKline, BigDecimal averageVolume, BigDecimal atr) {
    BigDecimal open = new BigDecimal(lastKline.getOpenPrice());
    BigDecimal close = new BigDecimal(lastKline.getClosePrice());
    BigDecimal high = new BigDecimal(lastKline.getHighPrice());
    BigDecimal low = new BigDecimal(lastKline.getLowPrice());

    BigDecimal body = close.subtract(open).abs();
    BigDecimal lowerShadow = open.min(close).subtract(low);
    BigDecimal range = high.subtract(low);

    // Verificações básicas
    boolean isBullish = close.compareTo(open) > 0;
    boolean hasSignificantVolume = new BigDecimal(lastKline.getVolume())
      .compareTo(averageVolume.multiply(BigDecimal.valueOf(1.2))) >= 0;
    boolean hasMinimumBodySize = body.compareTo(atr.multiply(BigDecimal.valueOf(0.2))) >= 0;
    boolean hasLongLowerShadow = lowerShadow.compareTo(body.multiply(BigDecimal.valueOf(2))) > 0;


    boolean bodyToRangeRatio;
    if (range.compareTo(BigDecimal.ZERO) == 0 || range.compareTo(new BigDecimal("0.000000001")) < 0) {
      bodyToRangeRatio = false;
    } else {
      bodyToRangeRatio = body.divide(range, 8, RoundingMode.HALF_UP)
        .compareTo(BigDecimal.valueOf(0.3)) <= 0;
    }

    // Critério principal: vela bullish com sombra inferior longa
    boolean basicCriteria = isBullish && hasLongLowerShadow && hasSignificantVolume;

    // Para swing trading, queremos rejeições fortes
    return basicCriteria &&
      (hasMinimumBodySize || bodyToRangeRatio) &&
      lowerShadow.compareTo(range.multiply(BigDecimal.valueOf(0.5))) >= 0;
  }

  private boolean isBearishPriceRejection(KlineDto lastKline, BigDecimal averageVolume, BigDecimal atr) {
    BigDecimal open = new BigDecimal(lastKline.getOpenPrice());
    BigDecimal close = new BigDecimal(lastKline.getClosePrice());
    BigDecimal high = new BigDecimal(lastKline.getHighPrice());
    BigDecimal low = new BigDecimal(lastKline.getLowPrice());

    BigDecimal body = close.subtract(open).abs();
    BigDecimal upperShadow = high.subtract(open.max(close));
    BigDecimal range = high.subtract(low);

    // Verificações básicas
    boolean isBearish = close.compareTo(open) < 0;
    boolean hasSignificantVolume = new BigDecimal(lastKline.getVolume())
      .compareTo(averageVolume.multiply(BigDecimal.valueOf(1.2))) >= 0;
    boolean hasMinimumBodySize = body.compareTo(atr.multiply(BigDecimal.valueOf(0.2))) >= 0;
    boolean hasLongUpperShadow = upperShadow.compareTo(body.multiply(BigDecimal.valueOf(2))) > 0;

    boolean bodyToRangeRatio;
    if (range.compareTo(BigDecimal.ZERO) == 0 || range.compareTo(new BigDecimal("0.000000001")) < 0) {
      bodyToRangeRatio = false;
    } else {
      bodyToRangeRatio = body.divide(range, 8, RoundingMode.HALF_UP)
        .compareTo(BigDecimal.valueOf(0.3)) <= 0;
    }

    // Critério principal: vela bearish com sombra superior longa
    boolean basicCriteria = isBearish && hasLongUpperShadow && hasSignificantVolume;

    // Para swing trading, queremos rejeições fortes
    return basicCriteria &&
      (hasMinimumBodySize || bodyToRangeRatio) &&
      upperShadow.compareTo(range.multiply(BigDecimal.valueOf(0.5))) >= 0;
  }

  private BigDecimal estimatePotentialProfit(MarketConditions c) {
    BigDecimal rsiWeight =
      c.rsi().compareTo(BigDecimal.valueOf(35)) < 0
        ? BigDecimal.valueOf(0.6)
        : c.rsi().compareTo(BigDecimal.valueOf(45)) < 0
        ? BigDecimal.valueOf(0.5)
        : BigDecimal.valueOf(0.4);

    BigDecimal volatilityFactor = c.volatility().multiply(rsiWeight);

    BigDecimal volumeMultiplier =
      c.currentVolume()
        .divide(c.averageVolume(), 8, RoundingMode.HALF_UP)
        .min(BigDecimal.valueOf(2.5))
        .max(BigDecimal.valueOf(0.6));

    BigDecimal distanceToResistance =
      c.resistance().subtract(c.currentPrice())
        .divide(c.currentPrice(), 8, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100))
        .multiply(volumeMultiplier);

    BigDecimal trendBias =
      c.ema8().compareTo(c.ema21()) > 0
        ? BigDecimal.valueOf(0.5)
        : (c.priceSlope().compareTo(BigDecimal.ZERO) > 0
        ? BigDecimal.valueOf(0.2)
        : BigDecimal.valueOf(0.05));

    BigDecimal riskToSupport =
      c.currentPrice()
        .subtract(c.support())
        .divide(c.currentPrice(), 8, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100))
        .max(BigDecimal.valueOf(0.05));

    BigDecimal riskReturn =
      distanceToResistance.divide(riskToSupport, 8, RoundingMode.HALF_UP)
        .min(BigDecimal.valueOf(4.0))
        .max(BigDecimal.ONE);

    return volatilityFactor
      .add(distanceToResistance.multiply(BigDecimal.valueOf(0.3)))
      .add(trendBias)
      .add(BigDecimal.valueOf(0.1))
      .multiply(BigDecimal.valueOf(0.7).add(riskReturn.multiply(BigDecimal.valueOf(0.3))));
  }

  private boolean isPatternsAligned(List<KlineDto> klines, MarketConditions conditions, boolean bullish) {
    if (klines == null || klines.size() < 3) return false;

    boolean patternDetected = checkPatternFormations(klines, conditions.averageVolume(), bullish);

    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    BigDecimal bandPosition = conditions.currentPrice()
      .subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));

    // additional filter: only accept bullish if momentum not strongly negative
    if (patternDetected) {
      if (bullish && conditions.momentum().compareTo(BigDecimal.valueOf(-0.1)) < 0) return false;
      if (!bullish && conditions.momentum().compareTo(BigDecimal.valueOf(0.1)) > 0) return false;

      // bullish: near lower band, bearish: near upper band
      if (bullish && bandPosition.compareTo(BigDecimal.valueOf(15)) < 0) return true;
      if (!bullish && bandPosition.compareTo(BigDecimal.valueOf(85)) > 0) return true;
    }

    return patternDetected;
  }

  private boolean checkPatternFormations(List<KlineDto> klines, BigDecimal averageVolume, boolean bullish) {
    if (klines.size() < 4) return false;

    KlineDto current = klines.get(klines.size() - 1);
    KlineDto previous = klines.get(klines.size() - 2);
    KlineDto prePrevious = klines.get(klines.size() - 3);

    BigDecimal cOpen = new BigDecimal(current.getOpenPrice());
    BigDecimal cClose = new BigDecimal(current.getClosePrice());
    BigDecimal cHigh = new BigDecimal(current.getHighPrice());
    BigDecimal cLow = new BigDecimal(current.getLowPrice());
    BigDecimal cBody = cClose.subtract(cOpen).abs();
    BigDecimal cUpperShadow = cHigh.subtract(cClose.max(cOpen));
    BigDecimal cLowerShadow = cOpen.min(cClose).subtract(cLow);

    BigDecimal pOpen = new BigDecimal(previous.getOpenPrice());
    BigDecimal pClose = new BigDecimal(previous.getClosePrice());
    BigDecimal pBody = pClose.subtract(pOpen).abs();

    BigDecimal ppOpen = new BigDecimal(prePrevious.getOpenPrice());
    BigDecimal ppClose = new BigDecimal(prePrevious.getClosePrice());
    BigDecimal ppBody = ppClose.subtract(ppOpen).abs();

    boolean isBullishCurrent = cClose.compareTo(cOpen) > 0;
    boolean isBullishPrev = pClose.compareTo(pOpen) > 0;
    boolean isBullishPP = ppClose.compareTo(ppOpen) > 0;

    if (bullish) {
      // 🔹 Hammer
      boolean isHammer = !isBullishPrev && isBullishCurrent &&
        cLowerShadow.compareTo(cBody.multiply(BigDecimal.valueOf(2.5))) > 0 &&
        cUpperShadow.compareTo(cBody.multiply(BigDecimal.valueOf(0.3))) < 0;

      // 🔹 Bullish Engulfing
      boolean isEngulfing = !isBullishPrev && isBullishCurrent &&
        cBody.compareTo(pBody.multiply(BigDecimal.valueOf(1.5))) > 0 &&
        cOpen.compareTo(pClose) < 0 && cClose.compareTo(pOpen) > 0 &&
        new BigDecimal(current.getVolume()).compareTo(averageVolume) > 0;

      // 🔹 Morning Star
      boolean isMorningStar = !isBullishPP && ppBody.compareTo(BigDecimal.ZERO) > 0 &&
        pBody.compareTo(ppBody.multiply(BigDecimal.valueOf(0.5))) < 0 &&
        isBullishCurrent &&
        cClose.compareTo(ppOpen.add(ppBody.multiply(BigDecimal.valueOf(0.5)))) > 0;

      return isHammer || isEngulfing || isMorningStar;
    } else {
      // 🔹 Shooting Star
      boolean isShootingStar = isBullishPrev && !isBullishCurrent &&
        cUpperShadow.compareTo(cBody.multiply(BigDecimal.valueOf(2.5))) > 0 &&
        cLowerShadow.compareTo(cBody.multiply(BigDecimal.valueOf(0.3))) < 0;

      // 🔹 Bearish Engulfing
      boolean isBearishEngulfing = isBullishPrev && !isBullishCurrent &&
        cBody.compareTo(pBody.multiply(BigDecimal.valueOf(1.5))) > 0 &&
        cOpen.compareTo(pClose) > 0 && cClose.compareTo(pOpen) < 0 &&
        new BigDecimal(current.getVolume()).compareTo(averageVolume) > 0;

      // 🔹 Evening Star
      boolean isEveningStar = isBullishPP && ppBody.compareTo(BigDecimal.ZERO) > 0 &&
        pBody.compareTo(ppBody.multiply(BigDecimal.valueOf(0.5))) < 0 &&
        !isBullishCurrent &&
        cClose.compareTo(ppOpen.subtract(ppBody.multiply(BigDecimal.valueOf(0.5)))) < 0;

      return isShootingStar || isBearishEngulfing || isEveningStar;
    }
  }


}