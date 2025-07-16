package dev.prjbtrad001.domain.core;

public interface TradeBot {
  void start();

  void stop();

  boolean isRunning();

  BotType getBotType();


  enum BotType {
    BTC,
    ETH
  }

}
