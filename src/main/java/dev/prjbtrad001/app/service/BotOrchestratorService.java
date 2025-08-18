package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.bot.BotParameters;
import dev.prjbtrad001.app.bot.BotTask;
import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.app.utils.CriptoUtils;
import dev.prjbtrad001.domain.repository.BotRepository;
import dev.prjbtrad001.infra.exception.BotOperationException;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.jbosslog.JBossLog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static dev.prjbtrad001.app.utils.LogUtils.log;

/**
 * Service responsible for managing trading bots lifecycle.
 * Handles bot creation, execution, monitoring and shutdown.
 * Uses a thread pool to schedule and execute bot tasks.
 */
@JBossLog
@ApplicationScoped
public class BotOrchestratorService {

  @Inject
  private BotRepository botRepository;
  @Inject
  BotExecutorService botExecutorService;

  /**
   * Manages the scheduling of bot tasks.
   * Each bot runs on its own schedule based on the configured interval.
   */
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

  /**
   * Tracks currently running bots and their scheduled tasks.
   * Key: Bot ID, Value: Scheduled task handle
   */
  private final Map<UUID, ScheduledFuture<?>> runningBots = new ConcurrentHashMap<>();

  @Transactional
  public SimpleTradeBot createBot(SimpleTradeBot bot) {
    Objects.requireNonNull(bot, "Bot cannot be null");
    Objects.requireNonNull(bot.getParameters(), "Bot parameters cannot be null");
    log.debug("Creating bot: " + bot.getParameters().getBotType());
    botRepository.createBot(bot);
    return bot;
  }

  @Transactional
  public void createAllFromFile(Path filePath) throws IOException {
    List<SimpleTradeBot> bots = CriptoUtils.readFromCsv(filePath);
    log.debugf("Creating %s bots from file: %s", bots.size(), filePath.getFileName());

    botRepository.persist(bots);
  }

  @Transactional
  public List<SimpleTradeBot> getAllBots() {
    List<SimpleTradeBot> bots = botRepository.getAllBots();
    BotOrchestratorService.log.debug("Getting all " + bots.size() + " bots.");
    return
      bots.stream()
        .sorted(Comparator.comparing(tradeBot -> !tradeBot.isRunning()))
        .toList();
  }

  @Transactional
  public void deleteBot(UUID botId) {
    BotOrchestratorService.log.debug("Deleting bot: " + botId);
    stopBot(botId);
    botRepository.deleteBot(botId);
  }

  @Transactional
  public SimpleTradeBot updateBot(BotParameters parameters, UUID botId) {
    SimpleTradeBot bot = getBotById(botId);
    bot.setParameters(parameters);
    bot.persist();
    BotOrchestratorService.log.debug("Updating bot: " + bot.getParameters().getBotType());
    return bot;
  }

  @Transactional
  public SimpleTradeBot getBotById(UUID botId) {
    BotOrchestratorService.log.debug("Getting bot by ID: " + botId);
    return
      botRepository
        .getBotById(botId)
        .orElseThrow(() -> new NoSuchElementException("Bot not found with ID: " + botId));
  }

  @Transactional
  public void startBot(UUID botId) {
    try {
      SimpleTradeBot bot = getBotById(botId);
      if (isAlreadyRunning(bot.getId())) {
        log.warnf("Bot %s is already running", bot.getId());
        return;
      }

      int interval = bot.getIntervalInSeconds();
      scheduleNewBotTask(bot, interval);

      updateBotStatus(bot, true);
      log.infof("Bot %s started with interval of %ds", bot.getId(), interval);
    } catch (Exception e) {
      log.errorf("Failed to start bot %s: %s", botId, e.getMessage());
      throw new BotOperationException("Failed to start bot", e);
    }
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
  public void deleteAll() {
    botRepository.deleteAll();
    runningBots.clear();
    log.debugf("All bots deleted successfully");
  }

  @Transactional
  public void runAll() {
    try {
      List<UUID> botIds = SimpleTradeBot
        .find("running", false)
        .stream()
        .map(entity -> (SimpleTradeBot) entity)
        .map(SimpleTradeBot::getId)
        .toList();

      botIds.forEach(b -> {
        try {
          startBot(b);
          Thread.sleep(1000);
        } catch (Exception e) {
          log.errorf("Failed to start bot %s: %s", b, e.getMessage());
        }
      });

    } catch (Exception e) {
      log.errorf("Failed to run all bots: %s", e.getMessage());
      throw new BotOperationException("Failed to run all bots", e);
    }
  }

  @PreDestroy
  @Transactional
  public void shutdownAll() {
    log.info("Initiating graceful shutdown of all bots...");
    try {
      stopAllBots();
      cleanupResources();
      log.info("All bots stopped successfully");
    } catch (Exception e) {
      log.error("Error during shutdown: " + e.getMessage());
    }
  }

  public void stopAllBots() {
    runningBots.keySet().forEach(this::stopBot);
    runningBots.clear();
  }

  private void cleanupResources() {
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      scheduler.shutdownNow();
    }
  }

  private void updateBotStatus(SimpleTradeBot bot, boolean running) {
    bot.setRunning(running);
    botRepository.persist(bot);
  }

  private boolean isAlreadyRunning(UUID botId) {
    return runningBots.containsKey(botId);
  }

  private void scheduleNewBotTask(SimpleTradeBot bot, int interval) {
    BotTask task = new BotTask(bot.getId(), botExecutorService);
    ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
      task,
      2,
      interval,
      TimeUnit.SECONDS
    );
    runningBots.put(bot.getId(), future);
    log.debugf("Scheduled new task for bot %s with interval %ds", bot.getId(), interval);
  }

}
