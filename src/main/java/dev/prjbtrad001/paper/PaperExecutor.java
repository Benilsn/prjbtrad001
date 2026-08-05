package dev.prjbtrad001.paper;

import dev.prjbtrad001.domain.bot.BotStatus;
import dev.prjbtrad001.domain.bot.TradeBot;
import dev.prjbtrad001.domain.bot.TradeRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Simulated order execution against the {@link PaperWallet}.
 *
 * Fees are charged on both sides at the wallet's fee rate, so paper P&L reflects
 * the same cost drag that killed the old scalping approach — no free lunch.
 */
@JBossLog
@ApplicationScoped
public class PaperExecutor {

  @Inject
  PaperWallet wallet;

  /**
   * Opens a long position of {@code orderSizeBrl} at {@code price}.
   * Returns false when the bot is already open or the wallet is short on cash.
   */
  public boolean buy(TradeBot bot, BigDecimal price) {
    BotStatus status = bot.getStatus();
    if (status.isOpen()) return false;

    BigDecimal notional = bot.getOrderSizeBrl();
    if (!wallet.debit(notional)) {
      log.warnf("[%s] paper buy skipped — insufficient wallet balance (need R$ %s)", bot.getSymbol(), notional);
      return false;
    }

    // Fill a touch above the quote — a market buy lifts the ask.
    BigDecimal fill = wallet.fillPrice(price, true);

    BigDecimal fee = wallet.feeOn(notional);
    wallet.recordFee(fee);
    BigDecimal quantity = notional.subtract(fee).divide(fill, 8, RoundingMode.HALF_UP);

    status.setOpen(true);
    status.setQuantity(quantity);
    status.setAvgPrice(fill);
    status.setInvestedBrl(notional);
    status.setLastEntryTime(LocalDateTime.now());

    TradeRecord.buy(bot, fill, quantity, notional, fee).persist();

    log.infof("[%s] 🔵 BUY  %s @ R$ %s (cotação R$ %s · fee R$ %s)",
      bot.getSymbol(), quantity.setScale(6, RoundingMode.HALF_UP),
      fill.setScale(2, RoundingMode.HALF_UP),
      price.setScale(2, RoundingMode.HALF_UP), fee.setScale(2, RoundingMode.HALF_UP));
    return true;
  }

  /**
   * Closes the open position at {@code price}, realising net profit into the
   * bot's status and returning cash (minus fee) to the wallet.
   */
  public boolean sell(TradeBot bot, BigDecimal price, TradeRecord.Reason reason) {
    BotStatus status = bot.getStatus();
    if (!status.isOpen() || status.getQuantity().signum() <= 0) return false;

    BigDecimal quantity = status.getQuantity();
    BigDecimal invested = status.getInvestedBrl();

    // Fill a touch below the quote — a market sell hits the bid.
    BigDecimal fill = wallet.fillPrice(price, false);

    BigDecimal proceeds = quantity.multiply(fill);
    BigDecimal fee = wallet.feeOn(proceeds);
    wallet.recordFee(fee);
    BigDecimal net = proceeds.subtract(fee).setScale(2, RoundingMode.HALF_UP);
    wallet.credit(net);

    BigDecimal profit = net.subtract(invested).setScale(2, RoundingMode.HALF_UP);
    BigDecimal profitPct = invested.signum() == 0 ? BigDecimal.ZERO
      : profit.divide(invested, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

    status.setRealizedProfit(status.getRealizedProfit().add(profit));
    status.setClosedTrades(status.getClosedTrades() + 1);

    TradeRecord.sell(bot, fill, quantity, proceeds.setScale(2, RoundingMode.HALF_UP),
      fee, profit, profitPct, reason).persist();

    log.infof("[%s] %s SELL @ R$ %s (cotação R$ %s) → trade P&L R$ %s (%.2f%%) [%s] (total R$ %s)",
      bot.getSymbol(), profit.signum() >= 0 ? "💚" : "🔴",
      fill.setScale(2, RoundingMode.HALF_UP),
      price.setScale(2, RoundingMode.HALF_UP), profit, profitPct, reason,
      status.getRealizedProfit());

    // Reset position
    status.setOpen(false);
    status.setQuantity(BigDecimal.ZERO);
    status.setAvgPrice(BigDecimal.ZERO);
    status.setInvestedBrl(BigDecimal.ZERO);
    status.setLastEntryTime(null);
    return true;
  }
}
