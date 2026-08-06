package dev.prjbtrad001.paper;

import dev.prjbtrad001.domain.bot.TradeRecord;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * A single simulated BRL cash account shared by every paper bot.
 *
 * Each bot keeps its own position in its {@code BotStatus}; the wallet only
 * holds the shared cash and the running fee tally. Real money is never touched.
 *
 * The cash itself is NOT persisted — it is rebuilt from {@code trade_record} on
 * every start. That table is already a complete ledger (every fill, both sides,
 * with its fee), so replaying it is both simpler than a second source of truth
 * and immune to the two drifting apart.
 *
 * Before this, a restart silently reset the balance to the initial amount while
 * open positions stayed in the database — leaving the account holding more cash
 * than it ever had, and losing the fee tally entirely.
 */
@JBossLog
@ApplicationScoped
public class PaperWallet {

  @ConfigProperty(name = "bot.paper.initial-balance")
  BigDecimal initialBalance;

  @ConfigProperty(name = "bot.paper.fee-rate")
  BigDecimal feeRate;

  @ConfigProperty(name = "bot.paper.slippage-rate")
  BigDecimal slippageRate;

  private BigDecimal balance = BigDecimal.ZERO;
  private BigDecimal totalFees = BigDecimal.ZERO;

  @PostConstruct
  void init() {
    balance = initialBalance;
  }

  /**
   * Replays every recorded fill to restore the cash position and fee tally.
   *
   *   saldo = inicial − Σ(compras) + Σ(vendas líquidas de taxa)
   *
   * Runs after startup so the database is ready. Bots tick only after their own
   * schedule kicks in, so there is no window where a trade could be priced
   * against a half-rebuilt balance.
   */
  @Transactional
  void rebuildFromHistory(@Observes StartupEvent ev) {
    List<TradeRecord> history = TradeRecord.listAll();
    BigDecimal[] state = replay(initialBalance, history);

    synchronized (this) {
      balance = state[0];
      totalFees = state[1];
    }
    log.infof("Carteira reconstruída de %d execuções: saldo R$ %s, taxas R$ %s",
      history.size(), balance, totalFees);
  }

  /** Pure ledger replay: returns {cash, fees}. Kept separate so it is testable. */
  static BigDecimal[] replay(BigDecimal initial, List<TradeRecord> history) {
    BigDecimal cash = initial;
    BigDecimal fees = BigDecimal.ZERO;

    for (TradeRecord t : history) {
      BigDecimal fee = t.getFeeBrl() == null ? BigDecimal.ZERO : t.getFeeBrl();
      BigDecimal notional = t.getNotionalBrl() == null ? BigDecimal.ZERO : t.getNotionalBrl();
      fees = fees.add(fee);

      // A buy costs its full notional (the fee is already inside it); a sell
      // returns the proceeds minus the fee charged on the way out.
      cash = t.getSide() == TradeRecord.Side.BUY
        ? cash.subtract(notional)
        : cash.add(notional.subtract(fee));
    }
    return new BigDecimal[]{
      cash.setScale(2, RoundingMode.HALF_UP),
      fees.setScale(2, RoundingMode.HALF_UP)
    };
  }

  public synchronized BigDecimal getBalance() {
    return balance;
  }

  public synchronized BigDecimal getTotalFees() {
    return totalFees;
  }

  public BigDecimal getFeeRate() {
    return feeRate;
  }

  public BigDecimal getInitialBalance() {
    return initialBalance;
  }

  /** Deducts {@code amount} from cash if available; returns false when short. */
  public synchronized boolean debit(BigDecimal amount) {
    if (amount.compareTo(balance) > 0) return false;
    balance = balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
    return true;
  }

  public synchronized void credit(BigDecimal amount) {
    balance = balance.add(amount).setScale(2, RoundingMode.HALF_UP);
  }

  public synchronized void recordFee(BigDecimal fee) {
    totalFees = totalFees.add(fee).setScale(2, RoundingMode.HALF_UP);
  }

  /** Fee charged on a given BRL notional. */
  public BigDecimal feeOn(BigDecimal notional) {
    return notional.multiply(feeRate).setScale(8, RoundingMode.HALF_UP);
  }

  public BigDecimal getSlippageRate() {
    return slippageRate;
  }

  /**
   * The price a market order would realistically fill at — always adverse:
   * a little more when buying, a little less when selling. Without this the
   * paper account gets a fill nobody could actually obtain.
   */
  public BigDecimal fillPrice(BigDecimal price, boolean buying) {
    BigDecimal factor = buying
      ? BigDecimal.ONE.add(slippageRate)
      : BigDecimal.ONE.subtract(slippageRate);
    return price.multiply(factor).setScale(8, RoundingMode.HALF_UP);
  }
}
