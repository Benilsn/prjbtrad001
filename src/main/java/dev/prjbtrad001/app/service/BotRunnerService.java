package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.bot.SimpleTradeBot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import static dev.prjbtrad001.app.utils.LogUtils.LINE_SEPARATOR;
import static dev.prjbtrad001.app.utils.LogUtils.log;

@ApplicationScoped
public class BotRunnerService {

  @Inject
  TradingService tradingService;

  @Transactional
  public void executeBot(SimpleTradeBot bot) {
    if (!bot.isRunning()) return;

    try {
      long processTime = System.currentTimeMillis();

      log("[" + bot.getParameters().getBotType() + " - " + bot.getId() + "] Checking market data...");
      tradingService.analyzeMarket(bot);
      SimpleTradeBot.getEntityManager().merge(bot);

      log(String.format("[%s] - %d", bot.getParameters().getBotType(), System.currentTimeMillis() - processTime) + "ms to process bot: " + bot.getId());
      log(LINE_SEPARATOR, false);
    } catch (Exception e) {
      log("Error while running bot: " + e.getMessage());
    }
  }
}