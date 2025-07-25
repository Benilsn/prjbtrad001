package dev.prjbtrad001.app.service;

import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.domain.repository.BotRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.*;
import java.util.concurrent.*;

import static dev.prjbtrad001.app.utils.LogUtils.LOG_DATA;
import static dev.prjbtrad001.app.utils.LogUtils.log;

@JBossLog
@ApplicationScoped
public class BotOrchestratorService {


  private BotRepository<SimpleTradeBot> botRepository;

  @Inject
  Instance<BotRepository<SimpleTradeBot>> repositories;

  @ConfigProperty(name = "bot.data.strategy")
  String dataStrategy;

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

    bot.start();
    bot.persist();

    ScheduledFuture<?> future =
      scheduler
        .scheduleAtFixedRate(bot, 0, interval, TimeUnit.SECONDS);

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

    bot.stop();
    log("Stopped bot " + bot.getId());
  }

  @PreDestroy
  @Transactional
  public void shutdownAll() {
    runningBots
      .values()
      .forEach(future -> future.cancel(true));

    LOG_DATA.clear();
    runningBots.clear();
    scheduler.shutdown();
    log("All bots stopped and scheduler shut down.");
  }

  public List<String> getLogData() {
    return LOG_DATA;
  }

  @PostConstruct
  public void init() {
    this.botRepository =
      repositories
        .select(NamedLiteral.of(dataStrategy))
        .get();
  }


}
