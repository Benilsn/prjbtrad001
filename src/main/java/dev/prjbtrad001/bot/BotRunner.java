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
 * Decisions are made only on the last CLOSED candle — the still-forming candle
 * is dropped — so the live loop can never act on data that hasn't happened yet.
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

    // Drop the in-progress candle: decide on closed data only.
    List<KlineDto> closed = candles.subList(0, candles.size() - 1);
    BigDecimal price = closed.getLast().close();
    BotStatus status = bot.getStatus();

    // Safety-net stop-loss, checked against the real average entry price.
    if (status.isOpen() && status.getAvgPrice().signum() > 0) {
      BigDecimal stopPrice = status.getAvgPrice()
        .multiply(BigDecimal.ONE.subtract(bot.getStopLossPercent().movePointLeft(2)));
      if (price.compareTo(stopPrice) <= 0) {
        log.infof("[%s] ⛔ stop-loss at R$ %s (entry R$ %s)", symbol,
          price.setScale(2, RoundingMode.HALF_UP), status.getAvgPrice().setScale(2, RoundingMode.HALF_UP));
        paperExecutor.sell(bot, price, TradeRecord.Reason.STOP_LOSS);
        return;
      }
    }

    BarSeries series = EmaCrossStrategy.buildSeries(closed, symbol);
    Signal signal = EmaCrossStrategy.evaluateLast(series, bot.getEmaFast(), bot.getEmaSlow(), status.isOpen());

    switch (signal) {
      case ENTER -> paperExecutor.buy(bot, price);
      case EXIT -> paperExecutor.sell(bot, price, TradeRecord.Reason.EMA_CROSS);
      // Ticks are hourly at most, so one concise line per evaluation is useful
      // rather than noisy — it is the only live visibility into the loop.
      case HOLD -> log.infof("[%s] ⚪ hold · %s · R$ %s · closed candle %s",
        symbol, status.isOpen() ? "LONG" : "flat",
        price.setScale(2, RoundingMode.HALF_UP), closed.getLast().closeInstant());
    }
  }
}
