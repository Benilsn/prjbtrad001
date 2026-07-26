package dev.prjbtrad001.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prjbtrad001.backtest.BacktestEngine;
import dev.prjbtrad001.backtest.BacktestRequest;
import dev.prjbtrad001.backtest.BacktestResult;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * The backtest page: a form and, after running, the equity curve + metrics.
 */
@Path("/backtest")
public class BacktestResource {

  private static final DateTimeFormatter LABEL =
    DateTimeFormatter.ofPattern("dd/MM/yy").withZone(ZoneId.systemDefault());
  private static final int MAX_CHART_POINTS = 320;

  @Inject
  BacktestEngine engine;
  @Inject
  ObjectMapper mapper;

  @ConfigProperty(name = "bot.symbol.list")
  List<String> symbols;
  @ConfigProperty(name = "bot.paper.fee-rate")
  BigDecimal feeRate;
  @ConfigProperty(name = "bot.strategy.defaults.timeframe")
  String defTimeframe;
  @ConfigProperty(name = "bot.strategy.defaults.ema-fast")
  int defEmaFast;
  @ConfigProperty(name = "bot.strategy.defaults.ema-slow")
  int defEmaSlow;
  @ConfigProperty(name = "bot.strategy.defaults.stop-loss-percent")
  BigDecimal defStop;

  @GET
  public TemplateInstance page() {
    return base(null, null, defaults());
  }

  @POST
  @Path("/run")
  @Consumes("application/x-www-form-urlencoded")
  public TemplateInstance run(
    @FormParam("symbol") String symbol,
    @FormParam("timeframe") String timeframe,
    @FormParam("emaFast") int emaFast,
    @FormParam("emaSlow") int emaSlow,
    @FormParam("stopLossPercent") BigDecimal stop,
    @FormParam("candles") int candles,
    @FormParam("feePercent") BigDecimal feePercent) {

    BacktestRequest req = new BacktestRequest(symbol, timeframe, emaFast, emaSlow, stop, candles, feePercent);
    BacktestResult result = engine.run(req);

    Map<String, Object> submitted = new HashMap<>();
    submitted.put("symbol", symbol);
    submitted.put("timeframe", timeframe);
    submitted.put("emaFast", emaFast);
    submitted.put("emaSlow", emaSlow);
    submitted.put("stop", stop);
    submitted.put("candles", candles);
    submitted.put("feePercent", feePercent);

    String chartJson = result.ok() ? buildChartJson(result) : "null";
    return base(result, chartJson, submitted);
  }

  private TemplateInstance base(BacktestResult result, String chartJson, Map<String, Object> submitted) {
    return Templates.backtest()
      .data("pageTitle", "Backtest")
      .data("symbols", symbols)
      .data("timeframes", BotResource.TIMEFRAMES)
      .data("result", result)
      .data("chartJson", chartJson)
      .data("form", submitted);
  }

  private Map<String, Object> defaults() {
    Map<String, Object> m = new HashMap<>();
    m.put("symbol", symbols.isEmpty() ? "BTCBRL" : symbols.getFirst());
    m.put("timeframe", defTimeframe);
    m.put("emaFast", defEmaFast);
    m.put("emaSlow", defEmaSlow);
    m.put("stop", defStop);
    m.put("candles", 500);
    // fee-rate is a fraction (0.001); the form takes a percentage (0.1)
    m.put("feePercent", feeRate.movePointRight(2));
    return m;
  }

  /** Serialises a down-sampled equity/buy-hold series for Chart.js. */
  private String buildChartJson(BacktestResult r) {
    List<Double> equity = r.equityCurve();
    List<Double> buyHold = r.buyHoldCurve();
    List<Long> times = r.times();
    int n = equity.size();
    int step = Math.max(1, n / MAX_CHART_POINTS);

    List<String> labels = new ArrayList<>();
    List<Double> eq = new ArrayList<>();
    List<Double> bh = new ArrayList<>();
    for (int i = 0; i < n; i += step) {
      labels.add(LABEL.format(java.time.Instant.ofEpochMilli(times.get(i))));
      eq.add(equity.get(i));
      bh.add(buyHold.get(i));
    }
    // Always include the final point.
    if ((n - 1) % step != 0) {
      labels.add(LABEL.format(java.time.Instant.ofEpochMilli(times.get(n - 1))));
      eq.add(equity.get(n - 1));
      bh.add(buyHold.get(n - 1));
    }

    Map<String, Object> chart = new LinkedHashMap<>();
    chart.put("labels", labels);
    chart.put("equity", eq);
    chart.put("buyHold", bh);
    try {
      return mapper.writeValueAsString(chart);
    } catch (Exception e) {
      return "null";
    }
  }
}
