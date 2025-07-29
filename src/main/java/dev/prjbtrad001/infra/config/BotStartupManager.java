package dev.prjbtrad001.infra.config;

import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.app.service.BotOrchestratorService;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;

import java.util.List;
import java.util.UUID;

@Startup
@JBossLog
@ApplicationScoped
public class BotStartupManager {

  @Inject
  BotOrchestratorService botOrchestratorService;

  @PostConstruct
  public void restoreRunningBots() {
    List<UUID> botIds = SimpleTradeBot
      .find("running", true)
      .stream()
      .map(entity -> (SimpleTradeBot) entity)
      .map(SimpleTradeBot::getId)
      .toList();

    log.info("Restoring " + botIds.size() + " running bots...");

    botIds
      .forEach(b -> {
        botOrchestratorService.startBot(b);
        try {
          Thread.sleep(1500);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      });
  }
}