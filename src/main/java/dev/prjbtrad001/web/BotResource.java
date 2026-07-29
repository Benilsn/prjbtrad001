package dev.prjbtrad001.web;

import dev.prjbtrad001.bot.BotOrchestrator;
import dev.prjbtrad001.domain.bot.BotType;
import dev.prjbtrad001.domain.bot.TradeBot;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.util.*;

/**
 * Bot lifecycle: create, edit, delete, start, stop. All mutations redirect back
 * to the dashboard with a status message.
 */
@Path("/bots")
public class BotResource {

  /** Candle intervals offered in the form; must match TradeBot's @Pattern. */
  static final List<String> TIMEFRAMES =
    List.of("15m", "30m", "1h", "2h", "4h", "6h", "8h", "12h", "1d", "3d", "1w");

  @Inject
  Validator validator;
  @Inject
  BotOrchestrator orchestrator;

  @ConfigProperty(name = "bot.symbol.list")
  List<String> symbols;

  @ConfigProperty(name = "bot.strategy.defaults.timeframe")
  String defTimeframe;
  @ConfigProperty(name = "bot.strategy.defaults.ema-fast")
  int defEmaFast;
  @ConfigProperty(name = "bot.strategy.defaults.ema-slow")
  int defEmaSlow;
  @ConfigProperty(name = "bot.strategy.defaults.stop-loss-percent")
  BigDecimal defStop;
  @ConfigProperty(name = "bot.strategy.defaults.order-size-brl")
  BigDecimal defOrderSize;

  @GET
  @Path("/create")
  public TemplateInstance create() {
    return form(null, null, null);
  }

  @GET
  @Path("/edit/{id}")
  @Transactional
  public TemplateInstance edit(@PathParam("id") UUID id) {
    TradeBot bot = TradeBot.findById(id);
    return form(bot, id, null);
  }

  @POST
  @Path("/save")
  @Transactional
  public Object save(
    @BeanParam TradeBot input,
    @FormParam("botId") UUID botId) {

    List<String> errors = new ArrayList<>();
    for (ConstraintViolation<TradeBot> v : validator.validate(input)) {
      errors.add(v.getMessage());
    }
    if (!input.hasValidEmaOrder()) {
      errors.add("Fast EMA must be smaller than slow EMA.");
    }
    if (!errors.isEmpty()) {
      return form(input, botId, errors);
    }

    if (botId != null) {
      TradeBot bot = TradeBot.findById(botId);
      if (bot != null) {
        bot.setSymbol(input.getSymbol());
        bot.setTimeframe(input.getTimeframe());
        bot.setEmaFast(input.getEmaFast());
        bot.setEmaSlow(input.getEmaSlow());
        bot.setStopLossPercent(input.getStopLossPercent());
        bot.setOrderSizeBrl(input.getOrderSizeBrl());
      }
      return redirect("Bot updated.");
    }

    input.persist();
    return redirect("Bot created.");
  }

  @POST
  @Path("/delete")
  @Transactional
  public Response delete(@FormParam("botId") UUID botId) {
    orchestrator.stop(botId);
    TradeBot.deleteById(botId);
    return redirect("Bot deleted.");
  }

  @GET
  @Path("/start/{id}")
  public Response start(@PathParam("id") UUID id) {
    orchestrator.start(id);
    return redirect("Bot started.");
  }

  @GET
  @Path("/stop/{id}")
  public Response stop(@PathParam("id") UUID id) {
    orchestrator.stop(id);
    return redirect("Bot stopped.");
  }

  // ── helpers ──────────────────────────────────────────────────────
  /**
   * Resolves the effective field values (existing bot, submitted input, or
   * configured defaults) so the template only has to render them.
   */
  private TemplateInstance form(TradeBot bot, UUID botId, List<String> errors) {
    Map<String, Object> v = new HashMap<>();
    v.put("symbol", bot != null && bot.getSymbol() != null ? bot.getSymbol().name() : null);
    v.put("timeframe", bot != null && bot.getTimeframe() != null ? bot.getTimeframe() : defTimeframe);
    v.put("emaFast", bot != null && bot.getEmaFast() > 0 ? bot.getEmaFast() : defEmaFast);
    v.put("emaSlow", bot != null && bot.getEmaSlow() > 0 ? bot.getEmaSlow() : defEmaSlow);
    v.put("stop", bot != null && bot.getStopLossPercent() != null
      ? bot.getStopLossPercent().stripTrailingZeros().toPlainString() : defStop.toPlainString());
    v.put("orderSize", bot != null && bot.getOrderSizeBrl() != null
      ? bot.getOrderSizeBrl().stripTrailingZeros().toPlainString() : defOrderSize.toPlainString());

    return Templates.botForm()
      .data("pageTitle", botId != null ? "Edit Bot" : "Create Bot")
      .data("symbols", symbols)
      .data("types", BotType.values())
      .data("timeframes", TIMEFRAMES)
      .data("botId", botId)
      .data("v", v)
      .data("errors", errors);
  }

  private Response redirect(String message) {
    return Response.seeOther(
      UriBuilder.fromPath("/").queryParam("message", message).build()).build();
  }
}
