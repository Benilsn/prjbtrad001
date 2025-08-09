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

    List<KlineDto> klines = tradingExecutor.getCandles(
      parameters.getBotType().toString(),
      parameters.getInterval(),
      parameters.getCandlesAnalyzed()
    );

    MarketAnalyzer marketAnalyzer = new MarketAnalyzer();
    MarketConditions conditions = marketAnalyzer.analyzeMarket(klines, parameters);
    boolean isDownTrend = isDownTrendMarket(conditions);

    if (!status.isLong()) {
      evaluateBuySignal(bot, conditions, isDownTrend);
    } else {
      evaluateSellSignal(bot, conditions, isDownTrend);
    }
  }

  private void evaluateBuySignal(SimpleTradeBot bot, MarketConditions conditions, boolean isDownTrend) {
    BotParameters parameters = bot.getParameters();
    String botTypeName = "[" + parameters.getBotType() + "] - ";

    if (isDownTrend) {
      // Condições mais flexíveis para scalping
      boolean oversold = conditions.rsi().compareTo(BigDecimal.valueOf(35)) <= 0; // Menos rígido

      // Proximidade ao suporte em vez de exatamente no suporte
      BigDecimal supportFactor = BigDecimal.ONE.subtract(BigDecimal.valueOf(0.015)); // 1,5% do suporte
      boolean nearSupport = conditions.currentPrice().compareTo(
        conditions.support().multiply(supportFactor)) <= 0;

      // Volume pode ser normal ou acima - menos restritivo
      boolean adequateVolume = conditions.currentVolume().compareTo(
        conditions.averageVolume().multiply(BigDecimal.valueOf(0.8))) >= 0;

      // Adicionando bounce signal (inversão rápida)
      boolean potentialBounce = conditions.momentum().compareTo(BigDecimal.ZERO) > 0;

      log(botTypeName + "🔻 Downtrend detected");
      log(botTypeName + "📉 Oversold: " + oversold + " (RSI: " + conditions.rsi() + ")");
      log(botTypeName + "🛡️ Near Support: " + nearSupport);
      log(botTypeName + "📊 Volume: " + adequateVolume);
      log(botTypeName + "🔄 Momentum: " + potentialBounce);

      // Mais flexível - 3 de 4 condições
      if ((oversold ? 1 : 0) + (nearSupport ? 1 : 0) + (adequateVolume ? 1 : 0) + (potentialBounce ? 1 : 0) >= 3) {
        // Ajuste dinâmico baseado na força do sinal (0.3 a 0.7)
        BigDecimal signalStrength = calculateSignalStrength(conditions);
        BigDecimal reducedAmount = calculateOptimalBuyAmount(bot, conditions)
          .multiply(signalStrength);

        log(botTypeName + "🔵 Scalp BUY em downtrend! Força: " + signalStrength + " Valor: " + reducedAmount);
        executeBuyOrder(bot, reducedAmount);
      } else {
        log(botTypeName + "⚪ Sem sinal de scalp em downtrend");
      }
      return;
    }

    // Refined conditions for purchase
    boolean rsiOversold = conditions.rsi().compareTo(parameters.getRsiPurchase()) <= 0;

    boolean bullishTrend = conditions.sma9().compareTo(conditions.sma21()) > 0 &&
      conditions.ema8().compareTo(conditions.ema21()) > 0;

    boolean touchedSupport =
      conditions.currentPrice()
        .compareTo(conditions.support().multiply(BigDecimal.ONE.add(BigDecimal.valueOf(0.005)))) <= 0;

    boolean touchedBollingerLower = conditions.currentPrice().compareTo(conditions.bollingerLower()
      .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(0.02)))) <= 0;

    boolean strongVolume = conditions.currentVolume()
      .compareTo(conditions.averageVolume().multiply(parameters.getVolumeMultiplier())) >= 0;

    boolean positiveMonentum = conditions.momentum().compareTo(BigDecimal.ZERO) > 0;

    boolean lowVolatility = conditions.volatility().compareTo(BigDecimal.valueOf(3)) < 0;

    // Calcular posição percentual nas Bandas de Bollinger (0% = banda inferior, 100% = banda superior)
    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    BigDecimal pricePositionInBand = conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));

    // Para scalping, consideramos favorável quando preço está nos primeiros 20% da banda
    boolean bollingerBuyCondition = pricePositionInBand.compareTo(BigDecimal.valueOf(20)) <= 0;

    // Se o preço rompeu abaixo da banda inferior, sinal ainda mais forte
    boolean priceBelowBand = conditions.currentPrice().compareTo(conditions.bollingerLower()) < 0;
    if (priceBelowBand) {
      log(botTypeName + "⚡ Preço ABAIXO da Banda Inferior de Bollinger - Sinal forte de compra");
    }

    // Print logs of conditions
    log(botTypeName + "🔻 RSI Oversold: " + rsiOversold + " (" + conditions.rsi() + " <= " + parameters.getRsiPurchase() + ")");
    log(botTypeName + "📈 Bullish Trend: " + bullishTrend);
    log(botTypeName + "🛡️ Touched Support: " + touchedSupport);
    log(botTypeName + "📊 Volume: " + (strongVolume ? "STRONG" : "WEAK"));
    log(botTypeName + "🧲 Touched Bollinger Lower: " + touchedBollingerLower);
    log(botTypeName + "📊 Posição nas Bandas: " + pricePositionInBand + "% (< 20% = favorável)");

    TradingSignals buySignals = TradingSignals.builder()
      .rsiCondition(rsiOversold)
      .trendCondition(bullishTrend || touchedSupport)
      .volumeCondition(strongVolume)
      .priceCondition(touchedSupport || touchedBollingerLower)
      .momentumCondition(positiveMonentum)
      .volatilityCondition(lowVolatility)
      .stopLoss(false)
      .takeProfit(false)
      .bollingerBandCondition(bollingerBuyCondition)
      .build();

    if (buySignals.shouldBuy()) {
      log(botTypeName + "🔵 BUY signal detected!");
      executeBuyOrder(bot, calculateOptimalBuyAmount(bot, conditions));
    } else {
      log(botTypeName + "⚪ Insufficient conditions for purchase.");
    }
  }

  private void evaluateSellSignal(SimpleTradeBot bot, MarketConditions conditions, boolean isDownTrend) {
    BotParameters parameters = bot.getParameters();
    Status status = bot.getStatus();
    String botTypeName = "[" + parameters.getBotType() + "] - ";

    // Calculation of current profit/loss
    BigDecimal priceChangePercent = calculatePriceChangePercent(status, conditions.currentPrice());
    log(botTypeName + String.format("📉 Current price variation: %.2f%%", priceChangePercent));

    if (applyTrailingStop(bot, conditions)) {
      return;
    }

    if (isDownTrend) {
      // Cálculos de TP/SL dinâmicos baseados na volatilidade
      BigDecimal volatilityFactor;
      if (conditions.volatility().compareTo(BigDecimal.valueOf(3)) > 0) {
        volatilityFactor = BigDecimal.valueOf(0.4); // Volatilidade alta - TP/SL mais curtos
      } else if (conditions.volatility().compareTo(BigDecimal.valueOf(1.5)) > 0) {
        volatilityFactor = BigDecimal.valueOf(0.6); // Volatilidade média
      } else {
        volatilityFactor = BigDecimal.valueOf(0.8); // Volatilidade baixa
      }

      BigDecimal adjustedTP = parameters.getTakeProfitPercent().multiply(volatilityFactor);
      BigDecimal adjustedSL = parameters.getStopLossPercent().multiply(volatilityFactor);

      // Take profit mais agressivo em downtrend forte
      boolean priceWeakening = conditions.momentum().compareTo(BigDecimal.valueOf(-0.2)) < 0;
      boolean strongDowntrend = conditions.priceSlope().compareTo(BigDecimal.valueOf(-0.05)) < 0;

      if (priceWeakening && strongDowntrend) {
        adjustedTP = adjustedTP.multiply(BigDecimal.valueOf(0.7));
      }

      boolean smallTakeProfit = priceChangePercent.compareTo(adjustedTP) >= 0;
      boolean tightStopLoss = priceChangePercent.compareTo(adjustedSL.negate()) <= 0;

      // Reversão de RSI como sinal adicional de saída
      boolean rsiReversal = conditions.rsi().compareTo(BigDecimal.valueOf(55)) > 0 &&
        priceChangePercent.compareTo(BigDecimal.valueOf(0.3)) > 0;

      boolean timeout = checkPositionTimeout(bot, conditions, TradingConstants.POSITION_TIMEOUT_SECONDS / 3) &&
        priceChangePercent.compareTo(BigDecimal.ZERO) >= 0;

      log(botTypeName + "🔻 Downtrend detected");
      log(botTypeName + "💰 Adjusted TP (>= " + adjustedTP + "%): " + smallTakeProfit + " (" + priceChangePercent + "%)");
      log(botTypeName + "⛔ Adjusted SL (<= -" + adjustedSL + "%): " + tightStopLoss + " (" + priceChangePercent + "%)");
      log(botTypeName + "🔄 RSI Reversal: " + rsiReversal);

      if (smallTakeProfit || tightStopLoss || rsiReversal || timeout) {
        String reason = smallTakeProfit ? "Take Profit" : (tightStopLoss ? "Stop Loss" : (timeout ? "Timeout" : "RSI Reversal"));
        log(botTypeName + "🔴 SELL signal in downtrend! Reason: " + reason);
        executeSellOrder(bot);
        return;
      }
    }

    boolean rsiOverbought = conditions.rsi().compareTo(parameters.getRsiSale()) >= 0;
    boolean bearishTrend = conditions.sma9().compareTo(conditions.sma21()) < 0;
    boolean touchedResistance = conditions.currentPrice().compareTo(
      conditions.resistance().multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.005)))) >= 0;
    boolean touchedBollingerUpper = conditions.currentPrice().compareTo(
      conditions.bollingerUpper().multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.01)))) >= 0;
    boolean negativeMonentum = conditions.momentum().compareTo(BigDecimal.ZERO) < 0;

    // Evaluation of stop loss and take profit
    boolean reachedStopLoss = priceChangePercent.compareTo(parameters.getStopLossPercent().negate()) <= 0;
    boolean reachedTakeProfit = priceChangePercent.compareTo(parameters.getTakeProfitPercent()) >= 0;

    // Time stop - if position has been open for a long time
    boolean positionTimeout = checkPositionTimeout(bot, conditions, TradingConstants.POSITION_TIMEOUT_SECONDS) &&
      priceChangePercent.compareTo(BigDecimal.valueOf(0.5)) >= 0; // At least 0.5% profit

    // Dynamic stop loss if profit is already above 1.5%
    boolean dynamicStopLoss = priceChangePercent.compareTo(BigDecimal.valueOf(1.5)) >= 0 &&
      priceChangePercent.compareTo(priceChangePercent.multiply(BigDecimal.valueOf(0.7))) <= 0;

    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    BigDecimal pricePositionInBand = conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));

    // Condição de venda quando preço está nos últimos 20% da banda
    boolean bollingerSellCondition = pricePositionInBand.compareTo(BigDecimal.valueOf(80)) >= 0;

    // Preço acima da banda superior = sinal mais forte
    boolean priceAboveBand = conditions.currentPrice().compareTo(conditions.bollingerUpper()) > 0;
    if (priceAboveBand) {
      log(botTypeName + "⚡ Preço ACIMA da Banda Superior de Bollinger - Sinal forte de venda");
    }

    log(botTypeName + "🔺 RSI Overbought: " + rsiOverbought);
    log(botTypeName + "📉 Bearish Trend: " + bearishTrend);
    log(botTypeName + "🧲 Touched Resistance/Upper Band: " + (touchedResistance || touchedBollingerUpper));
    log(botTypeName + "⛔ Stop Loss: " + reachedStopLoss + ", Take Profit: " + reachedTakeProfit);
    log(botTypeName + "⏱️ Position Timeout: " + positionTimeout);
    log(botTypeName + "🔄 Dynamic Stop Loss: " + dynamicStopLoss);
    log(botTypeName + "📊 Posição nas Bandas: " + pricePositionInBand + "% (> 80% = favorável para venda)");

    TradingSignals sellSignals = TradingSignals.builder()
      .rsiCondition(rsiOverbought)
      .trendCondition(bearishTrend)
      .volumeCondition(false)
      .priceCondition(touchedResistance || touchedBollingerUpper)
      .momentumCondition(negativeMonentum && priceChangePercent.compareTo(BigDecimal.valueOf(0.8)) >= 0)
      .volatilityCondition(false)
      .stopLoss(reachedStopLoss || dynamicStopLoss)
      .takeProfit(reachedTakeProfit || positionTimeout)
      .bollingerBandCondition(bollingerSellCondition || priceAboveBand)
      .build();

    if (sellSignals.shouldSell()) {
      log(botTypeName + "🔴 SELL signal detected!");
      executeSellOrder(bot);
    } else {
      log(botTypeName + "⚪ Maintaining current position.");
    }
  }

  private BigDecimal calculateOptimalBuyAmount(SimpleTradeBot bot, MarketConditions conditions) {
    BotParameters parameters = bot.getParameters();
    BigDecimal baseAmount = parameters.getPurchaseAmount();
    BigDecimal adjustmentFactor = BigDecimal.ONE;

    // Ajuste por volatilidade - escala dinâmica
    if (conditions.volatility().compareTo(BigDecimal.valueOf(2)) >= 0) {
      BigDecimal volatilityFactor =
        BigDecimal.ONE
          .subtract(conditions.volatility().multiply(BigDecimal.valueOf(0.05)));
      // Limita redução máxima a 60%
      volatilityFactor = volatilityFactor.max(BigDecimal.valueOf(0.4));
      adjustmentFactor = adjustmentFactor.multiply(volatilityFactor);
    }

    // Ajuste por RSI - escala proporcional
    if (conditions.rsi().compareTo(BigDecimal.valueOf(30)) <= 0) {
      // Quanto menor o RSI, maior o ajuste (entre 1.0 e 1.5)
      BigDecimal rsiBoost =
        BigDecimal.valueOf(1.5)
          .subtract(conditions.rsi().divide(BigDecimal.valueOf(30), 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(0.5)));

      adjustmentFactor = adjustmentFactor.multiply(rsiBoost);
    }

    // Ajuste por proximidade ao suporte
    BigDecimal priceToSupport = conditions.currentPrice().divide(conditions.support(), 8, RoundingMode.HALF_UP);
    if (priceToSupport.compareTo(BigDecimal.valueOf(1.02)) <= 0) {  // Até 2% acima do suporte
      adjustmentFactor = adjustmentFactor.multiply(BigDecimal.valueOf(1.15));  // +15%
    }

    // Ajuste por posição nas Bandas de Bollinger
    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    BigDecimal pricePosition = conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP);

    if (pricePosition.compareTo(BigDecimal.valueOf(0.2)) <= 0) {  // Próximo da banda inferior
      adjustmentFactor = adjustmentFactor.multiply(BigDecimal.valueOf(1.1));  // +10%
    }

    // Redução durante tendência de baixa
    if (conditions.ema50().compareTo(conditions.ema100()) < 0 &&
      conditions.priceSlope().compareTo(BigDecimal.ZERO) < 0) {
      adjustmentFactor = adjustmentFactor.multiply(BigDecimal.valueOf(0.6));  // -40%
    }

    // Limites de segurança (entre 30% e 150% do valor base)
    BigDecimal minFactor = BigDecimal.valueOf(0.3);
    BigDecimal maxFactor = BigDecimal.valueOf(1.5);
    adjustmentFactor = adjustmentFactor.max(minFactor).min(maxFactor);

    return baseAmount.multiply(adjustmentFactor);
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

    log(botTypeName + "✅ Purchase executed: Average price = " + newAveragePrice + ", Quantity = " + newTotalQuantity);
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

    log(botTypeName + "✅ Sale executed successfully");
    log(botTypeName + String.format("💰 Profit after fees: R$%.2f (%.2f%%)", profit, profitPercent));
    log(botTypeName + String.format("💰 Accumulated profit: R$%.2f", totalProfit));
  }

  private boolean checkPositionTimeout(SimpleTradeBot bot, MarketConditions conditions, int timeoutSeconds) {
    if (!bot.getStatus().isLong()) return false;

    // Ajusta timeout com base na volatilidade
    double volatilityFactor = Math.min(2.0, Math.max(0.5, conditions.volatility().doubleValue() / 2.0));
    int adjustedTimeout = (int) (timeoutSeconds / volatilityFactor);

    return bot.getStatus().getLastPurchaseTime()
      .plusSeconds(adjustedTimeout)
      .isBefore(LocalDateTime.now());
  }

  private BigDecimal calculatePriceChangePercent(Status status, BigDecimal currentPrice) {
    if (status.getAveragePrice() == null || status.getAveragePrice().compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }

    return currentPrice.subtract(status.getAveragePrice())
      .divide(status.getAveragePrice(), 8, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(100));
  }

  private boolean isDownTrendMarket(MarketConditions conditions) {
    // Versão para scalping - indicadores mais rápidos
    boolean emaShortDowntrend = conditions.ema8().compareTo(conditions.ema21()) < 0;
    boolean priceDecreasing = conditions.priceSlope().compareTo(BigDecimal.ZERO) < 0;
    boolean belowBollingerMiddle = conditions.currentPrice().compareTo(conditions.bollingerMiddle()) < 0;

    // Para scalping, mais sensível - pelo menos 2 condições precisam ser verdadeiras
    int trueCount = 0;
    if (emaShortDowntrend) trueCount++;
    if (priceDecreasing) trueCount++;
    if (belowBollingerMiddle) trueCount++;

    return trueCount >= 2;
  }

  private BigDecimal calculateSignalStrength(MarketConditions conditions) {
    int positiveSignals = 0;
    int totalSignals = 5;

    // Avalia RSI - quanto mais baixo, melhor o sinal
    if (conditions.rsi().compareTo(BigDecimal.valueOf(35)) <= 0) positiveSignals++;
    if (conditions.rsi().compareTo(BigDecimal.valueOf(20)) <= 0) positiveSignals++; // RSI muito baixo é sinal forte

    // Avalia proximidade ao suporte
    BigDecimal priceToSupport = conditions.currentPrice().divide(conditions.support(), 8, RoundingMode.HALF_UP);
    if (priceToSupport.compareTo(BigDecimal.valueOf(1.01)) <= 0) positiveSignals++; // Muito próximo ao suporte

    // Avalia momentum positivo
    if (conditions.momentum().compareTo(BigDecimal.ZERO) > 0) positiveSignals++;

    // Avalia posição nas bandas de Bollinger (próximo à banda inferior)
    BigDecimal bandWidth = conditions.bollingerUpper().subtract(conditions.bollingerLower());
    BigDecimal positionInBand = conditions.currentPrice().subtract(conditions.bollingerLower())
      .divide(bandWidth, 8, RoundingMode.HALF_UP);
    if (positionInBand.compareTo(BigDecimal.valueOf(0.2)) <= 0) positiveSignals++;

    // Calcula força do sinal entre 0.3 e 0.7
    BigDecimal signalRatio = BigDecimal.valueOf(positiveSignals)
      .divide(BigDecimal.valueOf(totalSignals), 8, RoundingMode.HALF_UP);

    return BigDecimal.valueOf(0.3)
      .add(signalRatio.multiply(BigDecimal.valueOf(0.4)));
  }

  private boolean applyTrailingStop(SimpleTradeBot bot, MarketConditions conditions) {
    Status status = bot.getStatus();
    BigDecimal currentProfit = calculatePriceChangePercent(status, conditions.currentPrice());

    // Inicia trailing quando lucro > 0.5%
    if (currentProfit.compareTo(BigDecimal.valueOf(0.5)) > 0) {
      // Calcula 70% do lucro atual como stop dinâmico
      BigDecimal trailingLevel = currentProfit.multiply(BigDecimal.valueOf(0.7));

      // Atualiza o trailing stop se for maior que o anterior
      if (status.getTrailingStopLevel() == null ||
        trailingLevel.compareTo(status.getTrailingStopLevel()) > 0) {
        status.setTrailingStopLevel(trailingLevel);
        log("[" + bot.getParameters().getBotType() + "] - 🔄 Trailing stop atualizado: " + trailingLevel + "%");
      }
    }

    // Verifica se o preço recuou abaixo do trailing stop
    if (status.getTrailingStopLevel() != null &&
      currentProfit.compareTo(status.getTrailingStopLevel()) < 0) {
      log("[" + bot.getParameters().getBotType() + "] - 🔴 Trailing Stop atingido em " + currentProfit + "%");
      executeSellOrder(bot);
      return true; // Indica que uma venda foi executada
    }

    return false; // Nenhuma venda foi executada
  }

}