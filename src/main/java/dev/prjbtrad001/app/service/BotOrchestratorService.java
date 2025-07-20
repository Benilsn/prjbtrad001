package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.domain.repository.BotRepository;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.*;

@JBossLog
@ApplicationScoped
public class BotOrchestratorService {

  private final Random rdn = new Random();

  private BotRepository<SimpleTradeBot> botRepository;

  @Inject
  Instance<BotRepository<SimpleTradeBot>> repositories;

  @ConfigProperty(name = "bot.data.strategy")
  String dataStrategy;


  public SimpleTradeBot createBot(SimpleTradeBot bot) {
    log.debug("Creating bot: " + bot.getParameters().getBotType());
    botRepository.createBot(bot);
    return bot;
  }

  public List<SimpleTradeBot> getAllBots() {
    List<SimpleTradeBot> bots = botRepository.getAllBots();
    log.debug("Getting all " + bots.size() + " bots.");
    return
      bots
        .stream()
        .sorted(Comparator.comparing(tradeBot -> !tradeBot.isRunning()))
        .toList();
  }

  public void deleteBot(UUID botId) {
    log.debug("Deleting bot: " + botId);
    botRepository.deleteBot(botId);
  }

  public SimpleTradeBot getBotById(UUID botId) {
    log.debug("Getting bot by ID: " + botId);
    return
      botRepository
        .getBotById(botId)
        .orElseThrow(() -> new NoSuchElementException("Bot not found with ID: " + botId));
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

  @PostConstruct
  public void init() {
    this.botRepository =
      repositories
        .select(NamedLiteral.of(dataStrategy))
        .get();
  }


}
