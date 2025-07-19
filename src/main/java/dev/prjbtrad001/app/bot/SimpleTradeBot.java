package dev.prjbtrad001.app.bot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.prjbtrad001.domain.core.TradeBot;
import dev.prjbtrad001.infra.config.BotConfig;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
@Getter
@Setter
@NoArgsConstructor
public class SimpleTradeBot implements TradeBot, Runnable {

  private TradeBot.BotType botType = BotType.BTCUSDT;
  private BotConfig config;
  private volatile boolean running = false;

  @JsonIgnore
  private Thread worker;

  public SimpleTradeBot(TradeBot.BotType botType, BotConfig config) {
    this.botType = botType;
    this.config = config;
  }

  @Override
  public void start() {
    if (running) return;
    running = true;
    worker = new Thread(this);
    worker.start();
  }

  @Override
  public void stop() {
    running = false;
    if (worker != null) worker.interrupt();
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public TradeBot.BotType getBotType() {
    return botType;
  }

  @Override
  public BotConfig getConfig() {
    return this.config;
  }

  @Override
  public void run() {
    while (running) {
      log.info("[" + botType + "] Checking market data...");
      // Simulate trade logic here
      try {
        Thread.sleep(config.getInterval() * 1000L);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}