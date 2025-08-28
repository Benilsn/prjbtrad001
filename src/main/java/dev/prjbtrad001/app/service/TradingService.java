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
      evaluateBuySignal(bot, conditions, isDownTrend);
    } else {
      evaluateSellSignal(bot, conditions, isDownTrend);
    }
  }

  private void evaluateBuySignal(SimpleTradeBot bot, MarketConditions conditions, boolean isDownTrend) {
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
        .build();

    // 🔹 TODO Incluir 'Reason' no log
    if (tradingSignals.shouldBuy()) {
      log(botTypeName + "🔵 BUY signal in " + (isDownTrend ? "Down" : "Normal") + " trend detected!");
      executeBuyOrder(bot, calculateOptimalBuyAmount(bot, conditions));
    } else {
      log(botTypeName + "⚪ Insufficient conditions for purchase.");
    }

  }

  private void evaluateSellSignal(SimpleTradeBot bot, MarketConditions conditions, boolean isDownTrend) {
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
    boolean minimumProfit = priceChangePercent.compareTo(MIN_PROFIT_THRESHOLD) >= 0;

    TradingSignals.Bearish tradingSignals =
      TradingSignals.Bearish.builder()
        .rsiCondition(rsiOverbought)
        .trendCondition(bearishTrend)
        .priceCondition(touchedResistance || touchedBollingerUpper)
        .macdCondition(macdNegative)
        .stochCondition(stochasticOverbought)
        .momentumCondition(negativeMomentum)
        .volumeCondition(highVolatility)
        .stopLoss(reachedStopLoss)
        .takeProfit(reachedTakeProfit)
        .minimumProfitReached(minimumProfit)
        .build();

    //TODO Incluir 'Reason' no log
    if (tradingSignals.shouldSell()) {
      log(botTypeName + "🔴 SELL signal detected!");
      executeSellOrder(bot);
      return;
    } else {
      log(botTypeName + "⚪ No SELL signal in " + (isDownTrend ? "Down" : "Normal") + " trend, maintaining current position.", true);
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
    if (priceToSupport.compareTo(BigDecimal.valueOf(1.01)) <= 0) {  // Até 1% acima do suporte (mais sensível)
      adjustmentFactor = adjustmentFactor.multiply(BigDecimal.valueOf(1.2));  // +20%
    }

    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    BigDecimal pricePosition = conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP);

    if (pricePosition.compareTo(BigDecimal.valueOf(0.15)) <= 0) {
      adjustmentFactor = adjustmentFactor.multiply(BigDecimal.valueOf(1.15));  // +15%
    }

    if (conditions.ema50().compareTo(conditions.ema100()) < 0 &&
      conditions.priceSlope().compareTo(BigDecimal.ZERO) < 0) {
      adjustmentFactor = adjustmentFactor.multiply(BigDecimal.valueOf(0.5));  // -50%
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

  private boolean isPriceRejection(KlineDto lastKline, BigDecimal averageVolume, BigDecimal atr) {
    BigDecimal open = new BigDecimal(lastKline.getOpenPrice());
    BigDecimal close = new BigDecimal(lastKline.getClosePrice());
    BigDecimal high = new BigDecimal(lastKline.getHighPrice());
    BigDecimal low = new BigDecimal(lastKline.getLowPrice());

    BigDecimal body = close.subtract(open).abs();
    BigDecimal upperShadow = high.subtract(open.max(close));
    BigDecimal lowerShadow = open.min(close).subtract(low);

    if (body.compareTo(atr.multiply(BigDecimal.valueOf(0.2))) < 0) {
      return false;
    }

    BigDecimal volume = new BigDecimal(lastKline.getVolume());
    if (volume.compareTo(averageVolume.multiply(BigDecimal.valueOf(1.2))) < 0) {
      return false;
    }

    boolean bullishRejection =
      lowerShadow.compareTo(body.multiply(BigDecimal.valueOf(2))) > 0 &&
        close.compareTo(open) > 0;

    boolean bearishRejection =
      upperShadow.compareTo(body.multiply(BigDecimal.valueOf(2))) > 0 &&
        close.compareTo(open) < 0;

    return bullishRejection || bearishRejection;
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

  private boolean hasRecentReversalPattern(List<KlineDto> klines, MarketConditions conditions) {
    if (klines == null || klines.size() < 3) return false;

    boolean patternDetected = checkPatternFormations(klines, conditions.averageVolume());
    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    BigDecimal bandPosition = conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));

    if (patternDetected) {
      if (conditions.momentum().compareTo(BigDecimal.valueOf(-0.1)) < 0) return false;
      if (bandPosition.compareTo(BigDecimal.valueOf(0.15)) < 0) return false;
    }

    return patternDetected;
  }

  private boolean checkPatternFormations(List<KlineDto> klines, BigDecimal averageVolume) {
    if (klines.size() < 4) return false;

    KlineDto currentCandle = klines.getLast();
    KlineDto previousCandle = klines.get(klines.size() - 2);
    KlineDto prePreviousCandle = klines.get(klines.size() - 3);

    BigDecimal cOpen = new BigDecimal(currentCandle.getOpenPrice());
    BigDecimal cClose = new BigDecimal(currentCandle.getClosePrice());
    BigDecimal cHigh = new BigDecimal(currentCandle.getHighPrice());
    BigDecimal cLow = new BigDecimal(currentCandle.getLowPrice());

    BigDecimal pOpen = new BigDecimal(previousCandle.getOpenPrice());
    BigDecimal pClose = new BigDecimal(previousCandle.getClosePrice());

    BigDecimal ppOpen = new BigDecimal(prePreviousCandle.getOpenPrice());
    BigDecimal ppClose = new BigDecimal(prePreviousCandle.getClosePrice());

    BigDecimal cBody = cClose.subtract(cOpen).abs();
    BigDecimal pBody = pClose.subtract(pOpen).abs();
    BigDecimal ppBody = ppClose.subtract(ppOpen).abs();

    BigDecimal cUpperShadow = cHigh.subtract(cClose.max(cOpen));
    BigDecimal cLowerShadow = cOpen.min(cClose).subtract(cLow);

    boolean isBullishCurrent = cClose.compareTo(cOpen) > 0;
    boolean isBullishPrev = pClose.compareTo(pOpen) > 0;
    boolean isBullishPP = ppClose.compareTo(ppOpen) > 0;

    // 🔹 Hammer
    boolean isHammer =
      !isBullishPrev && isBullishCurrent &&
        cLowerShadow.compareTo(cBody.multiply(BigDecimal.valueOf(2.5))) > 0 &&
        cUpperShadow.compareTo(cBody.multiply(BigDecimal.valueOf(0.3))) < 0;

    // 🔹 Bullish Engulfing (with volume confirmation)
    boolean isBullishEngulfing =
      !isBullishPrev && isBullishCurrent &&
        cBody.compareTo(pBody.multiply(BigDecimal.valueOf(1.5))) > 0 &&
        cOpen.compareTo(pClose) < 0 && cClose.compareTo(pOpen) > 0 &&
        new BigDecimal(currentCandle.getVolume()).compareTo(averageVolume) > 0;

    // 🔹 Morning Star
    boolean isMorningStar =
      !isBullishPP && ppBody.compareTo(BigDecimal.ZERO) > 0 &&
        pBody.compareTo(ppBody.multiply(BigDecimal.valueOf(0.5))) < 0 &&
        isBullishCurrent &&
        cClose.compareTo(ppOpen.add(ppBody.multiply(BigDecimal.valueOf(0.5)))) > 0;

    return isHammer || isBullishEngulfing || isMorningStar;
  }


}