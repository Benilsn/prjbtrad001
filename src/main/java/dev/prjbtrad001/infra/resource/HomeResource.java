package dev.prjbtrad001.infra.resource;

import dev.prjbtrad001.app.service.BotOrchestratorService;
import dev.prjbtrad001.app.dto.Cripto;
import dev.prjbtrad001.app.service.BinanceService;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/")
public class HomeResource {

  @Inject
  BinanceService binanceService;

  @Inject
  BotOrchestratorService botOrchestratorService;

  @GET
  public TemplateInstance homePage() {
    return Templates.home()
      .data("pageTitle", "btrad001")
      .data("btcData", binanceService.getPrice("BTCUSDT"))
      .data("ethData", binanceService.getPrice("ETHUSDT"))
      .data("data", botOrchestratorService.getLogData());
  }

  @GET()
  @Path("/refresh-data")
  @Produces(MediaType.APPLICATION_JSON)
  public Cripto refresh(@QueryParam("symbol") String symbol) {
    return binanceService.getPrice(symbol);
  }


}
