package dev.prjbtrad001.domain.core;

import dev.prjbtrad001.app.bot.BotParameters;

public interface TradeBot {

  void start();
  void stop();
  boolean isRunning();
  BotParameters getParameters();

}