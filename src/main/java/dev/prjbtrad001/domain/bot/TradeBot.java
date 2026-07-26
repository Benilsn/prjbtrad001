package dev.prjbtrad001.domain.bot;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.FormParam;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single trend-following bot: a dual-EMA crossover strategy on one trading pair.
 *
 * Entry  : fast EMA crosses above slow EMA.
 * Exit   : fast EMA crosses below slow EMA, or the stop-loss is hit.
 *
 * Only a handful of knobs — deliberately. The old bot drowned in parameters that
 * never earned their keep; here every field maps to a real decision.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class TradeBot extends PanacheEntityBase {

  @Id
  @Setter(AccessLevel.NONE)
  @GeneratedValue(generator = "uuid")
  @UuidGenerator(style = UuidGenerator.Style.AUTO)
  private UUID id;

  @NotNull(message = "Trading pair is required")
  @Enumerated(EnumType.STRING)
  @FormParam("symbol")
  private BotType symbol;

  /** Binance candle interval (e.g. 1h, 4h, 1d). */
  @NotNull(message = "Timeframe is required")
  @Pattern(regexp = "^(15m|30m|1h|2h|4h|6h|8h|12h|1d|3d|1w)$",
    message = "Timeframe must be one of: 15m, 30m, 1h, 2h, 4h, 6h, 8h, 12h, 1d, 3d, 1w")
  @Column(name = "timeframe")
  @FormParam("timeframe")
  private String timeframe;

  @Min(value = 2, message = "Fast EMA period must be at least 2")
  @FormParam("emaFast")
  private int emaFast;

  @Min(value = 3, message = "Slow EMA period must be at least 3")
  @FormParam("emaSlow")
  private int emaSlow;

  /** Safety-net stop, as a percentage below the average entry price. */
  @NotNull(message = "Stop-loss percent is required")
  @DecimalMin(value = "0", inclusive = false, message = "Stop-loss must be greater than zero")
  @FormParam("stopLossPercent")
  private BigDecimal stopLossPercent;

  /** Fixed BRL amount committed per entry. */
  @NotNull(message = "Order size is required")
  @DecimalMin(value = "0", inclusive = false, message = "Order size must be greater than zero")
  @FormParam("orderSizeBrl")
  private BigDecimal orderSizeBrl;

  private boolean running = false;

  @Embedded
  private BotStatus status = new BotStatus();

  public TradeBot(BotType symbol, String timeframe, int emaFast, int emaSlow,
                  BigDecimal stopLossPercent, BigDecimal orderSizeBrl) {
    this.symbol = symbol;
    this.timeframe = timeframe;
    this.emaFast = emaFast;
    this.emaSlow = emaSlow;
    this.stopLossPercent = stopLossPercent;
    this.orderSizeBrl = orderSizeBrl;
    this.status = new BotStatus();
  }

  /** Compact label such as "9×21" for the UI. */
  public String emaLabel() {
    return emaFast + "×" + emaSlow;
  }

  /** Cross-field sanity used by the resource before persisting. */
  public boolean hasValidEmaOrder() {
    return emaFast < emaSlow;
  }
}
