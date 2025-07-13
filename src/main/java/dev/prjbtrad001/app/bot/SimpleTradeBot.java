package dev.prjbtrad001.app.bot;


import dev.prjbtrad001.domain.config.BotConfig;
import dev.prjbtrad001.domain.core.TradeBot;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class SimpleTradeBot implements TradeBot, Runnable {

  private final String symbol;
  private final BotConfig config;
  private volatile boolean running = false;
  private Thread worker;

  public SimpleTradeBot(String symbol, BotConfig config) {
    this.symbol = symbol;
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
  public String getSymbol() {
    return symbol;
  }

  @Override
  public void run() {
    while (running) {
      log.info("[" + symbol + "] Checking market data...");
      // Simulate trade logic here
      try {
        Thread.sleep(config.pollingInterval * 1000L);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}