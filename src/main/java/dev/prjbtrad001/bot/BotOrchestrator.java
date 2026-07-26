package dev.prjbtrad001.bot;

import dev.prjbtrad001.domain.bot.TradeBot;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.jbosslog.JBossLog;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Schedules each running bot on its own recurring tick.
 *
 * The old code computed the tick interval wrongly (hours and days collapsed to
 * minutes, and "1s"/"1w" threw). Here {@link #pollSeconds} maps the timeframe
 * correctly and clamps to a sane [60s, 1h] polling band — re-checking a closed
 * candle is harmless because entries only fire when flat.
 */
@JBossLog
@ApplicationScoped
public class BotOrchestrator {

  @Inject
  BotRunner runner;

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
  private final Map<UUID, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

  void onStart(@Observes StartupEvent ev) {
    // Re-arm any bot left marked as running (no-op on a fresh drop-and-create DB).
    reschedulePersistedRunners();
  }

  @Transactional
  void reschedulePersistedRunners() {
    List<TradeBot> bots = TradeBot.list("running", true);
    for (TradeBot bot : bots) schedule(bot.getId(), bot.getTimeframe());
    if (!bots.isEmpty()) log.infof("Re-armed %d running bot(s) on startup", bots.size());
  }

  /** Marks the bot running and schedules its tick. */
  @Transactional
  public void start(UUID botId) {
    TradeBot bot = TradeBot.findById(botId);
    if (bot == null) return;
    bot.setRunning(true);
    schedule(botId, bot.getTimeframe());
    log.infof("Started bot %s (%s %s)", botId, bot.getSymbol(), bot.getTimeframe());
  }

  /** Cancels the tick and marks the bot idle. */
  @Transactional
  public void stop(UUID botId) {
    cancel(botId);
    TradeBot bot = TradeBot.findById(botId);
    if (bot != null) bot.setRunning(false);
    log.infof("Stopped bot %s", botId);
  }

  public boolean isScheduled(UUID botId) {
    return scheduled.containsKey(botId);
  }

  private void schedule(UUID botId, String timeframe) {
    if (scheduled.containsKey(botId)) return;
    long poll = pollSeconds(timeframe);
    ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
      try {
        runner.runOnce(botId);
      } catch (Exception e) {
        log.errorf("Bot %s tick failed: %s", botId, e.getMessage());
      }
    }, 3, poll, TimeUnit.SECONDS);
    scheduled.put(botId, future);
  }

  private void cancel(UUID botId) {
    ScheduledFuture<?> future = scheduled.remove(botId);
    if (future != null) future.cancel(false);
  }

  @PreDestroy
  void shutdown() {
    scheduled.values().forEach(f -> f.cancel(false));
    scheduled.clear();
    scheduler.shutdownNow();
  }

  /**
   * Timeframe string ("15m", "4h", "1d", "1w") → tick interval in seconds,
   * clamped to [60, 3600].
   */
  static long pollSeconds(String timeframe) {
    long candleSeconds = timeframeSeconds(timeframe);
    return Math.max(60, Math.min(candleSeconds, 3600));
  }

  static long timeframeSeconds(String timeframe) {
    if (timeframe == null || timeframe.length() < 2) return 3600;
    char unit = timeframe.charAt(timeframe.length() - 1);
    long n;
    try {
      n = Long.parseLong(timeframe.substring(0, timeframe.length() - 1));
    } catch (NumberFormatException e) {
      return 3600;
    }
    return switch (unit) {
      case 'm' -> n * 60;
      case 'h' -> n * 3600;
      case 'd' -> n * 86400;
      case 'w' -> n * 604800;
      default -> 3600;
    };
  }
}
