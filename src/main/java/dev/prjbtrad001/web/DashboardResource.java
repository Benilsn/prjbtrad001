package dev.prjbtrad001.web;

import dev.prjbtrad001.domain.bot.TradeBot;
import dev.prjbtrad001.market.MarketDataClient;
import dev.prjbtrad001.paper.PaperWallet;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import java.math.BigDecimal;
import java.util.*;

/**
 * The landing page: a dashboard of every bot plus a paper-account summary.
 * Live prices are fetched only for open positions, so a dashboard full of idle
 * bots stays fast.
 */
@Path("/")
public class DashboardResource {

  @Inject
  PaperWallet wallet;
  @Inject
  MarketDataClient marketData;

  @GET
  @Transactional
  public TemplateInstance dashboard(@QueryParam("message") String message) {
    List<TradeBot> bots = TradeBot.<TradeBot>listAll().stream()
      .sorted(Comparator
        .comparing((TradeBot b) -> !b.isRunning())
        .thenComparing(b -> !b.getStatus().isOpen())
        .thenComparing(b -> b.getSymbol().name()))
      .toList();

    // Price cache for the distinct symbols of open bots only.
    Map<String, BigDecimal> priceCache = new HashMap<>();
    for (TradeBot b : bots) {
      if (b.getStatus().isOpen()) {
        priceCache.computeIfAbsent(b.getSymbol().name(), marketData::getPrice);
      }
    }

    List<BotView> views = new ArrayList<>();
    BigDecimal realizedTotal = BigDecimal.ZERO;
    BigDecimal unrealizedTotal = BigDecimal.ZERO;
    int running = 0;

    for (TradeBot b : bots) {
      realizedTotal = realizedTotal.add(nz(b.getStatus().getRealizedProfit()));
      if (b.isRunning()) running++;

      BigDecimal price = b.getStatus().isOpen() ? priceCache.get(b.getSymbol().name()) : null;
      BigDecimal unreal = price == null ? null : b.getStatus().unrealizedProfit(price);
      if (unreal != null) unrealizedTotal = unrealizedTotal.add(unreal);
      views.add(new BotView(b, price, unreal));
    }

    Map<String, Object> summary = new HashMap<>();
    summary.put("total", bots.size());
    summary.put("running", running);
    summary.put("realized", realizedTotal);
    summary.put("unrealized", unrealizedTotal);
    summary.put("balance", wallet.getBalance());
    summary.put("fees", wallet.getTotalFees());

    return Templates.dashboard()
      .data("pageTitle", "Dashboard")
      .data("message", message)
      .data("bots", views)
      .data("summary", summary);
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }
}
