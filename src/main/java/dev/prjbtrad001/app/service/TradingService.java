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

import static dev.prjbtrad001.app.core.TradingConstants.*;
import static dev.prjbtrad001.app.utils.LogUtils.log;
import static dev.prjbtrad001.infra.exception.ErrorCode.*;

/**
 * Swing-trade execution engine.
 *
 * Strategy summary:
 *  - Only enter longs above EMA200 (macro trend filter)
 *  - Wait for RSI pullback to 30–55 before buying
 *  - Confirm with MACD histogram turn + volume + support proximity
 *  - ATR-based stop loss (1.5x ATR below entry)
 *  - Trailing stop activates at +1% profit, keeps 65% of peak
 *  - Exit when RSI overbought + MACD turning down, or at resistance
 */
@JBossLog
@ApplicationScoped
public class TradingService {

  @Inject
  TradingExecutor tradingExecutor;

  @Transactional
  public void analyzeMarket(SimpleTradeBot bot) {
    BotParameters params = bot.getParameters();
    String tag = "[" + params.getBotType() + "] ";

    if (bot.isTradingPaused()) {
      log(tag + "⛔ Paused until " + bot.getPauseUntil()
        + " (consecutive losses: " + bot.getConsecutiveLosses() + ")", true);
      return;
    }

    List<KlineDto> klines = tradingExecutor.getCandles(
      params.getBotType().toString(),
      params.getInterval(),
      params.getCandlesAnalyzed()
    );

    MarketAnalyzer analyzer = new MarketAnalyzer();
    MarketConditions conditions = analyzer.analyzeMarket(klines, params);

    if (!bot.getStatus().isLong()) {
      evaluateBuySignal(bot, conditions, klines);
    } else {
      evaluateSellSignal(bot, conditions, klines);
    }
  }

  // ──────────────────────────────────────────────────────────────
  //  BUY evaluation
  // ──────────────────────────────────────────────────────────────
  private void evaluateBuySignal(SimpleTradeBot bot,
                                 MarketConditions c,
                                 List<KlineDto> klines) {
    BotParameters params = bot.getParameters();
    String tag = "[" + params.getBotType() + "] ";

    // ── Pre-flight: is potential profit worth the fees? ──────────
    BigDecimal distanceToResistance = c.resistance().subtract(c.currentPrice())
      .divide(c.currentPrice(), 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));

    if (distanceToResistance.compareTo(MIN_PROFIT_THRESHOLD) <= 0) {
      log(tag + "⚠️ Distance to resistance (" + distanceToResistance.setScale(2, RoundingMode.HALF_UP)
        + "%) below threshold. Skipping.");
      return;
    }

    // ── Condition 1: Macro trend — price above EMA200 ─────────────
    boolean trendAboveEma200 = c.currentPrice().compareTo(c.ema200()) > 0;

    // ── Condition 2: RSI in swing buy window (30–55) ─────────────
    boolean rsiInBuyWindow = c.rsi().compareTo(RSI_SWING_BUY_MIN) >= 0
      && c.rsi().compareTo(RSI_SWING_BUY_MAX) <= 0;

    // ── Condition 3: MACD histogram turned positive ───────────────
    // histogram > 0 means the MACD line is above its signal line (bullish cross)
    boolean macdTurningUp = c.macdHistogram().compareTo(MACD_HISTOGRAM_MIN) > 0;

    // ── Condition 4: Price at support ────────────────────────────
    BigDecimal supportZone = c.support().multiply(BigDecimal.valueOf(1.015)); // within 1.5%
    BigDecimal ema50Zone   = c.ema50().multiply(BigDecimal.valueOf(1.015));
    BigDecimal lowerBandZone = c.bollingerLower().multiply(BigDecimal.valueOf(1.02));
    boolean priceAtSupport = c.currentPrice().compareTo(supportZone) <= 0
      || c.currentPrice().compareTo(ema50Zone) <= 0
      || c.currentPrice().compareTo(lowerBandZone) <= 0;

