package dev.prjbtrad001.app;


import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.domain.config.BotConfig;
import dev.prjbtrad001.domain.core.TradeBot;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class BotOrchestratorService {

  private final Map<String, TradeBot> activeBots = new ConcurrentHashMap<>();

  public void startBot(String symbol, BotConfig config) {
    if (activeBots.containsKey(symbol)) return;
    TradeBot bot = new SimpleTradeBot(symbol, config);
    ((Runnable) bot).run();
    activeBots.put(symbol, bot);
  }

  public void stopBot(String symbol) {
    TradeBot bot = activeBots.remove(symbol);
    if (bot != null) bot.stop();
  }

  public Map<String, TradeBot> getActiveBots() {
    return activeBots;
  }

  @PreDestroy
  public void stopAllBots() {
    for (TradeBot bot : activeBots.values()) {
      bot.stop();
    }
    activeBots.clear();
  }

  public enum BotType {
    BTC_TRADE_BOT,
    ETH_TRADE_BOT
  }
}
