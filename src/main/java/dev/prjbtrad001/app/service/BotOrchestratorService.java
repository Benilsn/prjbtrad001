package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.bot.BotTask;
import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.app.utils.LogUtils;
import dev.prjbtrad001.domain.repository.BotRepository;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.jbosslog.JBossLog;

import java.util.*;
import java.util.concurrent.*;

import static dev.prjbtrad001.app.utils.LogUtils.log;

@JBossLog
@ApplicationScoped
public class BotOrchestratorService {

  @Inject
  private BotRepository botRepository;

  @Inject
  BotRunnerService botRunnerService;

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
  private final Map<UUID, ScheduledFuture<?>> runningBots = new ConcurrentHashMap<>();

  public SimpleTradeBot createBot(SimpleTradeBot bot) {
    BotOrchestratorService.log.debug("Creating bot: " + bot.getParameters().getBotType());
    botRepository.createBot(bot);
    return bot;
  }

  public List<SimpleTradeBot> getAllBots() {
    List<SimpleTradeBot> bots = botRepository.getAllBots();
    BotOrchestratorService.log.debug("Getting all " + bots.size() + " bots.");
    return
      bots
        .stream()
        .sorted(Comparator.comparing(tradeBot -> !tradeBot.isRunning()))
        .toList();
  }

  public void deleteBot(UUID botId) {
    BotOrchestratorService.log.debug("Deleting bot: " + botId);
    stopBot(botId);
    botRepository.deleteBot(botId);
  }

  public SimpleTradeBot getBotById(UUID botId) {
    BotOrchestratorService.log.debug("Getting bot by ID: " + botId);
    return
      botRepository
        .getBotById(botId)
        .orElseThrow(() -> new NoSuchElementException("Bot not found with ID: " + botId));
  }


  @Transactional
  public void startBot(UUID botId) {
    SimpleTradeBot bot = getBotById(botId);

    if (runningBots.containsKey(bot.getId())) {
      log("Bot " + bot.getId() + " is already running.");
      return;
    }

    int interval = (Integer.parseInt(bot.getParameters().getInterval().replaceAll("[mhd]", "")) * 60) + 1;

    bot.setRunning(true);
    persist(bot);

    BotTask task = new BotTask(bot, botRunnerService);
    ScheduledFuture<?> future =
      scheduler
        .scheduleAtFixedRate(task, 0, interval, TimeUnit.SECONDS);

    runningBots.put(bot.getId(), future);
    log("Started bot " + bot.getId() + " with interval " + interval + "s");
  }

  @Transactional
  public void stopBot(UUID botId) {
    SimpleTradeBot bot = getBotById(botId);

    ScheduledFuture<?> future = runningBots.remove(bot.getId());
    if (future != null) {
      future.cancel(true);
    }

    bot.setRunning(false);
    log("Stopped bot " + bot.getId());
  }

  @Transactional
  public void persist(SimpleTradeBot bot) {
    bot.persist();
  }

  @PreDestroy
  @Transactional
  public void shutdownAll() {
    runningBots
      .values()
      .forEach(future -> future.cancel(true));

    SimpleTradeBot
      .findAll()
      .stream()
      .map(entity -> (SimpleTradeBot) entity)
      .map(SimpleTradeBot::getId)
      .toList()
      .forEach(this::stopBot);

    LogUtils.shutdown();
    runningBots.clear();
    scheduler.shutdown();
    log("All bots stopped and scheduler shut down.");
  }

}
