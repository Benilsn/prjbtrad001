package dev.prjbtrad001.bot;

import dev.prjbtrad001.domain.bot.BotStatus;
import dev.prjbtrad001.domain.bot.TradeBot;
import dev.prjbtrad001.domain.bot.TradeRecord;
import dev.prjbtrad001.market.KlineDto;
import dev.prjbtrad001.market.MarketDataClient;
import dev.prjbtrad001.paper.PaperExecutor;
import dev.prjbtrad001.strategy.EmaCrossStrategy;
import dev.prjbtrad001.strategy.Signal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.jbosslog.JBossLog;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * One evaluation tick for a single bot: fetch candles, decide, paper-trade.
 *
 * Two prices, deliberately:
 *
 *  - the SIGNAL is derived from the last CLOSED candle (the still-forming one is
 *    dropped), so the loop can never act on data that hasn't happened yet;
 *  - the ORDER fills at the LIVE market price, because that is what a real fill
 *    would cost at the moment the bot acts.
 *
 * Using the closed-candle price for both would make paper results systematically
 * optimistic — and a flattering simulation is worse than no simulation.
 */
@JBossLog
@ApplicationScoped
public class BotRunner {

  @Inject
  MarketDataClient marketData;
  @Inject
  PaperExecutor paperExecutor;

  @Transactional
  public void runOnce(UUID botId) {
    TradeBot bot = TradeBot.findById(botId);
    if (bot == null || !bot.isRunning()) return;

    String symbol = bot.getSymbol().name();
    int limit = Math.min(Math.max(bot.getEmaSlow() * 4, 120), 500);

    List<KlineDto> candles = marketData.getCandles(symbol, bot.getTimeframe(), limit);
    if (candles.size() < bot.getEmaSlow() + 2) {
      log.warnf("[%s] not enough candles (%d) — skipping tick", symbol, candles.size());
      return;
    }

    // Drop the in-progress candle: the SIGNAL may only ever see closed data.
    List<KlineDto> closed = candles.subList(0, candles.size() - 1);
    BotStatus status = bot.getStatus();

    // Orders fill at the CURRENT market price — never at the candle close the
    // signal came from. On a daily timeframe those can be a full day apart, and
    // the gap is not random: entries fire in uptrends (so the real fill is
    // dearer) and exits in downtrends (so the real fill is cheaper). Filling at
    // the stale close would flatter every trade in both directions and make
    // paper results useless as evidence.
    BigDecimal livePrice = marketData.getPrice(symbol);
    if (livePrice == null) {
      // Skip rather than fall back to the stale close: that would silently
      // reintroduce the bias. The hourly tick retries soon enough.
      log.warnf("[%s] live price unavailable — skipping tick", symbol);
      return;
    }

    // Safety-net stop-loss against the live price, so a sharp drawdown is caught
    // on the next hourly tick instead of waiting for the next daily close.
    if (status.isOpen() && status.getAvgPrice().signum() > 0) {
      BigDecimal stopPrice = status.getAvgPrice()
        .multiply(BigDecimal.ONE.subtract(bot.getStopLossPercent().movePointLeft(2)));
      if (livePrice.compareTo(stopPrice) <= 0) {
        log.infof("[%s] ⛔ stop-loss at R$ %s (entry R$ %s)", symbol,
          livePrice.setScale(2, RoundingMode.HALF_UP), status.getAvgPrice().setScale(2, RoundingMode.HALF_UP));
        paperExecutor.sell(bot, livePrice, TradeRecord.Reason.STOP_LOSS);
        return;
      }
    }

    BarSeries series = EmaCrossStrategy.buildSeries(closed, symbol);
    Signal signal = EmaCrossStrategy.evaluateLast(series, bot.getEmaFast(), bot.getEmaSlow(), status.isOpen());

    switch (signal) {
      case ENTER -> paperExecutor.buy(bot, livePrice);
      case EXIT -> paperExecutor.sell(bot, livePrice, TradeRecord.Reason.EMA_CROSS);
      // Ticks are hourly at most, so one concise line per evaluation is useful
      // rather than noisy — it is the only live visibility into the loop.
      // Both prices are shown: the live one moves, the signal one only changes
      // when a new candle closes.
      case HOLD -> log.infof("[%s] ⚪ hold · %s · now R$ %s · signal candle R$ %s @ %s",
        symbol, status.isOpen() ? "LONG" : "flat",
        livePrice.setScale(2, RoundingMode.HALF_UP),
        closed.getLast().close().setScale(2, RoundingMode.HALF_UP),
        closed.getLast().closeInstant());
    }
  }
}
