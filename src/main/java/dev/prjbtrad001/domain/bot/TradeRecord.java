package dev.prjbtrad001.domain.bot;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable log of every simulated execution.
 *
 * {@link BotStatus} only keeps aggregates (realised profit, trade count), which
 * is enough to render a card but useless for post-hoc analysis. This table is
 * the raw material: one row per fill, with the strategy context captured at the
 * time, so a later question like "how did 12x26 behave on 4h vs 1d?" can
 * actually be answered from data instead of guessed.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "trade_record", indexes = {
  @Index(name = "idx_trade_bot", columnList = "botId"),
  @Index(name = "idx_trade_time", columnList = "executedAt")
})
public class TradeRecord extends PanacheEntityBase {

  public enum Side {BUY, SELL}

  /** Why the order fired — lets you separate stop-outs from clean signal exits. */
  public enum Reason {EMA_CROSS, STOP_LOSS}

  @Id
  @Setter(AccessLevel.NONE)
  @GeneratedValue(generator = "uuid")
  @UuidGenerator(style = UuidGenerator.Style.AUTO)
  private UUID id;

  private UUID botId;

  @Enumerated(EnumType.STRING)
  private BotType symbol;

  @Enumerated(EnumType.STRING)
  private Side side;

  @Enumerated(EnumType.STRING)
  private Reason reason;

  @Column(precision = 19, scale = 8)
  private BigDecimal price;

  @Column(precision = 19, scale = 8)
  private BigDecimal quantity;

  /** BRL value of the fill before fees. */
  @Column(precision = 19, scale = 2)
  private BigDecimal notionalBrl;

  @Column(precision = 19, scale = 8)
  private BigDecimal feeBrl;

  /** Net result of the round trip — only set on SELL. */
  @Column(precision = 19, scale = 2)
  private BigDecimal profitBrl;

  @Column(precision = 19, scale = 4)
  private BigDecimal profitPct;

  // ── Strategy context, denormalised on purpose ──────────────────
  // Copied per row so history stays meaningful even if the bot is later
  // edited or deleted.
  private String timeframe;
  private int emaFast;
  private int emaSlow;

  private Instant executedAt;

  public static TradeRecord buy(TradeBot bot, BigDecimal price, BigDecimal qty,
                                BigDecimal notional, BigDecimal fee) {
    TradeRecord r = base(bot);
    r.side = Side.BUY;
    r.reason = Reason.EMA_CROSS;
    r.price = price;
    r.quantity = qty;
    r.notionalBrl = notional;
    r.feeBrl = fee;
    return r;
  }

  public static TradeRecord sell(TradeBot bot, BigDecimal price, BigDecimal qty,
                                 BigDecimal notional, BigDecimal fee,
                                 BigDecimal profitBrl, BigDecimal profitPct, Reason reason) {
    TradeRecord r = base(bot);
    r.side = Side.SELL;
    r.reason = reason;
    r.price = price;
    r.quantity = qty;
    r.notionalBrl = notional;
    r.feeBrl = fee;
    r.profitBrl = profitBrl;
    r.profitPct = profitPct;
    return r;
  }

  private static TradeRecord base(TradeBot bot) {
    TradeRecord r = new TradeRecord();
    r.botId = bot.getId();
    r.symbol = bot.getSymbol();
    r.timeframe = bot.getTimeframe();
    r.emaFast = bot.getEmaFast();
    r.emaSlow = bot.getEmaSlow();
    r.executedAt = Instant.now();
    return r;
  }
}
