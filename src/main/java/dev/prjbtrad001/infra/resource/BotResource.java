package dev.prjbtrad001.infra.resource;

import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.app.service.BotOrchestratorService;
import dev.prjbtrad001.domain.config.BotConfig;
import dev.prjbtrad001.domain.core.TradeBot;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/bots")
public class BotResource {

  @Inject
  BotOrchestratorService botOrchestratorService;

  @GET()
  @Path("/running")
  public TemplateInstance activebots() {
    return Templates.activeBots()
      .data("pageTitle", "Active Bots")
      .data("data", botOrchestratorService.getLogData())
      .data("message", null);
//      .data("activeBots", botOrchestratorService.getActiveBots());
  }

  @GET()
  @Path("/create")
  public TemplateInstance createBot() {
    return Templates.createBot()
      .data("pageTitle", "Create Bot");
  }

  @POST
  @Path("/save")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public TemplateInstance saveBot(
    @FormParam("symbol") String symbol,
    @FormParam("interval") long interval,
    @FormParam("stopLossPercent") double stopLossPercent,
    @FormParam("takeProfitPercent") double takeProfitPercent,
    @FormParam("rsiPurchase") double rsiPurchase,
    @FormParam("rsiSale") double rsiSale,
    @FormParam("volumeMultiplier") double volumeMultiplier,
    @FormParam("smaShort") int smaShort,
    @FormParam("smaLong") int smaLong,
    @FormParam("windowResistanceSupport") int windowResistanceSupport) {

    TradeBot bot =
      new SimpleTradeBot(
        TradeBot.BotType.valueOf(symbol),
        BotConfig.builder()
          .interval(interval)
          .stopLossPercent(stopLossPercent)
          .takeProfitPercent(takeProfitPercent)
          .rsiPurchase(rsiPurchase)
          .rsiSale(rsiSale)
          .volumeMultiplier(volumeMultiplier)
          .smaShort(smaShort)
          .smaLong(smaLong)
          .windowResistanceSupport(windowResistanceSupport)
          .build());

    botOrchestratorService.createBot(bot);

    return activebots().data("message", "Bot created successfully!");
  }

}