    // ── Condition 5: Volume confirmation ─────────────────────────
    boolean volumeConfirmed = c.currentVolume()
      .compareTo(c.averageVolume().multiply(VOLUME_ENTRY_MULTIPLIER)) >= 0;

    // ── Condition 6: Candlestick pattern ─────────────────────────
    boolean bullishPattern = isBullishPattern(klines, c);

    // ── Condition 7: R:R check (distance to resist vs ATR-based stop) ─
    BigDecimal atrStop     = c.atr().multiply(BigDecimal.valueOf(ATR_STOP_MULTIPLIER));
    BigDecimal distToStop  = atrStop.divide(c.currentPrice(), 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));
    boolean favourableRR   = distanceToResistance.compareTo(BigDecimal.ZERO) > 0
      && distToStop.compareTo(BigDecimal.ZERO) > 0
      && distanceToResistance.divide(distToStop, 4, RoundingMode.HALF_UP)
      .compareTo(BigDecimal.valueOf(1.5)) >= 0; // min 1.5:1 R:R

    MarketType marketType = MarketType.classifyMarket(c);

    // ── Log summary ───────────────────────────────────────────────
    log(tag + "📊 Market: " + marketType, true);
    log(tag + icon(trendAboveEma200) + " Above EMA200: price=" + fmt(c.currentPrice()) + " ema200=" + fmt(c.ema200()), true);
    log(tag + icon(rsiInBuyWindow)   + " RSI buy window [30–55]: " + c.rsi().setScale(1, RoundingMode.HALF_UP), true);
    log(tag + icon(macdTurningUp)    + " MACD histogram positive: " + c.macdHistogram().setScale(4, RoundingMode.HALF_UP), true);
    log(tag + icon(priceAtSupport)   + " Price at support zone", true);
    log(tag + icon(volumeConfirmed)  + " Volume confirmed: " + fmt(c.currentVolume()) + " vs avg " + fmt(c.averageVolume()), true);
    log(tag + icon(bullishPattern)   + " Bullish candle pattern", true);
    log(tag + icon(favourableRR)     + " R:R ratio: " + distanceToResistance.setScale(2, RoundingMode.HALF_UP) + "% / " + distToStop.setScale(2, RoundingMode.HALF_UP) + "%", true);

    TradingSignals.Bullish signal = new TradingSignals.Bullish(
      trendAboveEma200, rsiInBuyWindow, macdTurningUp,
      priceAtSupport, volumeConfirmed, bullishPattern,
      favourableRR, marketType
    );

    if (signal.shouldBuy()) {
      log(tag + "🔵 BUY signal — confidence: " + signal.confidenceScore() + "/100");
      executeBuyOrder(bot, c, calculateBuyAmount(bot, c));
    } else {
      log(tag + "⚪ No buy signal.", true);
    }
  }

  // ──────────────────────────────────────────────────────────────
  //  SELL evaluation
  // ──────────────────────────────────────────────────────────────
  private void evaluateSellSignal(SimpleTradeBot bot,
                                  MarketConditions c,
                                  List<KlineDto> klines) {
    BotParameters params = bot.getParameters();
    Status status = bot.getStatus();
    String tag = "[" + params.getBotType() + "] ";

    BigDecimal priceChangePct = calculatePriceChangePercent(status, c.currentPrice());
    log(tag + String.format("📈 P&L: %.2f%% (take-profit: %.2f%%, stop: -%.2f%%)",
      priceChangePct.doubleValue(),
      params.getTakeProfitPercent().doubleValue(),
      params.getStopLossPercent().doubleValue()), true);

    MarketType marketType = MarketType.classifyMarket(c);

    // ── Trailing stop check ───────────────────────────────────────
    if (applyTrailingStop(bot, c, tag)) return;

    // ── ATR-based hard stop (overrides the configured stop loss if tighter) ──
    BigDecimal atrStop = c.atr().multiply(BigDecimal.valueOf(ATR_STOP_MULTIPLIER))
      .divide(status.getAveragePrice() != null ? status.getAveragePrice() : c.currentPrice(),
        8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));
    boolean stopLoss = priceChangePct.compareTo(params.getStopLossPercent().negate()) <= 0
      || priceChangePct.compareTo(atrStop.negate()) <= 0;

    boolean takeProfit = priceChangePct.compareTo(params.getTakeProfitPercent()) >= 0;

    // ── RSI overbought ────────────────────────────────────────────
    boolean rsiOverbought = c.rsi().compareTo(RSI_OVERBOUGHT) >= 0;

    // ── MACD turned negative ──────────────────────────────────────
    boolean macdTurningDown = c.macdHistogram().compareTo(BigDecimal.ZERO) < 0;

    // ── Price at resistance ───────────────────────────────────────
    boolean priceAtResistance = c.currentPrice()
      .compareTo(c.resistance().multiply(BigDecimal.valueOf(0.995))) >= 0;
    boolean priceAtUpperBand  = c.currentPrice()
      .compareTo(c.bollingerUpper().multiply(BigDecimal.valueOf(0.99))) >= 0;

    // ── Bearish candle pattern ────────────────────────────────────
    boolean bearishPattern = isBearishPattern(klines, c);

    log(tag + icon(!stopLoss)         + " Stop loss: " + priceChangePct.setScale(2, RoundingMode.HALF_UP) + "% (ATR stop: " + atrStop.setScale(2, RoundingMode.HALF_UP) + "%)", true);
    log(tag + icon(takeProfit)        + " Take profit reached", true);
    log(tag + icon(rsiOverbought)     + " RSI overbought: " + c.rsi().setScale(1, RoundingMode.HALF_UP), true);
    log(tag + icon(macdTurningDown)   + " MACD histogram negative: " + c.macdHistogram().setScale(4, RoundingMode.HALF_UP), true);
    log(tag + icon(priceAtResistance) + " Price at resistance: " + fmt(c.currentPrice()) + " vs " + fmt(c.resistance()), true);
    log(tag + icon(bearishPattern)    + " Bearish candle pattern", true);
    log(tag + "📊 Market: " + marketType, true);

    TradingSignals.Bearish signal = new TradingSignals.Bearish(
      stopLoss, takeProfit, rsiOverbought, macdTurningDown,
      priceAtResistance, priceAtUpperBand, bearishPattern,
      false, // trailing stop handled separately above
      marketType
    );

    if (signal.shouldSell()) {
      log(tag + "🔴 SELL signal detected");
      executeSellOrder(bot);
    } else {
      log(tag + "⚪ Holding position.", true);
    }
  }

  // ──────────────────────────────────────────────────────────────
  //  Position sizing
  // ──────────────────────────────────────────────────────────────
  private BigDecimal calculateBuyAmount(SimpleTradeBot bot, MarketConditions c) {
    BotParameters params = bot.getParameters();
    BigDecimal base = params.getPurchaseAmount();

    // Scale down in high volatility, scale up in low volatility with good RSI
    double volatilityPct = c.volatility()
      .divide(c.currentPrice(), 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100)).doubleValue();

    double factor;
    if (volatilityPct > 2.5)      factor = 0.5;
    else if (volatilityPct > 1.5) factor = 0.75;
    else if (volatilityPct < 0.5) factor = 1.2; // quiet market — slightly more
    else                           factor = 1.0;

    // RSI deeply oversold → slight boost (better entry)
    if (c.rsi().compareTo(BigDecimal.valueOf(35)) < 0) factor = Math.min(factor * 1.15, 1.3);

    BigDecimal sized = base.multiply(BigDecimal.valueOf(factor))
      .max(base.multiply(BigDecimal.valueOf(0.3)))  // never below 30% of base
      .min(base.multiply(BigDecimal.valueOf(1.3))); // never above 130% of base

    return bot.getAdjustedPositionSize(sized);
  }

  // ──────────────────────────────────────────────────────────────
  //  Trailing stop
  // ──────────────────────────────────────────────────────────────
  private boolean applyTrailingStop(SimpleTradeBot bot, MarketConditions c, String tag) {
    Status status = bot.getStatus();
    BigDecimal profit = calculatePriceChangePercent(status, c.currentPrice());

    // Only activates after the position is up enough to cover fees + margin
    if (profit.compareTo(TRAILING_ACTIVATION) < 0) return false;

    // Update the high-water mark
    if (status.getTrailingStopLevel() == null
      || profit.compareTo(status.getTrailingStopLevel()) > 0) {
      status.setTrailingStopLevel(profit);
      log(tag + "🔄 Trailing stop high-water: " + profit.setScale(2, RoundingMode.HALF_UP) + "%", true);
    }

    // Stop if profit has fallen to KEEP_FRACTION of peak
    BigDecimal stopLevel = status.getTrailingStopLevel().multiply(TRAILING_KEEP_FRACTION);
    if (profit.compareTo(stopLevel) < 0) {
      log(tag + "🔴 Trailing stop hit — peak: " + status.getTrailingStopLevel().setScale(2, RoundingMode.HALF_UP)
        + "% → now: " + profit.setScale(2, RoundingMode.HALF_UP) + "%");
      executeSellOrder(bot);
      return true;
    }
    return false;
  }

  // ──────────────────────────────────────────────────────────────
  //  Order execution
  // ──────────────────────────────────────────────────────────────
  private void executeBuyOrder(SimpleTradeBot bot, MarketConditions c, BigDecimal amount) {
    BotParameters params = bot.getParameters();
    Status status = bot.getStatus();
    String tag = "[" + params.getBotType() + "] ";

    BigDecimal valueToBuy = amount;
    if (params.getPurchaseStrategy() == PurchaseStrategy.PERCENTAGE) {
      valueToBuy = tradingExecutor.getBalance()
        .orElseThrow(() -> new TradeException(BALANCE_NOT_FOUND.getMessage()))
        .balance()
        .multiply(amount)
        .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
    }

    log(tag + "💰 Placing buy order: R$ " + valueToBuy.setScale(2, RoundingMode.HALF_UP));

    TradeOrderDto order = tradingExecutor
      .placeBuyOrder(params.getBotType().name(), valueToBuy)
      .orElseThrow(() -> new TradeException(FAILED_TO_PLACE_BUY_ORDER.getMessage()));

    BigDecimal qty     = order.quantity();
    BigDecimal spent   = order.totalSpentBRL();

    BigDecimal newQty      = status.getQuantity() != null ? status.getQuantity().add(qty) : qty;
    BigDecimal newPurchased = status.getTotalPurchased() != null ? status.getTotalPurchased().add(spent) : spent;
    BigDecimal newAvg      = newPurchased.divide(newQty, 8, RoundingMode.HALF_UP);

    status.setQuantity(newQty);
    status.setTotalPurchased(newPurchased);
    status.setAveragePrice(newAvg);
    status.setLastPurchaseTime(LocalDateTime.now());
    status.setLong(true);
    status.setTrailingStopLevel(null); // reset trailing stop on new position

    log(tag + "✅ Bought " + qty.setScale(6, RoundingMode.HALF_UP)
      + " @ avg R$ " + newAvg.setScale(2, RoundingMode.HALF_UP));
  }

  private void executeSellOrder(SimpleTradeBot bot) {
    BotParameters params = bot.getParameters();
    Status status = bot.getStatus();
    String tag = "[" + params.getBotType() + "] ";

    if (!status.isLong()
      || status.getQuantity() == null
      || status.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
      log(tag + "⚠️ Sell called with no open position — skipping");
      return;
    }

    log(tag + "💰 Placing sell order");

    TradeOrderDto order = tradingExecutor.placeSellOrder(params.getBotType().name())
      .orElseThrow(() -> new TradeException(FAILED_TO_PLACE_SELL_ORDER.getMessage()));

    BigDecimal received = order.trades().stream()
      .map(t -> t.price().multiply(t.quantity()))
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal invested     = status.getTotalPurchased();
    BigDecimal profit       = received.subtract(invested);
    BigDecimal totalProfit  = status.getProfit() != null ? status.getProfit().add(profit) : profit;
    BigDecimal profitPct    = profit.divide(invested, 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));

    // Reset position
    status.setProfit(totalProfit);
    status.setQuantity(BigDecimal.ZERO);
    status.setTotalPurchased(BigDecimal.ZERO);
    status.setAveragePrice(BigDecimal.ZERO);
    status.setLastPurchaseTime(null);
    status.setTrailingStopLevel(null);
    status.setLong(false);
    bot.addTradeResult(profit.compareTo(BigDecimal.ZERO) > 0);

    String profitEmoji = profit.compareTo(BigDecimal.ZERO) > 0 ? "💚" : "🔴";
    log(tag + profitEmoji + String.format(" Trade result: R$ %.2f (%.2f%%)", profit, profitPct));
    log(tag + "📊 Accumulated profit: R$ " + totalProfit.setScale(2, RoundingMode.HALF_UP));
    log(tag + "✅ Sell executed");
  }

  // ──────────────────────────────────────────────────────────────
  //  Candlestick pattern detection
  // ──────────────────────────────────────────────────────────────
  private boolean isBullishPattern(List<KlineDto> klines, MarketConditions c) {
    if (klines.size() < 3) return false;

    KlineDto curr = klines.get(klines.size() - 1);
    KlineDto prev = klines.get(klines.size() - 2);
    KlineDto pprev = klines.get(klines.size() - 3);

    BigDecimal cOpen  = new BigDecimal(curr.getOpenPrice());
    BigDecimal cClose = new BigDecimal(curr.getClosePrice());
    BigDecimal cHigh  = new BigDecimal(curr.getHighPrice());
    BigDecimal cLow   = new BigDecimal(curr.getLowPrice());
    BigDecimal cBody  = cClose.subtract(cOpen).abs();
    BigDecimal cLowerShadow = cOpen.min(cClose).subtract(cLow);
    BigDecimal cUpperShadow = cHigh.subtract(cClose.max(cOpen));

    BigDecimal pOpen  = new BigDecimal(prev.getOpenPrice());
    BigDecimal pClose = new BigDecimal(prev.getClosePrice());
    BigDecimal pBody  = pClose.subtract(pOpen).abs();

    BigDecimal ppOpen  = new BigDecimal(pprev.getOpenPrice());
    BigDecimal ppClose = new BigDecimal(pprev.getClosePrice());
    BigDecimal ppBody  = ppClose.subtract(ppOpen).abs();

    boolean isBullishCurr = cClose.compareTo(cOpen) > 0;
    boolean isBullishPrev = pClose.compareTo(pOpen) > 0;
    boolean isBullishPP   = ppClose.compareTo(ppOpen) > 0;

    // Hammer: long lower shadow, small upper shadow, closed bullish
    boolean hammer = !isBullishPrev && isBullishCurr
      && cLowerShadow.compareTo(cBody.multiply(BigDecimal.valueOf(2.0))) > 0
      && cUpperShadow.compareTo(cBody.multiply(BigDecimal.valueOf(0.4))) < 0;

    // Bullish engulfing: current green body engulfs prior red body
    boolean engulfing = !isBullishPrev && isBullishCurr
      && cBody.compareTo(pBody.multiply(BigDecimal.valueOf(1.3))) > 0
      && cOpen.compareTo(pClose) < 0 && cClose.compareTo(pOpen) > 0
      && new BigDecimal(curr.getVolume()).compareTo(c.averageVolume()) > 0;

    // Morning star: big red, small doji, big green
    boolean morningStar = !isBullishPP
      && pBody.compareTo(ppBody.multiply(BigDecimal.valueOf(0.4))) < 0
      && isBullishCurr
      && cClose.compareTo(ppOpen.add(ppBody.multiply(BigDecimal.valueOf(0.5)))) > 0;

    return hammer || engulfing || morningStar;
  }

  private boolean isBearishPattern(List<KlineDto> klines, MarketConditions c) {
    if (klines.size() < 3) return false;

    KlineDto curr = klines.get(klines.size() - 1);
    KlineDto prev = klines.get(klines.size() - 2);
    KlineDto pprev = klines.get(klines.size() - 3);

    BigDecimal cOpen  = new BigDecimal(curr.getOpenPrice());
    BigDecimal cClose = new BigDecimal(curr.getClosePrice());
    BigDecimal cHigh  = new BigDecimal(curr.getHighPrice());
    BigDecimal cBody  = cClose.subtract(cOpen).abs();
    BigDecimal cUpperShadow = cHigh.subtract(cClose.max(cOpen));
    BigDecimal cLowerShadow = cOpen.min(cClose).subtract(new BigDecimal(curr.getLowPrice()));

    BigDecimal pOpen  = new BigDecimal(prev.getOpenPrice());
    BigDecimal pClose = new BigDecimal(prev.getClosePrice());
    BigDecimal pBody  = pClose.subtract(pOpen).abs();

    BigDecimal ppOpen  = new BigDecimal(pprev.getOpenPrice());
    BigDecimal ppClose = new BigDecimal(pprev.getClosePrice());
    BigDecimal ppBody  = ppClose.subtract(ppOpen).abs();

    boolean isBullishPrev = pClose.compareTo(pOpen) > 0;
    boolean isBullishPP   = ppClose.compareTo(ppOpen) > 0;
    boolean isBearishCurr = cClose.compareTo(cOpen) < 0;

    // Shooting star: long upper shadow, small lower shadow, closed bearish
    boolean shootingStar = isBullishPrev && isBearishCurr
      && cUpperShadow.compareTo(cBody.multiply(BigDecimal.valueOf(2.0))) > 0
      && cLowerShadow.compareTo(cBody.multiply(BigDecimal.valueOf(0.4))) < 0;

    // Bearish engulfing
    boolean engulfing = isBullishPrev && isBearishCurr
      && cBody.compareTo(pBody.multiply(BigDecimal.valueOf(1.3))) > 0
      && cOpen.compareTo(pClose) > 0 && cClose.compareTo(pOpen) < 0
      && new BigDecimal(curr.getVolume()).compareTo(c.averageVolume()) > 0;

    // Evening star
    boolean eveningStar = isBullishPP
      && pBody.compareTo(ppBody.multiply(BigDecimal.valueOf(0.4))) < 0
      && isBearishCurr
      && cClose.compareTo(ppOpen.subtract(ppBody.multiply(BigDecimal.valueOf(0.5)))) < 0;

    return shootingStar || engulfing || eveningStar;
  }

  // ──────────────────────────────────────────────────────────────
  //  Helpers
  // ──────────────────────────────────────────────────────────────
  public static BigDecimal calculatePriceChangePercent(Status status, BigDecimal currentPrice) {
    if (status.getAveragePrice() == null || status.getAveragePrice().compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return currentPrice.subtract(status.getAveragePrice())
      .divide(status.getAveragePrice(), 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));
  }

  private static String icon(boolean condition) { return condition ? "🟢" : "🔴"; }

  private static String fmt(BigDecimal v) {
    return v == null ? "N/A" : v.setScale(4, RoundingMode.HALF_UP).toPlainString();
  }
}
