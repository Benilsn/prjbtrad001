package dev.prjbtrad001.domain.core;

import dev.prjbtrad001.infra.config.BotConfig;

public interface TradeBot {

  void start();

  void stop();

  boolean isRunning();

  BotType getBotType();

  BotConfig getConfig();

  enum BotType {
    BTCUSDT,
    ETHUSDT
  }

}
