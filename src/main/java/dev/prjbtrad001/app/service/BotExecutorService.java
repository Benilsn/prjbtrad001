package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.bot.SimpleTradeBot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

import static dev.prjbtrad001.app.utils.LogUtils.LINE_SEPARATOR;
import static dev.prjbtrad001.app.utils.LogUtils.log;

@ApplicationScoped
public class BotExecutorService {

  @Inject
  TradingService tradingService;

  @Transactional
  public void executeById(UUID botId) {
    SimpleTradeBot bot = SimpleTradeBot.find("id", botId).firstResult();

    if (bot == null || !bot.isRunning()) return;

    try {
      long start = System.currentTimeMillis();

      log(LINE_SEPARATOR, false);
      log("[" + bot.getParameters().getBotType() + " - " + bot.getId() + "] Checking market data...");
      tradingService.analyzeMarket(bot);
      log("[" + bot.getParameters().getBotType() + "] - Took " + (System.currentTimeMillis() - start) + "ms to process bot: " + bot.getId());

    } catch (Exception e) {
      log("Error while running bot: " + e.getMessage());
    }
  }
}