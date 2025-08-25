package dev.prjbtrad001.app.bot;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.jbosslog.JBossLog;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@JBossLog
@Getter
@Setter
@NoArgsConstructor
public class SimpleTradeBot extends PanacheEntityBase {

  @Id
  @Setter(AccessLevel.NONE)
  @GeneratedValue(generator = "uuid")
  @UuidGenerator(style = UuidGenerator.Style.AUTO)
  private UUID id;

  @Embedded
  private BotParameters parameters;

  @Embedded
  private Status status;

  private boolean running = false;

  private int consecutiveLosses = 0;

  private boolean tradingPaused = false;

  private LocalDateTime pauseUntil = LocalDateTime.now();

  private BigDecimal positionSizeMultiplier = BigDecimal.ONE;

  public SimpleTradeBot(BotParameters parameters) {
    this.parameters = parameters;
    this.status = new Status();
  }

  public void addTradeResult(boolean isProfit) {
    if (!isProfit) {
      consecutiveLosses++;

      if (consecutiveLosses >= 2) {
        tradingPaused = true;
        pauseUntil = LocalDateTime.now().plusMinutes(30);
        positionSizeMultiplier = BigDecimal.valueOf(0.5);
      }
    } else {
      consecutiveLosses = 0;
      tradingPaused = false;

      positionSizeMultiplier = positionSizeMultiplier.add(BigDecimal.valueOf(0.1))
        .min(BigDecimal.ONE);
    }
  }

  public boolean isTradingPaused() {
    if (tradingPaused && pauseUntil.isBefore(LocalDateTime.now())) {
      tradingPaused = false;
      pauseUntil = LocalDateTime.now();
      positionSizeMultiplier = BigDecimal.valueOf(0.6);
    }
    return tradingPaused;
  }

  public BigDecimal getAdjustedPositionSize(BigDecimal baseAmount) {
    return baseAmount.multiply(positionSizeMultiplier);
  }

  public Integer getIntervalInMinutes() {
    return Integer.parseInt(parameters.getInterval().replaceAll("[mhd]", ""));
  }

  public Integer getIntervalInSeconds() {
    return Integer.parseInt(parameters.getInterval().replaceAll("[mhd]", "")) * 60;
  }

}
