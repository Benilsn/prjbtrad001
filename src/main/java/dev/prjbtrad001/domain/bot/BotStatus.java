package dev.prjbtrad001.domain.bot;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Live position + running P&L for a single bot.
 * All monetary values are in BRL; quantity is the base asset amount held.
 */
@Getter
@Setter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class BotStatus {

  /** true when the bot currently holds the asset (in a long position). */
  private boolean open;

  @Column(precision = 19, scale = 8)
  private BigDecimal quantity = BigDecimal.ZERO;

  /** Average entry price of the current position (BRL per unit). */
  @Column(precision = 19, scale = 8)
  private BigDecimal avgPrice = BigDecimal.ZERO;

  /** BRL amount currently invested in the open position (fees included). */
  @Column(precision = 19, scale = 2)
  private BigDecimal investedBrl = BigDecimal.ZERO;

  /** Cumulative realised profit across all closed trades (net of fees). */
  @Column(precision = 19, scale = 2)
  private BigDecimal realizedProfit = BigDecimal.ZERO;

  /** Number of closed round-trip trades. */
  private int closedTrades;

  private LocalDateTime lastEntryTime;

  /**
   * Unrealised P&L in BRL at the given current price. Zero when flat.
   */
  public BigDecimal unrealizedProfit(BigDecimal currentPrice) {
    if (!open || currentPrice == null || avgPrice == null) return BigDecimal.ZERO;
    return currentPrice.subtract(avgPrice).multiply(quantity);
  }

  /** Sign of realised profit: 1 positive, -1 negative, 0 zero/absent. */
  public int realizedSign() {
    if (realizedProfit == null || realizedProfit.signum() == 0) return 0;
    return realizedProfit.signum();
  }
}
