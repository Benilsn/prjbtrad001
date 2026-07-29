package dev.prjbtrad001.paper;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A single simulated BRL cash account shared by every paper bot.
 *
 * Each bot keeps its own position in its {@code BotStatus}; the wallet only
 * holds the shared cash and the running fee tally. Real money is never touched.
 */
@ApplicationScoped
public class PaperWallet {

  @ConfigProperty(name = "bot.paper.initial-balance")
  BigDecimal initialBalance;

  @ConfigProperty(name = "bot.paper.fee-rate")
  BigDecimal feeRate;

  private BigDecimal balance = BigDecimal.ZERO;
  private BigDecimal totalFees = BigDecimal.ZERO;

  @PostConstruct
  void init() {
    balance = initialBalance;
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
}
