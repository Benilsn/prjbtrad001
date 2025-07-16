package dev.prjbtrad001.app.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.domain.config.BotConfig;
import dev.prjbtrad001.domain.core.TradeBot;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.jbosslog.JBossLog;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@JBossLog
@ApplicationScoped
public class BotOrchestratorService {

  private final Random rdn = new Random();

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

//  public Map<TradeBot.BotType, TradeBot> getActiveBots() {
//    Map<TradeBot.BotType, TradeBot> activeBots = new ConcurrentHashMap<>();
//
//    try {
//      InputStream is = getClass().getClassLoader().getResourceAsStream("bots");
//      if (is == null) throw new IllegalStateException("File 'bots' not found in resources");
//
//      try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
//        String line;
//        while ((line = reader.readLine()) != null) {
//          TradeBot bot = mapper.readValue(line, TradeBot.class);
//          activeBots.add(bot);
//        }
//      }
//
//    } catch (Exception e) {
//      log.error("Error reading bot configurations: " + e.getMessage());
//    }
//
//    return activeBots;
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
