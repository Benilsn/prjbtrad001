package dev.prjbtrad001.backtest;

import dev.prjbtrad001.market.KlineDto;
import dev.prjbtrad001.market.MarketDataClient;
import dev.prjbtrad001.strategy.EmaCrossStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.ta4j.core.BarSeries;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the EMA-cross strategy over historical candles and reports how it would
 * have performed — the single most important tool in the project.
 *
 * ta4j supplies the honest part: correct indicator math and lookahead-free entry
 * and exit timing (including the stop-loss). We then compute the money side
 * ourselves — a fixed nominal capital that compounds trade-to-trade, with the
 * fee charged on both sides — so the numbers are in BRL and reflect real cost
 * drag rather than a version-specific criterion's semantics.
 */
@JBossLog
@ApplicationScoped
public class BacktestEngine {

  private static final int MAX_CANDLES = 2000;
  private static final DateTimeFormatter FMT =
    DateTimeFormatter.ofPattern("dd/MM/yy HH:mm").withZone(ZoneId.systemDefault());

  @Inject
  MarketDataClient marketData;

  @ConfigProperty(name = "bot.paper.initial-balance")
  BigDecimal initialBalance;

  public BacktestResult run(BacktestRequest req) {
    if (req.emaFast() >= req.emaSlow()) {
      return BacktestResult.error("Fast EMA must be smaller than slow EMA.");
    }

    int wanted = Math.min(Math.max(req.candles(), req.emaSlow() + 10), MAX_CANDLES);
    List<KlineDto> candles = marketData.getCandlesRange(req.symbol(), req.timeframe(), wanted);
    if (candles.size() < req.emaSlow() + 10) {
      return BacktestResult.error("Not enough historical data returned ("
        + candles.size() + " candles). Try a larger timeframe or fewer periods.");
    }

    BarSeries series = EmaCrossStrategy.buildSeries(candles, req.symbol());
    Strategy strategy = EmaCrossStrategy.buildStrategy(series, req.emaFast(), req.emaSlow(), req.stopLossPercent());

    TradingRecord record = new BarSeriesManager(series).run(strategy);

    int bars = series.getBarCount();
    double[] close = new double[bars];
    long[] times = new long[bars];
    for (int i = 0; i < bars; i++) {
      close[i] = series.getBar(i).getClosePrice().doubleValue();
      times[i] = series.getBar(i).getEndTime().toEpochMilli();
    }

    // Map bar index -> entry / exit for the equity walk.
    Map<Integer, Integer> entryToExit = new HashMap<>();
    for (Position p : record.getPositions()) {
      if (p.getEntry() != null && p.getExit() != null) {
        entryToExit.put(p.getEntry().getIndex(), p.getExit().getIndex());
      }
    }
    Position current = record.getCurrentPosition();
    Integer openEntryIdx = (current != null && current.isOpened() && current.getEntry() != null)
      ? current.getEntry().getIndex() : null;

    double feeRate = req.feePercent().doubleValue() / 100.0;
    double initial = initialBalance.doubleValue();

    // ── Strategy equity curve (mark-to-market each bar) ──
    List<Double> equity = new ArrayList<>(bars);
    List<BacktestResult.TradeRow> tradeRows = new ArrayList<>();

    double capital = initial;
    double units = 0;
    boolean inPos = false;
    double entryCapital = 0, entryPrice = 0;
    long entryTime = 0;
    int tradeNo = 0;
    int wins = 0;
    double grossWin = 0, grossLoss = 0;
    Integer pendingExit = null;

    for (int i = 0; i < bars; i++) {
      double price = close[i];

      // Enter on this bar's close if the record opened a position here.
      if (!inPos && (entryToExit.containsKey(i) || (openEntryIdx != null && openEntryIdx == i))) {
        double fee = capital * feeRate;
        entryCapital = capital;
        entryPrice = price;
        entryTime = times[i];
        units = (capital - fee) / price;
        capital = 0;
        inPos = true;
        pendingExit = entryToExit.get(i); // null when this is the still-open position
      }

      double barEquity = inPos ? units * price : capital;

      // Exit on this bar's close if scheduled.
      if (inPos && pendingExit != null && pendingExit == i) {
        double proceeds = units * price;
        double fee = proceeds * feeRate;
        capital = proceeds - fee;
        double netPct = (capital / entryCapital - 1) * 100.0;
        boolean win = capital >= entryCapital;
        if (win) { wins++; grossWin += (capital - entryCapital); }
        else { grossLoss += (entryCapital - capital); }
        tradeRows.add(new BacktestResult.TradeRow(
          ++tradeNo, FMT.format(java.time.Instant.ofEpochMilli(entryTime)),
          FMT.format(java.time.Instant.ofEpochMilli(times[i])),
          entryPrice, price, round2(netPct), win));
        barEquity = capital;
        inPos = false;
        units = 0;
        pendingExit = null;
      }

      equity.add(round2(barEquity));
    }

    double finalEquity = equity.isEmpty() ? initial : equity.getLast();
    double netReturnPct = (finalEquity / initial - 1) * 100.0;

    // ── Buy & hold benchmark ──
    List<Double> buyHold = new ArrayList<>(bars);
    double bhUnits = (initial - initial * feeRate) / close[0];
    for (int i = 0; i < bars; i++) buyHold.add(round2(bhUnits * close[i]));
    double bhFinal = bhUnits * close[bars - 1];
    bhFinal -= bhFinal * feeRate;
    double buyHoldReturnPct = (bhFinal / initial - 1) * 100.0;

    // ── Drawdown ──
    double peak = Double.NEGATIVE_INFINITY, maxDd = 0;
    for (double eq : equity) {
      peak = Math.max(peak, eq);
      if (peak > 0) maxDd = Math.max(maxDd, (peak - eq) / peak * 100.0);
    }

    double winRate = tradeNo == 0 ? 0 : (double) wins / tradeNo * 100.0;
    double profitFactor = grossLoss == 0 ? (grossWin > 0 ? Double.POSITIVE_INFINITY : 0) : grossWin / grossLoss;

    List<Long> timeList = new ArrayList<>(bars);
    for (long t : times) timeList.add(t);

    log.infof("Backtest %s %s EMA%dx%d: %d candles, %d trades, return %.2f%% (B&H %.2f%%)",
      req.symbol(), req.timeframe(), req.emaFast(), req.emaSlow(), bars, tradeNo, netReturnPct, buyHoldReturnPct);

    return new BacktestResult(
      true, null,
      req.symbol(), req.timeframe(), req.emaFast(), req.emaSlow(), bars,
      FMT.format(java.time.Instant.ofEpochMilli(times[0])),
      FMT.format(java.time.Instant.ofEpochMilli(times[bars - 1])),
      round2(initial), round2(finalEquity), round2(netReturnPct), round2(buyHoldReturnPct),
      round2(maxDd), tradeNo, round2(winRate),
      Double.isInfinite(profitFactor) ? profitFactor : round2(profitFactor),
      equity, buyHold, timeList, tradeRows);
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }
}
