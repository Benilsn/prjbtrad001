package dev.prjbtrad001.app.service;

import dev.prjbtrad001.domain.core.TradeBot;
import dev.prjbtrad001.app.repository.impl.FileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@JBossLog
@ApplicationScoped
public class BotOrchestratorService {

  private final Random rdn = new Random();

  @Inject
  private FileRepository fileRepository;

  public TradeBot createBot(TradeBot bot) {
    log.info("Creating bot: " + bot.getParameters().getBotType());
    fileRepository.createBot(bot);
    return bot;
  }

  public List<TradeBot> getAllBots() {
    List<TradeBot> bots = fileRepository.getAllBots();
    log.info("Getting all " + bots.size() + " bots.");
    return
      bots
        .stream()
        .sorted(Comparator.comparing(tradeBot -> !tradeBot.isRunning()))
        .toList();
  }


//  public void startBot(TradeBot.BotType botType, BotConfig config) {
//    if (getActiveBots().containsKey(botType)) return;
//    TradeBot bot = new SimpleTradeBot(botType, config);
//    ((Runnable) bot).run();
//    getActiveBots().put(botType, bot);
//  }
//
//  public void stopBot(String symbol) {
//    TradeBot bot = getActiveBots().remove(symbol);
//    if (bot != null) bot.stop();
//  }

//  @PreDestroy
//  public void stopAllBots() {
//    for (TradeBot bot : getActiveBots().values()) {
//      bot.stop();
//    }
//    getActiveBots().clear();
//  }


  public List<String> getLogData() {

    List<String> listOfData = new ArrayList<>();
    List<String> coins = List.of("BTC", "ETH", "XRP", "LTC", "BCH");
    List<String> operation = List.of("Buy", "Sell");

    for (int i = 0; i < rdn.nextInt(10, 99); i++) {
      listOfData.add(
        String.format("[%02d:%02d] %s %s at $%d",
          rdn.nextInt(0, 24), // Hour
          rdn.nextInt(0, 60), // Minute
          operation.get(rdn.nextInt(operation.size())), // Buy/Sell
          coins.get(rdn.nextInt(coins.size())), // Coin type
          rdn.nextInt(1000, 100000) // Price
        ));
    }
    return listOfData;
  }


}
