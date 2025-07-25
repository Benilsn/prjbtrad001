package dev.prjbtrad001.app.bot;

import dev.prjbtrad001.app.service.TradingService;
import dev.prjbtrad001.domain.core.TradeBot;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.jbosslog.JBossLog;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

import static dev.prjbtrad001.app.utils.LogUtils.LINE_SEPARATOR;
import static dev.prjbtrad001.app.utils.LogUtils.log;

@Entity
@JBossLog
@Getter
@Setter
@NoArgsConstructor
public class SimpleTradeBot extends PanacheEntityBase implements TradeBot, Runnable {

  @Id
  @Setter(AccessLevel.NONE)
  @GeneratedValue(generator = "uuid")
  @UuidGenerator(style = UuidGenerator.Style.AUTO)
  private UUID id;

  @Embedded
  private BotParameters parameters;

  private boolean running = false;

  public SimpleTradeBot(BotParameters parameters) {
    this.parameters = parameters;
  }

  @Override
  public void run() {
    if (!running) return;
    try {
      long processTime = System.currentTimeMillis();

      log("[" + parameters.getBotType() + " - " + id + "] Checking market data...");
      TradingService.analyzeMarket(
        parameters.getBotType(),
        parameters.getInterval(),
        parameters.getWindowResistanceSupport(),
        parameters.getSmaShort(),
        parameters.getSmaLong(),
        parameters.getRsiPurchase(),
        parameters.getRsiSale(),
        parameters.getVolumeMultiplier());

      log(String.format("[%s] - %d", parameters.getBotType(), System.currentTimeMillis() - processTime) + "ms to process bot: " + id);
      log(LINE_SEPARATOR, false);
    } catch (Exception e) {
      log("Error while running bot: " + e.getMessage());
    }
  }

  @Override
  public void start() {
    this.running = true;
  }

  @Override
  public void stop() {
    this.running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public BotParameters getParameters() {
    return parameters;
  }
}
