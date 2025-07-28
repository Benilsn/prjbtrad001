package dev.prjbtrad001.app.bot;

import dev.prjbtrad001.app.service.BotExecutorService;

import java.util.UUID;

public class BotTask implements Runnable {

  private final UUID botId;
  private final BotExecutorService botExecutorService;

  public BotTask(UUID botId, BotExecutorService botExecutorService) {
    this.botId = botId;
    this.botExecutorService = botExecutorService;
  }

  @Override
  public void run() {
    botExecutorService.executeById(botId);
  }

}