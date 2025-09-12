package dev.prjbtrad001.app.bot;

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

  public SimpleTradeBot(BotParameters parameters) {
    this.parameters = parameters;
    this.status = new Status();
  }

  public Integer getIntervalInMinutes() {
    return Integer.parseInt(parameters.getInterval().replaceAll("[mhd]", ""));
  }

  public Integer getIntervalInSeconds() {
    return Integer.parseInt(parameters.getInterval().replaceAll("[mhd]", "")) * 60;
  }

}
