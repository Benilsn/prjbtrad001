package dev.prjbtrad001.strategy;

import dev.prjbtrad001.market.KlineDto;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.StopLossRule;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dual-EMA crossover — the whole strategy, in one place.
 *
 *   Entry : fast EMA crosses ABOVE slow EMA
 *   Exit  : fast EMA crosses BELOW slow EMA, OR the stop-loss trips
 *
 * The exact same rules drive both the backtest ({@link #buildStrategy}) and the
 * live paper loop ({@link #evaluateLast}). Live evaluation only looks at the
 * cross rules; the stop-loss is enforced by the runner against the bot's real
 * fill price, which is more faithful than a synthetic trading record.
 */
public final class EmaCrossStrategy {

  private EmaCrossStrategy() {
  }

  /**
   * Builds a ta4j series from candles. Each bar ends at the candle's close time,
   * so only closed candles should be passed in (no lookahead / repainting).
   */
  public static BarSeries buildSeries(List<KlineDto> candles, String name) {
    BarSeries series = new BaseBarSeriesBuilder().withName(name).build();
    for (KlineDto k : candles) {
      series.barBuilder()
        .timePeriod(k.period())
        .endTime(k.closeInstant())
        .openPrice(k.open().doubleValue())
        .highPrice(k.high().doubleValue())
        .lowPrice(k.low().doubleValue())
        .closePrice(k.close().doubleValue())
        .volume(k.volume().doubleValue())
        .add();
    }
    return series;
  }

  /**
   * Full strategy including the stop-loss — used for backtesting via a
   * {@code BarSeriesManager}, which tracks entry prices in the trading record.
   */
  public static Strategy buildStrategy(BarSeries series, int emaFast, int emaSlow, BigDecimal stopLossPercent) {
    ClosePriceIndicator close = new ClosePriceIndicator(series);
    EMAIndicator fast = new EMAIndicator(close, emaFast);
    EMAIndicator slow = new EMAIndicator(close, emaSlow);

    Rule entry = new CrossedUpIndicatorRule(fast, slow);
    Rule exit = new CrossedDownIndicatorRule(fast, slow)
      .or(new StopLossRule(close, series.numFactory().numOf(stopLossPercent)));

    return new BaseStrategy("EMA " + emaFast + "x" + emaSlow, entry, exit);
  }

  /**
   * Live decision at the most recent (closed) bar, based on the EMA cross only.
   * Returns HOLD when there is not enough history to be meaningful.
   */
  public static Signal evaluateLast(BarSeries series, int emaFast, int emaSlow, boolean currentlyOpen) {
    if (series.getBarCount() <= emaSlow + 1) return Signal.HOLD;

    ClosePriceIndicator close = new ClosePriceIndicator(series);
    EMAIndicator fast = new EMAIndicator(close, emaFast);
    EMAIndicator slow = new EMAIndicator(close, emaSlow);

    int end = series.getEndIndex();
    Rule crossUp = new CrossedUpIndicatorRule(fast, slow);
    Rule crossDown = new CrossedDownIndicatorRule(fast, slow);

    if (!currentlyOpen && crossUp.isSatisfied(end)) return Signal.ENTER;
    if (currentlyOpen && crossDown.isSatisfied(end)) return Signal.EXIT;
    return Signal.HOLD;
  }
}
