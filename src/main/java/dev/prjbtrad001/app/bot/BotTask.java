package dev.prjbtrad001.app.bot;

import dev.prjbtrad001.app.service.BotRunnerService;

public class BotTask implements Runnable {

  private final SimpleTradeBot bot;
  private final BotRunnerService runnerService;

  public BotTask(SimpleTradeBot bot, BotRunnerService runnerService) {
    this.bot = bot;
    this.runnerService = runnerService;
  }

  @Override
  public void run() {
    runnerService.executeBot(bot);
  }

}