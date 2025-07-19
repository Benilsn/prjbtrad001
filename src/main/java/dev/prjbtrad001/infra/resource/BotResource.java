package dev.prjbtrad001.infra.resource;

import dev.prjbtrad001.app.bot.SimpleTradeBot;
import dev.prjbtrad001.app.service.BotOrchestratorService;
import dev.prjbtrad001.domain.core.TradeBot;
import dev.prjbtrad001.infra.config.BotConfig;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

@Path("/bots")
public class BotResource {

  @Inject
  BotOrchestratorService botOrchestratorService;

  @GET()
  @Path("/running")
  public TemplateInstance activebots(@QueryParam("message") String message) {
    return Templates.activeBots()
      .data("pageTitle", "Active Bots")
      .data("data", botOrchestratorService.getLogData())
      .data("message", message)
      .data("activeBots", botOrchestratorService.getAllBots());
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
  public Response saveBot(
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

    return
      Response
        .seeOther(UriBuilder.fromPath("/bots/running")
          .queryParam("message", "Bot created successfully!")
          .build())
        .build();
  }

}
