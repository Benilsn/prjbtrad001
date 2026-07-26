package dev.prjbtrad001.web;

import dev.prjbtrad001.domain.bot.TradeBot;

import java.math.BigDecimal;

/**
 * A bot plus its live-price-derived figures, ready for the dashboard.
 * {@code currentPrice}/{@code unrealized} may be null when the price lookup
 * failed or the bot is flat.
 */
public record BotView(TradeBot bot, BigDecimal currentPrice, BigDecimal unrealized) {

  public boolean hasUnrealized() {
    return unrealized != null && bot.getStatus().isOpen();
  }

  public int unrealizedSign() {
    return unrealized == null ? 0 : unrealized.signum();
  }
}
