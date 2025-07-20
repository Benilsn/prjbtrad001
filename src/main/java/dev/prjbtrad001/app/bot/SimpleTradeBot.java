package dev.prjbtrad001.app.bot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.prjbtrad001.domain.core.TradeBot;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.jbosslog.JBossLog;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

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

  private volatile boolean running = false;

  @Transient
  @JsonIgnore
  private Thread worker;

  public SimpleTradeBot(BotParameters parameters) {
    this.parameters = parameters;
  }

  @Override
  public void start() {
    if (running) return;
    running = true;
    worker = new Thread(this);
    worker.start();
  }

  @Override
  public void stop() {
    running = false;
    if (worker != null) worker.interrupt();
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public BotParameters getParameters() {
    return this.parameters;
  }

  @Override
  public void run() {
//    while (running) {
//      log.info("[" + parameters.getBotType() + "] Checking market data...");
//      // Simulate trade logic here
//      try {
//        Thread.sleep(parameters.getInterval() * 1000L);
//      } catch (InterruptedException e) {
//        Thread.currentThread().interrupt();
//      }
//    }
  }
}