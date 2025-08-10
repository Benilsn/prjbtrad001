package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.bot.BotParameters;
import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.app.bot.Status;
import dev.prjbtrad001.app.core.MarketConditions;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.*;

import static dev.prjbtrad001.app.service.TradingService.calculatePriceChangePercent;
import static dev.prjbtrad001.app.utils.LogUtils.log;

@JBossLog
@ApplicationScoped
public class LogService {

  private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(
    r -> new Thread(r, "trading-log-thread"));

  private final Map<String, MarketConditionsSnapshot> latestConditions =
    new ConcurrentHashMap<>();

  @PreDestroy
  void cleanup() {
    logExecutor.shutdown();
    try {
      if (!logExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        logExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      logExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private void captureMarketSnapshot(SimpleTradeBot bot, MarketConditions conditions,
                                     boolean isDownTrend) {
    String botId = bot.getParameters().getBotType().toString();
    latestConditions.put(botId, new MarketConditionsSnapshot(
      bot, conditions, isDownTrend));
  }

  // Chamado dos métodos evaluateBuySignal e evaluateSellSignal
  public void logSignals(SimpleTradeBot bot, MarketConditions conditions,
                         boolean isDownTrend) {
    // Captura snapshot thread-safe das condições atuais
    captureMarketSnapshot(bot, conditions, isDownTrend);

    // Enviar para processamento assíncrono
    CompletableFuture.runAsync(() ->
        processLogsAsync(bot.getParameters().getBotType().toString()),
      logExecutor);
  }

  private void processLogsAsync(String botId) {
    try {
      MarketConditionsSnapshot snapshot = latestConditions.get(botId);
      if (snapshot == null) return;

      SimpleTradeBot bot = snapshot.bot;
      MarketConditions conditions = snapshot.conditions;
      boolean isDownTrend = snapshot.isDownTrend;

      BotParameters parameters = bot.getParameters();
      String botTypeName = "[" + parameters.getBotType() + "] - ";

      // Informações de preço e médias móveis
      log(botTypeName + "📊 Current Price: " + conditions.currentPrice().setScale(5, RoundingMode.HALF_UP) +
        " | SMA9: " + conditions.sma9().setScale(5, RoundingMode.HALF_UP) +
        " | SMA21: " + conditions.sma21().setScale(5, RoundingMode.HALF_UP) +
        " | Ratio: " + conditions.sma9().divide(conditions.sma21(), 4, RoundingMode.HALF_UP));

      // Indicadores técnicos com interpretação
      BigDecimal rsi = conditions.rsi().setScale(2, RoundingMode.HALF_UP);
      String rsiInterpretation = rsi.compareTo(BigDecimal.valueOf(70)) > 0 ? "🔴 Overbought" :
        (rsi.compareTo(BigDecimal.valueOf(30)) < 0 ? "🔵 Oversold" : "⚪ Neutral");

      log(botTypeName + "📈 RSI: " + rsi + " " + rsiInterpretation +
        " | Volatility: " + conditions.volatility().setScale(4, RoundingMode.HALF_UP) +
        " | Momentum: " + conditions.momentum().setScale(2, RoundingMode.HALF_UP));

      // Tendência do mercado
      String trendDirection = isDownTrend ? "🔻 BEARISH" : "🔺 BULLISH";
      String slopeDirection = conditions.priceSlope().compareTo(BigDecimal.ZERO) > 0 ? "↗️" : "↘️";
      log(botTypeName + "🔍 Market Trend: " + trendDirection +
        " | Slope: " + slopeDirection + " " + conditions.priceSlope().setScale(5, RoundingMode.HALF_UP));

      // Bandas de Bollinger com gap percentual
      BigDecimal upperGapPercent = conditions.bollingerUpper().subtract(conditions.currentPrice())
        .divide(conditions.currentPrice(), 4, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

      BigDecimal lowerGapPercent = conditions.currentPrice().subtract(conditions.bollingerLower())
        .divide(conditions.currentPrice(), 4, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

      log(botTypeName + "📉 Bollinger Bands: Upper=" + conditions.bollingerUpper().setScale(2, RoundingMode.HALF_UP) +
        " (" + upperGapPercent + "% away)" +
        " | Middle=" + conditions.bollingerMiddle().setScale(2, RoundingMode.HALF_UP) +
        " | Lower=" + conditions.bollingerLower().setScale(2, RoundingMode.HALF_UP) +
        " (" + lowerGapPercent + "% away)");

      // Posição nas bandas
      BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
      BigDecimal pricePositionInBand = conditions.currentPrice().subtract(conditions.bollingerLower())
        .divide(bandWidth, 8, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100));

      String bandPosition = pricePositionInBand.compareTo(BigDecimal.valueOf(80)) > 0 ? "🔴 Upper Band (Sell Zone)" :
        pricePositionInBand.compareTo(BigDecimal.valueOf(20)) < 0 ? "🔵 Lower Band (Buy Zone)" :
          "⚪ Middle Band (Neutral)";

      log(botTypeName + "🧲 Band Position: " + pricePositionInBand.setScale(2, RoundingMode.HALF_UP) + "% - " + bandPosition);

      // Volume com interpretação
      BigDecimal volumeRatio = conditions.currentVolume().divide(conditions.averageVolume(), 5, RoundingMode.HALF_UP);
      String volumeInterpretation = volumeRatio.compareTo(BigDecimal.valueOf(1.2)) > 0 ? "🔊 High" :
        volumeRatio.compareTo(BigDecimal.valueOf(0.8)) < 0 ? "🔈 Low" :
          "🔉 Normal";

      log(botTypeName + "📊 Volume: Current=" + conditions.currentVolume().setScale(5, RoundingMode.HALF_UP) +
        " | Average=" + conditions.averageVolume().setScale(5, RoundingMode.HALF_UP) +
        " | Ratio=" + volumeRatio + " " + volumeInterpretation);

      // Informações específicas para downtrend
      if (isDownTrend) {
        boolean emaShortDowntrend = conditions.ema8().compareTo(conditions.ema21()) < 0;
        BigDecimal emaRatio = conditions.ema8().divide(conditions.ema21(), 4, RoundingMode.HALF_UP);
        log(botTypeName + "🔻 BEARISH TREND - Additional Details:");
        log(botTypeName + "📉 Price Slope: " + conditions.priceSlope().setScale(5, RoundingMode.HALF_UP) +
          " | Bearish strength: " + (conditions.priceSlope().abs().multiply(BigDecimal.valueOf(100))).setScale(2, RoundingMode.HALF_UP));
        log(botTypeName + "📉 EMA Short-Term Downtrend: " + emaShortDowntrend +
          " (EMA8/EMA21: " + emaRatio + " - Bearish when < 1.0)");
      }
    } catch (Exception e) {
      LogService.log.error("Error processing async logs: " + e.getMessage(), e);
    }
  }

  public void processBuySignalLogs(SimpleTradeBot bot, MarketConditions conditions, String botTypeName) {
    boolean touchedSupport = conditions.currentPrice()
      .compareTo(conditions.support().multiply(BigDecimal.ONE.add(BigDecimal.valueOf(0.005)))) <= 0;

    log(botTypeName + "🛡️ Touched Support: " + touchedSupport + " (Price: " + conditions.currentPrice().setScale(2, RoundingMode.HALF_UP) +
      " | Support: " + conditions.support().setScale(2, RoundingMode.HALF_UP) + ")");

    BigDecimal priceToSupport = conditions.currentPrice().divide(conditions.support(), 4, RoundingMode.HALF_UP);
    BigDecimal priceToBollingerLower = conditions.currentPrice().divide(conditions.bollingerLower(), 4, RoundingMode.HALF_UP);

    log(botTypeName + "📏 Distance to Support: " + priceToSupport.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) +
      "% | To Lower Band: " + priceToBollingerLower.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%");
  }

  public void processSellSignalLogs(SimpleTradeBot bot, MarketConditions conditions, String botTypeName) {
    BotParameters parameters = bot.getParameters();
    Status status = bot.getStatus();
    BigDecimal priceChangePercent = status.isLong() ? calculatePriceChangePercent(status, conditions.currentPrice()) : BigDecimal.ZERO;

    boolean touchedResistance = conditions.currentPrice().compareTo(
      conditions.resistance().multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.005)))) >= 0;

    log(botTypeName + "🧲 Touched Resistance: " + touchedResistance + " (Price: " + conditions.currentPrice() +
      " | Resistance: " + conditions.resistance() + ")");

    boolean stopLossTriggered = priceChangePercent.compareTo(parameters.getStopLossPercent().negate()) <= 0;
    boolean takeProfitTriggered = priceChangePercent.compareTo(parameters.getTakeProfitPercent()) >= 0;

    log(botTypeName + "⛔ Stop Loss Triggered: " + stopLossTriggered +
      " (" + priceChangePercent.setScale(2, RoundingMode.HALF_UP) + "% / -" + parameters.getStopLossPercent() + "%)");
    log(botTypeName + "💰 Take Profit Triggered: " + takeProfitTriggered +
      " (" + priceChangePercent.setScale(2, RoundingMode.HALF_UP) + "% / " + parameters.getTakeProfitPercent() + "%)");
  }

  private record MarketConditionsSnapshot(SimpleTradeBot bot, MarketConditions conditions, boolean isDownTrend) { }
}